package com.better.nothing.music.vizualizer.logic

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class UdpNetworkSync(private val context: Context) {
    companion object {
        private const val TAG = "BNMV:UdpSync"
        private const val DISCOVERY_PORT = 8888
        private const val DISCOVERY_RESPONSE_PORT = 8891
        private const val STREAMING_PORT = 8889
        private const val LATENCY_PORT = 8890
        private const val DISCOVERY_MSG = "BNMV_DISCOVER"
        private const val HOST_MSG_PREFIX = "BNMV_HOST"
        private const val PING_PREFIX = "BNMV_PING"
        private const val PONG_PREFIX = "BNMV_PONG"
        private const val PROTOCOL_VERSION = "6.0.made.by.aleks.levet"
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

    private var discoverySocket: DatagramSocket? = null
    private var streamingSocket: DatagramSocket? = null
    private var listeningSocket: DatagramSocket? = null
    private var pingResponderSocket: DatagramSocket? = null
    
    private val _clientIps = MutableStateFlow<Map<InetAddress, Int?>>(emptyMap())
    val clientIps = _clientIps.asStateFlow()

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

    private fun acquireMulticastLock() {
        synchronized(lockSync) {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (multicastLock == null) {
                    multicastLock = wifiManager.createMulticastLock(TAG)
                    multicastLock?.setReferenceCounted(true)
                }
                multicastLock?.acquire()
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
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release MulticastLock", e)
            }
        }
    }

    // --- Discovery (Host Mode) ---

    fun startBroadcasting(deviceName: String) {
        if (isBroadcasting) {
            Log.i(TAG, "Host: startBroadcasting skipped (already broadcasting)")
            return
        }
        isBroadcasting = true
        acquireMulticastLock()
        startLatencyMeasurement()
        
        cachedIp = getIpAddress()
        lastIpCheck = System.currentTimeMillis()
        Log.i(TAG, "Host: Initialized with deviceName=$deviceName, IP=$cachedIp")

        broadcastExecutor.execute {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                }
                // Explicitly bind to IPv4 0.0.0.0 to avoid IPv6 issues on some tablets
                socket.bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), DISCOVERY_PORT))
                
                discoverySocket = socket
                val buffer = ByteArray(1024)
                Log.i(TAG, "Host: Started broadcasting on 0.0.0.0:$DISCOVERY_PORT")
                
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
                        
                        val msg = String(packet.data, 0, packet.length)
                        Log.i(TAG, "Host: Received discovery message '$msg' from ${packet.address}:${packet.port}")
                        if (msg == DISCOVERY_MSG) {
                            // Periodically refresh IP if it's been a while
                            if (System.currentTimeMillis() - lastIpCheck > 30000) {
                                cachedIp = getIpAddress()
                                lastIpCheck = System.currentTimeMillis()
                            }

                            val response = "$HOST_MSG_PREFIX;$deviceName;${Build.MODEL};$cachedIp;$STREAMING_PORT;$PROTOCOL_VERSION"
                            val responseData = response.toByteArray()
                            
                            // 1. Direct Response
                            val responsePacket = DatagramPacket(
                                responseData,
                                responseData.size,
                                packet.address,
                                DISCOVERY_RESPONSE_PORT
                            )
                            
                            // Use a fresh socket for sending to avoid binding issues or port locks
                            generalExecutor.execute {
                                var responseSocket: DatagramSocket? = null
                                try {
                                    responseSocket = DatagramSocket()
                                    responseSocket.broadcast = true
                                    
                                    // Send direct response a few times
                                    repeat(2) {
                                        responseSocket.send(responsePacket)
                                        Thread.sleep(20)
                                    }
                                    
                                    // 2. Broadcast Fallback - Shout to the whole network
                                    val targets = getDiscoveryTargets()
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
                                    Log.i(TAG, "Host: Sent host info responses (direct + broadcast fallback) to ${packet.address}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Host: Failed to send discovery response", e)
                                } finally {
                                    responseSocket?.close()
                                }
                            }
                            
                            // Add to streaming list with a small delay
                            // This gives the client's UI/Discovery enough time to process the response
                            // before we start flooding it with FFT data.
                            if (!_clientIps.value.containsKey(packet.address)) {
                                generalExecutor.execute {
                                    try {
                                        Thread.sleep(500)
                                        _clientIps.update { current ->
                                            if (current.containsKey(packet.address)) current
                                            else current + (packet.address to null)
                                        }
                                        Log.i(TAG, "Host: Added ${packet.address} to streaming clients after delay")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Host: Failed to add client after delay", e)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isBroadcasting && !socket.isClosed) {
                            Log.w(TAG, "Host: Discovery receive error (non-fatal): ${e.message}")
                            Thread.sleep(100) // Cooling off
                        }
                    }
                }
            } catch (e: Exception) {
                if (isBroadcasting && socket != null && !socket.isClosed) {
                    Log.e(TAG, "Host: Broadcasting loop error", e)
                }
            } finally {
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
        _clientIps.value = emptyMap()
    }

    // --- Latency (Host Mode) ---

    private fun startLatencyMeasurement() {
        if (isMeasuringLatency) return
        isMeasuringLatency = true
        latencyExecutor.execute {
            var latSocket: DatagramSocket? = null
            try {
                latSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                }
                latSocket.bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
                latSocket.soTimeout = 800
                val buffer = ByteArray(1024)
                
                while (isMeasuringLatency) {
                    val currentClients = _clientIps.value.keys
                    if (currentClients.isEmpty()) {
                        Thread.sleep(1000)
                        continue
                    }
                    
                    val now = System.currentTimeMillis()
                    for (ip in currentClients) {
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
                                    
                                    _clientIps.update { current ->
                                        if (current.containsKey(responsePacket.address)) {
                                            current + (responsePacket.address to latency)
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
                socket.bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), LATENCY_PORT))
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
            var clientSocket: DatagramSocket? = null
            try {
                // Bind to a fixed response port for predictability
                clientSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 500
                }
                clientSocket.bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), DISCOVERY_RESPONSE_PORT))
                
                // Start receiving in parallel
                val receiveJob = generalExecutor.submit {
                    val buffer = ByteArray(2048)
                    val startTime = System.currentTimeMillis()
                    val discoveredIps = mutableSetOf<String>()
                    
                    while (isDiscovering && System.currentTimeMillis() - startTime < 4500) {
                        try {
                            val responsePacket = DatagramPacket(buffer, buffer.size)
                            clientSocket.receive(responsePacket)
                            val response = String(responsePacket.data, 0, responsePacket.length)
                            
                            if (response.startsWith(HOST_MSG_PREFIX)) {
                                val parts = response.split(";")
                                if (parts.size >= 6) {
                                    val reportedIp = parts[3]
                                    val actualIp = responsePacket.address.hostAddress ?: reportedIp
                                    
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
                            // Continue
                        } catch (e: Exception) {
                            if (isDiscovering && !clientSocket.isClosed) {
                                Log.w(TAG, "Client: Discovery receive error: ${e.message}")
                            }
                        }
                    }
                }

                // Send discovery packets multiple times
                val targets = getDiscoveryTargets()
                val msg = DISCOVERY_MSG.toByteArray()
                
                repeat(5) {
                    for (target in targets) {
                        try {
                            val packet = DatagramPacket(msg, msg.size, target, DISCOVERY_PORT)
                            clientSocket.send(packet)
                        } catch (e: Exception) {}
                    }
                    Thread.sleep(300)
                }

                receiveJob.get()
            } catch (e: Exception) {
                Log.e(TAG, "Discovery scan failed", e)
            } finally {
                clientSocket?.close()
                isDiscovering = false
                releaseMulticastLock()
                Log.d(TAG, "Discovery scan finished")
            }
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
                }
                socket.bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), STREAMING_PORT))
                listeningSocket = socket
                
                // Firewall hole-punch: send a packet FROM our listening port TO the host's discovery port
                // This informs the host of our existence and "vouchers" for incoming traffic from that IP.
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
                while (isListening && !socket.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        if (packet.length == 768) {
                            receivedCount++
                            if (receivedCount % 100 == 0) {
                                Log.i(TAG, "Client: Received 100 FFT frames from ${packet.address}:${packet.port}")
                            }
                            val fft = unpackUint12(packet.data)
                            onFftReceived(fft)
                        } else {
                            // Ignore small packets (like BNMV_PUNCH or handshakes)
                            if (packet.length > 10) {
                                Log.w(TAG, "Client: Received packet with unexpected length: ${packet.length} from ${packet.address}")
                            }
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

    /**
     * Packs 512 uint12 values (0-4095) into 768 bytes.
     * 2 values take 24 bits (3 bytes).
     */
    fun packUint12(data: IntArray): ByteArray {
        val packed = ByteArray(768)
        for (i in 0 until 256) {
            val v1 = data[i * 2] and 0xFFF
            val v2 = data[i * 2 + 1] and 0xFFF
            
            // v1: 12 bits, v2: 12 bits
            // Byte 0: v1 low 8 bits
            // Byte 1: v1 high 4 bits | v2 low 4 bits
            // Byte 2: v2 high 8 bits
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
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            // Prioritize Wi-Fi and Hotspot interfaces
            val sorted = interfaces.sortedByDescending { 
                it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("softap") 
            }
            for (networkInterface in sorted) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = java.util.Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (address is java.net.Inet4Address) {
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
            val connectionInfo = wifiManager.connectionInfo
            val ipAddress = connectionInfo.ipAddress
            if (ipAddress != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address via WifiManager", e)
        }
        return "0.0.0.0"
    }

    private fun getDiscoveryTargets(): Set<InetAddress> {
        val targets = mutableSetOf<InetAddress>()
        
        // 1. Universal Broadcast
        try { targets.add(InetAddress.getByName("255.255.255.255")) } catch (ignored: Exception) {}
        
        // 2. NetworkInterface Broadcasts
        try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) targets.add(broadcast)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting broadcast addresses via NetworkInterface", e)
        }

        // 3. DHCP Gateway (Crucial for Hotspots/Restricted networks)
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp: DhcpInfo = wifiManager.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val gateway = String.format(
                    "%d.%d.%d.%d",
                    dhcp.gateway and 0xff,
                    dhcp.gateway shr 8 and 0xff,
                    dhcp.gateway shr 16 and 0xff,
                    dhcp.gateway shr 24 and 0xff
                )
                targets.add(InetAddress.getByName(gateway))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting gateway via WifiManager", e)
        }

        // 4. Common Hotspot Subnets Fallback
        try {
            targets.add(InetAddress.getByName("192.168.43.1"))   // Android Default Hotspot Gateway
            targets.add(InetAddress.getByName("192.168.43.255")) // Android Default Hotspot Broadcast
            targets.add(InetAddress.getByName("172.20.10.1"))    // iOS Default Hotspot Gateway
            targets.add(InetAddress.getByName("192.168.1.1"))    // Common Router Gateway
        } catch (ignored: Exception) {}

        return targets
    }
}
