package com.better.nothing.music.vizualizer.logic

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class UdpNetworkSync(private val context: Context) {
    companion object {
        private const val TAG = "BNMV:UdpSync"
        private const val DISCOVERY_PORT = 8888
        private const val DISCOVERY_RESPONSE_PORT = 8891
        private const val STREAMING_PORT = 8889
        private const val LATENCY_PORT = 8890
        private const val MULTICAST_GROUP_IP = "224.0.0.167"
        private const val DISCOVERY_MSG = "BNMV_DISCOVER"
        private const val HOST_MSG_PREFIX = "BNMV_HOST"
        private const val PING_PREFIX = "BNMV_PING"
        private const val PONG_PREFIX = "BNMV_PONG"
        private const val PROTOCOL_VERSION = "6.0.made.by.aleks.levet"
        private const val CLIENT_TIMEOUT_MS = 6000L
        private const val HOST_STREAMING_TIMEOUT_MS = 7000L
    }

    data class HostInfo(
        val name: String,
        val model: String,
        val ip: String,
        val port: Int,
        val version: String
    )

    private val broadcastExecutor = Executors.newFixedThreadPool(4)
    private val streamingExecutor = Executors.newSingleThreadExecutor()
    private val latencyExecutor = Executors.newFixedThreadPool(4)
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val generalExecutor = Executors.newCachedThreadPool()

    private var discoverySocket: MulticastSocket? = null
    private var streamingSocket: DatagramSocket? = null
    private var listeningSocket: DatagramSocket? = null
    private var pingResponderSocket: DatagramSocket? = null

    private val _clientIps = MutableStateFlow<Map<InetAddress, Int?>>(emptyMap())
    val clientIps = _clientIps.asStateFlow()

    private val clientLastSeenMap = ConcurrentHashMap<InetAddress, Long>()

    @Volatile private var isBroadcasting = false
    @Volatile private var isDiscovering = false
    @Volatile private var isListening = false
    @Volatile private var isMeasuringLatency = false
    @Volatile private var isRespondingToPings = false
    @Volatile private var isSendingFft = false
    private var fftCount = 0

    private var cachedIp: String = "0.0.0.0"
    private var lastIpCheck: Long = 0

    private var multicastLock: WifiManager.MulticastLock? = null
    private val lockSync = Any()

    private val multicastGroupAddress: InetAddress by lazy {
        InetAddress.getByName(MULTICAST_GROUP_IP)
    }

    private fun acquireMulticastLock() {
        synchronized(lockSync) {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (multicastLock == null) {
                    multicastLock = wifiManager.createMulticastLock(TAG)
                    multicastLock?.setReferenceCounted(false)
                }
                if (multicastLock?.isHeld == false) {
                    multicastLock?.acquire()
                    Log.d(TAG, "MulticastLock acquired")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire MulticastLock", e)
            }
        }
    }

    private fun releaseMulticastLock() {
        synchronized(lockSync) {
            try {
                multicastLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d(TAG, "MulticastLock released")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release MulticastLock", e)
            }
        }
    }

    private fun isLocalAddress(addr: InetAddress?): Boolean {
        if (addr == null) return false
        if (addr.isLoopbackAddress || addr.isAnyLocalAddress) return true
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (netIf in interfaces) {
                for (interfaceAddress in netIf.interfaceAddresses) {
                    if (interfaceAddress.address == addr) return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking local address", e)
        }
        return false
    }

    private fun isLocalIp(ipStr: String?): Boolean {
        if (ipStr.isNullOrEmpty() || ipStr == "0.0.0.0" || ipStr == "127.0.0.1") return true
        return try {
            val addr = InetAddress.getByName(ipStr)
            isLocalAddress(addr)
        } catch (e: Exception) {
            false
        }
    }

    private fun hasLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 37) {
            val granted = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.e(TAG, "ACCESS_LOCAL_NETWORK permission is REQUIRED on Android 17+ (API 37) for local network UDP operations!")
            }
            return granted
        }
        return true
    }

    private fun joinMulticastGroup(socket: MulticastSocket): List<NetworkInterface> {
        val joinedIfs = mutableListOf<NetworkInterface>()
        try {
            val group = multicastGroupAddress
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (netIf in interfaces) {
                if (!netIf.isUp || netIf.isLoopback || !netIf.supportsMulticast()) continue
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        socket.joinGroup(InetSocketAddress(group, DISCOVERY_PORT), netIf)
                        joinedIfs.add(netIf)
                    } else {
                        @Suppress("DEPRECATION")
                        socket.joinGroup(group)
                        joinedIfs.add(netIf)
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to join multicast on interface ${netIf.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error joining multicast group", e)
        }
        return joinedIfs
    }

    private fun leaveMulticastGroup(socket: MulticastSocket, joinedIfs: List<NetworkInterface>) {
        if (socket.isClosed) return
        val group = multicastGroupAddress
        for (netIf in joinedIfs) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    socket.leaveGroup(InetSocketAddress(group, DISCOVERY_PORT), netIf)
                } else {
                    @Suppress("DEPRECATION")
                    socket.leaveGroup(group)
                    break
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    // --- Discovery (Host Mode) ---

    fun startBroadcasting(deviceName: String) {
        if (isBroadcasting) {
            Log.i(TAG, "Host: startBroadcasting skipped (already broadcasting)")
            return
        }
        if (!hasLocalNetworkPermission()) {
            return
        }
        isBroadcasting = true
        acquireMulticastLock()
        startLatencyMeasurement()

        cachedIp = getIpAddress()
        lastIpCheck = System.currentTimeMillis()
        Log.i(TAG, "Host: Initialized with deviceName=$deviceName, IP=$cachedIp")

        broadcastExecutor.execute {
            var socket: MulticastSocket? = null
            var joinedInterfaces = emptyList<NetworkInterface>()
            try {
                socket = MulticastSocket(null).apply {
                    reuseAddress = true
                    timeToLive = 1
                }
                socket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), DISCOVERY_PORT))
                joinedInterfaces = joinMulticastGroup(socket)

                discoverySocket = socket
                val buffer = ByteArray(1024)
                Log.i(TAG, "Host: Started listening on 0.0.0.0:$DISCOVERY_PORT (Multicast + Broadcast)")

                var lastHeartbeat: Long = 0
                while (isBroadcasting && !socket.isClosed) {
                    try {
                        val now = System.currentTimeMillis()
                        if (now - lastHeartbeat > 5000) {
                            Log.i(TAG, "Host: Broadcast loop heartbeat (IP: $cachedIp, Clients: ${_clientIps.value.size})")
                            lastHeartbeat = now
                        }

                        socket.soTimeout = 1000
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (e: java.net.SocketTimeoutException) {
                            continue
                        }

                        // Filter out self-discovery requests to prevent feedback loop
                        if (isLocalAddress(packet.address)) {
                            Log.d(TAG, "Host: Ignored discovery request from self (${packet.address})")
                            continue
                        }

                        val msg = String(packet.data, 0, packet.length)
                        Log.i(TAG, "Host: Received discovery message '$msg' from ${packet.address}:${packet.port}")
                        if (msg == DISCOVERY_MSG) {
                            if (System.currentTimeMillis() - lastIpCheck > 30000) {
                                cachedIp = getIpAddress()
                                lastIpCheck = System.currentTimeMillis()
                            }

                            val response = "$HOST_MSG_PREFIX;$deviceName;${Build.MODEL};$cachedIp;$STREAMING_PORT;$PROTOCOL_VERSION"
                            val responseData = response.toByteArray()

                            generalExecutor.execute {
                                var responseSocket: DatagramSocket? = null
                                try {
                                    responseSocket = DatagramSocket()
                                    responseSocket.broadcast = true

                                    // Direct response to sender at DISCOVERY_RESPONSE_PORT (8891)
                                    val directPacket8891 = DatagramPacket(
                                        responseData,
                                        responseData.size,
                                        packet.address,
                                        DISCOVERY_RESPONSE_PORT
                                    )
                                    responseSocket.send(directPacket8891)

                                    if (packet.port != DISCOVERY_RESPONSE_PORT) {
                                        val directPacketPort = DatagramPacket(
                                            responseData,
                                            responseData.size,
                                            packet.address,
                                            packet.port
                                        )
                                        responseSocket.send(directPacketPort)
                                    }

                                    // Multicast response
                                    try {
                                        val mcastPacket = DatagramPacket(
                                            responseData,
                                            responseData.size,
                                            multicastGroupAddress,
                                            DISCOVERY_RESPONSE_PORT
                                        )
                                        responseSocket.send(mcastPacket)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Host: Multicast response failed", e)
                                    }

                                    // Broadcast fallback
                                    val targets = getBroadcastAddresses()
                                    for (target in targets) {
                                        try {
                                            val broadcastPacket = DatagramPacket(
                                                responseData,
                                                responseData.size,
                                                target,
                                                DISCOVERY_RESPONSE_PORT
                                            )
                                            responseSocket.send(broadcastPacket)
                                        } catch (e: Exception) {}
                                    }
                                    Log.i(TAG, "Host: Sent host info responses to ${packet.address}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Host: Failed to send discovery response", e)
                                } finally {
                                    responseSocket?.close()
                                }
                            }

                            // Add client IP to streaming list (deduplicated)
                            val clientAddr = packet.address
                            if (clientAddr != null && !isLocalAddress(clientAddr) && !_clientIps.value.containsKey(clientAddr)) {
                                generalExecutor.execute {
                                    try {
                                        Thread.sleep(200)
                                        clientLastSeenMap[clientAddr] = System.currentTimeMillis()
                                        _clientIps.update { current ->
                                            if (current.containsKey(clientAddr)) current
                                            else current + (clientAddr to null)
                                        }
                                        Log.i(TAG, "Host: Added $clientAddr to streaming clients")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Host: Failed to add client", e)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isBroadcasting && !socket.isClosed) {
                            Log.w(TAG, "Host: Discovery receive error (non-fatal): ${e.message}")
                            Thread.sleep(100)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isBroadcasting && socket != null && !socket.isClosed) {
                    Log.e(TAG, "Host: Broadcasting loop error", e)
                }
            } finally {
                socket?.let { leaveMulticastGroup(it, joinedInterfaces) }
                socket?.close()
                if (discoverySocket == socket) {
                    discoverySocket = null
                    isBroadcasting = false
                }
                releaseMulticastLock()
                Log.i(TAG, "Host: Stopped broadcasting")
            }
        }
    }

    fun stopBroadcasting() {
        isBroadcasting = false
        stopLatencyMeasurement()
        discoverySocket?.close()
        discoverySocket = null
        streamingSocket?.close()
        streamingSocket = null
        clientLastSeenMap.clear()
        _clientIps.value = emptyMap()
    }

    // --- Latency & Client Disconnection (Host Mode) ---

    private fun startLatencyMeasurement() {
        if (isMeasuringLatency) return
        isMeasuringLatency = true
        latencyExecutor.execute {
            var latSocket: DatagramSocket? = null
            try {
                latSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                }
                latSocket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
                latSocket.soTimeout = 800
                val buffer = ByteArray(1024)

                while (isMeasuringLatency) {
                    val currentClients = _clientIps.value.keys
                    if (currentClients.isEmpty()) {
                        Thread.sleep(1000)
                        continue
                    }

                    val now = System.currentTimeMillis()

                    // Check for client timeouts / disconnections
                    val timedOutClients = currentClients.filter { ip ->
                        val lastSeen = clientLastSeenMap[ip] ?: now
                        now - lastSeen > CLIENT_TIMEOUT_MS
                    }

                    if (timedOutClients.isNotEmpty()) {
                        for (disconnectedIp in timedOutClients) {
                            clientLastSeenMap.remove(disconnectedIp)
                            _clientIps.update { current -> current - disconnectedIp }
                            Log.i(TAG, "Host: Client $disconnectedIp disconnected (timeout). Removed from streaming clients.")
                        }
                    }

                    // Ping remaining clients
                    for (ip in _clientIps.value.keys) {
                        val pingMsg = "$PING_PREFIX;$now".toByteArray()
                        val packet = DatagramPacket(pingMsg, pingMsg.size, ip, LATENCY_PORT)
                        latSocket.send(packet)
                    }

                    val startListenTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startListenTime < 900) {
                        try {
                            val responsePacket = DatagramPacket(buffer, buffer.size)
                            latSocket.receive(responsePacket)
                            val response = String(responsePacket.data, 0, responsePacket.length)
                            if (response.startsWith(PONG_PREFIX)) {
                                val parts = response.split(";")
                                if (parts.size >= 2) {
                                    val sentTime = parts[1].toLong()
                                    val rtt = System.currentTimeMillis() - sentTime
                                    val latency = (rtt / 2).toInt()

                                    val senderAddr = responsePacket.address
                                    clientLastSeenMap[senderAddr] = System.currentTimeMillis()

                                    _clientIps.update { current ->
                                        if (current.containsKey(senderAddr)) {
                                            current + (senderAddr to latency)
                                        } else current
                                    }
                                }
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Latency receive error", e)
                        }
                    }
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                if (isMeasuringLatency) Log.e(TAG, "Latency measurement loop error", e)
            } finally {
                latSocket?.close()
                isMeasuringLatency = false
            }
        }
    }

    private fun stopLatencyMeasurement() {
        isMeasuringLatency = false
    }

    // --- Ping Responder (Client Mode) ---

    private fun startPingResponder() {
        if (isRespondingToPings) return
        isRespondingToPings = true
        latencyExecutor.execute {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                }
                socket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), LATENCY_PORT))
                pingResponderSocket = socket
                val buffer = ByteArray(1024)
                while (isRespondingToPings && !socket.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length)
                        if (msg.startsWith(PING_PREFIX)) {
                            val parts = msg.split(";")
                            if (parts.size >= 2) {
                                val timestamp = parts[1]
                                val response = "$PONG_PREFIX;$timestamp".toByteArray()
                                val responsePacket = DatagramPacket(
                                    response,
                                    response.size,
                                    packet.address,
                                    packet.port
                                )
                                socket.send(responsePacket)
                            }
                        }
                    } catch (e: Exception) {
                        if (isRespondingToPings && !socket.isClosed) {
                            Log.w(TAG, "Ping responder receive error: ${e.message}")
                            Thread.sleep(100)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRespondingToPings && socket != null && !socket.isClosed) {
                    Log.e(TAG, "Ping responder fatal error", e)
                }
            } finally {
                socket?.close()
                if (pingResponderSocket == socket) {
                    pingResponderSocket = null
                    isRespondingToPings = false
                }
            }
        }
    }

    private fun stopPingResponder() {
        isRespondingToPings = false
        pingResponderSocket?.close()
        pingResponderSocket = null
    }

    // --- Discovery (Client Mode) ---

    fun discoverHosts(onHostFound: (HostInfo) -> Unit) {
        if (isDiscovering) {
            Log.i(TAG, "Client: discoverHosts skipped (already discovering)")
            return
        }
        isDiscovering = true
        Log.i(TAG, "Client: Starting discovery scan...")
        acquireMulticastLock()

        scanExecutor.execute {
            var clientSocket: MulticastSocket? = null
            var joinedInterfaces = emptyList<NetworkInterface>()
            val discoveredIps = Collections.synchronizedSet(mutableSetOf<String>())

            try {
                clientSocket = MulticastSocket(null).apply {
                    reuseAddress = true
                    timeToLive = 1
                    soTimeout = 300
                }
                clientSocket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), DISCOVERY_RESPONSE_PORT))
                joinedInterfaces = joinMulticastGroup(clientSocket)

                val activeSocket = clientSocket
                val endTime = System.currentTimeMillis() + 3500

                val receiveJob = generalExecutor.submit {
                    val buffer = ByteArray(2048)
                    while (isDiscovering && System.currentTimeMillis() < endTime && !activeSocket.isClosed) {
                        try {
                            val responsePacket = DatagramPacket(buffer, buffer.size)
                            activeSocket.receive(responsePacket)

                            // Filter out responses from own device to avoid self-discovery feedback loops
                            if (isLocalAddress(responsePacket.address)) {
                                Log.d(TAG, "Client: Ignored host response from self (${responsePacket.address})")
                                continue
                            }

                            val response = String(responsePacket.data, 0, responsePacket.length)
                            if (response.startsWith(HOST_MSG_PREFIX)) {
                                val parts = response.split(";")
                                if (parts.size >= 6) {
                                    val reportedIp = parts[3]
                                    val actualIp = responsePacket.address.hostAddress ?: reportedIp

                                    if (isLocalIp(reportedIp) || isLocalIp(actualIp)) {
                                        Log.d(TAG, "Client: Ignored host response for own IP ($actualIp / $reportedIp)")
                                        continue
                                    }

                                    if (discoveredIps.add(actualIp)) {
                                        Log.i(TAG, "Client: Found host: ${parts[1]} ($actualIp) from ${responsePacket.address}:${responsePacket.port}")
                                        val host = HostInfo(
                                            name = parts[1],
                                            model = parts[2],
                                            ip = actualIp,
                                            port = parts[4].toInt(),
                                            version = parts[5]
                                        )
                                        onHostFound(host)
                                    }
                                }
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            // Expected timeout
                        } catch (e: Exception) {
                            if (isDiscovering && !activeSocket.isClosed) {
                                Log.w(TAG, "Client: Discovery receive error: ${e.message}")
                            }
                        }
                    }
                }

                val msg = DISCOVERY_MSG.toByteArray()

                // Phase 1: UDP Multicast
                Log.d(TAG, "Client: Phase 1 - Multicast discovery")
                repeat(2) {
                    try {
                        val packet = DatagramPacket(msg, msg.size, multicastGroupAddress, DISCOVERY_PORT)
                        activeSocket.send(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "Client: Multicast send failed", e)
                    }
                    Thread.sleep(150)
                }
                Thread.sleep(400)

                // Phase 2: UDP Broadcast
                if (discoveredIps.isEmpty()) {
                    Log.d(TAG, "Client: Phase 2 - Broadcast discovery")
                    val targets = getBroadcastAddresses()
                    repeat(2) {
                        for (target in targets) {
                            try {
                                val packet = DatagramPacket(msg, msg.size, target, DISCOVERY_PORT)
                                activeSocket.send(packet)
                            } catch (e: Exception) {}
                        }
                        Thread.sleep(150)
                    }
                    Thread.sleep(500)
                }

                // Phase 3: Subnet scan fallback
                if (discoveredIps.isEmpty()) {
                    Log.d(TAG, "Client: Phase 3 - Subnet scan fallback")
                    runSubnetScan(activeSocket, msg)
                    Thread.sleep(500)
                }

                isDiscovering = false
                try {
                    receiveJob.get()
                } catch (e: Exception) {
                    // Ignore listener wait errors
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery scan error/finished: ${e.message}")
            } finally {
                isDiscovering = false
                clientSocket?.let { leaveMulticastGroup(it, joinedInterfaces) }
                clientSocket?.close()
                releaseMulticastLock()
                Log.d(TAG, "Discovery scan finished")
            }
        }
    }

    private fun runSubnetScan(clientSocket: DatagramSocket, msg: ByteArray) {
        try {
            val subnetCandidates = mutableListOf<InetAddress>()
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            for (netIf in interfaces) {
                if (!netIf.isUp || netIf.isLoopback) continue
                for (addrSpec in netIf.interfaceAddresses) {
                    val addr = addrSpec.address
                    if (addr is Inet4Address) {
                        val prefix = addrSpec.networkPrefixLength.toInt()
                        val ipInt = inet4AddressToInt(addr)

                        val effectivePrefix = prefix.coerceAtLeast(24).coerceAtMost(30)
                        val mask = (-1) shl (32 - effectivePrefix)
                        val netAddress = ipInt and mask
                        val broadcastAddress = netAddress or mask.inv()

                        for (hostInt in (netAddress + 1) until broadcastAddress) {
                            if (hostInt != ipInt) {
                                intToInet4Address(hostInt)?.let { candidate ->
                                    if (!isLocalAddress(candidate)) {
                                        subnetCandidates.add(candidate)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (subnetCandidates.isEmpty()) return

            Log.i(TAG, "Client: Subnet scan targeting ${subnetCandidates.size} IPs")
            for (candidate in subnetCandidates) {
                try {
                    val packet = DatagramPacket(msg, msg.size, candidate, DISCOVERY_PORT)
                    clientSocket.send(packet)
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during subnet scan", e)
        }
    }

    private fun inet4AddressToInt(addr: Inet4Address): Int {
        val bytes = addr.address
        return ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    private fun intToInet4Address(ipInt: Int): InetAddress? {
        return try {
            val bytes = byteArrayOf(
                ((ipInt shr 24) and 0xFF).toByte(),
                ((ipInt shr 16) and 0xFF).toByte(),
                ((ipInt shr 8) and 0xFF).toByte(),
                (ipInt and 0xFF).toByte()
            )
            InetAddress.getByAddress(bytes)
        } catch (e: Exception) {
            null
        }
    }

    // --- Streaming (Host Mode) ---

    fun sendFft(fft: IntArray) {
        if (!isBroadcasting || fft.size != 512 || isSendingFft) return

        val clients = _clientIps.value.keys
        if (clients.isEmpty()) return

        isSendingFft = true
        val packed = packUint12(fft)

        streamingExecutor.execute {
            try {
                if (streamingSocket == null) {
                    synchronized(this) {
                        if (streamingSocket == null) {
                            streamingSocket = DatagramSocket()
                            Log.d(TAG, "Created streaming socket")
                        }
                    }
                }
                for (ip in clients) {
                    val packet = DatagramPacket(packed, packed.size, ip, STREAMING_PORT)
                    streamingSocket?.send(packet)
                }
                fftCount++
                if (fftCount % 100 == 0) {
                    Log.i(TAG, "Host: Sent 100 FFT frames to ${clients.size} clients")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Host: Streaming send error", e)
            } finally {
                isSendingFft = false
            }
        }
    }

    // --- Streaming (Client Mode) ---

    fun startListening(hostIp: String? = null, onFftReceived: (IntArray) -> Unit) {
        startListening(hostIp, null, onFftReceived)
    }

    fun startListening(hostIp: String? = null, onDisconnected: (() -> Unit)? = null, onFftReceived: (IntArray) -> Unit) {
        if (isListening) {
            Log.i(TAG, "Client: startListening skipped (already listening)")
            return
        }
        isListening = true
        acquireMulticastLock()
        startPingResponder()
        Log.i(TAG, "Client: Starting to listen for FFT on port $STREAMING_PORT. Target Host: ${hostIp ?: "none"}")

        broadcastExecutor.execute {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 1000
                }
                socket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), STREAMING_PORT))
                listeningSocket = socket

                hostIp?.let { ip ->
                    try {
                        val msg = DISCOVERY_MSG.toByteArray()
                        val packet = DatagramPacket(msg, msg.size, InetAddress.getByName(ip), DISCOVERY_PORT)
                        socket.send(packet)
                        Log.i(TAG, "Client: Sent hole-punch handshake from port $STREAMING_PORT to $ip:$DISCOVERY_PORT")
                    } catch (e: Exception) {
                        Log.w(TAG, "Client: Failed to send initial hole-punch", e)
                    }
                }

                val buffer = ByteArray(2048)
                var receivedCount = 0
                var lastFrameTime = System.currentTimeMillis()

                while (isListening && !socket.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        if (packet.length == 768) {
                            lastFrameTime = System.currentTimeMillis()
                            receivedCount++
                            if (receivedCount % 100 == 0) {
                                Log.i(TAG, "Client: Received 100 FFT frames from ${packet.address}:${packet.port}")
                            }
                            val fft = unpackUint12(packet.data)
                            onFftReceived(fft)
                        } else {
                            if (packet.length > 10) {
                                Log.w(TAG, "Client: Received packet with unexpected length: ${packet.length} from ${packet.address}")
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        if (hostIp != null && System.currentTimeMillis() - lastFrameTime > HOST_STREAMING_TIMEOUT_MS) {
                            Log.w(TAG, "Client: Host $hostIp streaming timeout (disconnected). Stopping listener.")
                            onDisconnected?.invoke()
                            break
                        }
                    } catch (e: Exception) {
                        if (isListening && !socket.isClosed) {
                            Log.w(TAG, "Client: Streaming listen receive error: ${e.message}")
                            Thread.sleep(10)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isListening && socket != null && !socket.isClosed) {
                    Log.e(TAG, "Client: Streaming listen fatal error", e)
                }
            } finally {
                socket?.close()
                if (listeningSocket == socket) {
                    listeningSocket = null
                    isListening = false
                }
                stopPingResponder()
                releaseMulticastLock()
                Log.i(TAG, "Client: Stopped listening")
            }
        }
    }

    fun stopListening() {
        isListening = false
        stopPingResponder()
        listeningSocket?.close()
    }

    fun sendHandshake(ip: String, port: Int) {
        scanExecutor.execute {
            try {
                val address = InetAddress.getByName(ip)
                val msg = DISCOVERY_MSG.toByteArray()
                val socket = DatagramSocket()
                val packet = DatagramPacket(msg, msg.size, address, port)
                socket.send(packet)
                socket.close()
                Log.d(TAG, "Sent handshake to $ip:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send handshake to $ip:$port", e)
            }
        }
    }

    // --- Packing Logic ---

    fun packUint12(data: IntArray): ByteArray {
        val packed = ByteArray(768)
        for (i in 0 until 256) {
            val v1 = data[i * 2] and 0xFFF
            val v2 = data[i * 2 + 1] and 0xFFF

            packed[i * 3] = (v1 and 0xFF).toByte()
            packed[i * 3 + 1] = (((v1 shr 8) and 0x0F) or ((v2 shl 4) and 0xF0)).toByte()
            packed[i * 3 + 2] = ((v2 shr 4) and 0xFF).toByte()
        }
        return packed
    }

    fun unpackUint12(packed: ByteArray): IntArray {
        val data = IntArray(512)
        for (i in 0 until 256) {
            val b0 = packed[i * 3].toInt() and 0xFF
            val b1 = packed[i * 3 + 1].toInt() and 0xFF
            val b2 = packed[i * 3 + 2].toInt() and 0xFF

            val v1 = b0 or ((b1 and 0x0F) shl 8)
            val v2 = ((b1 and 0xF0) shr 4) or (b2 shl 4)

            data[i * 2] = v1
            data[i * 2 + 1] = v2
        }
        return data
    }

    // --- Helpers ---

    private fun getIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val sorted = interfaces.sortedByDescending {
                it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("softap")
            }
            for (networkInterface in sorted) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (address is Inet4Address) {
                        val ip = address.hostAddress
                        if (ip != null && ip != "0.0.0.0") return ip
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address via NetworkInterface", e)
        }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager.connectionInfo
            @Suppress("DEPRECATION")
            val ipAddress = connectionInfo.ipAddress
            if (ipAddress != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    (ipAddress shr 8) and 0xff,
                    (ipAddress shr 16) and 0xff,
                    (ipAddress shr 24) and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address via WifiManager", e)
        }
        return "0.0.0.0"
    }

    private fun getBroadcastAddresses(): Set<InetAddress> {
        val targets = mutableSetOf<InetAddress>()

        try {
            targets.add(InetAddress.getByName("255.255.255.255"))
        } catch (ignored: Exception) {}

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (netIf in interfaces) {
                if (!netIf.isUp || netIf.isLoopback) continue
                for (interfaceAddress in netIf.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null && interfaceAddress.address is Inet4Address) {
                        targets.add(broadcast)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting broadcast addresses via NetworkInterface", e)
        }

        return targets
    }
}
