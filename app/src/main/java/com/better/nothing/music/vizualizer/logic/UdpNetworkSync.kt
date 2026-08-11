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
import java.util.concurrent.TimeUnit

class UdpNetworkSync(private val context: Context) {
    companion object {
        private const val TAG = "BNMV:UdpSync"
        private const val DISCOVERY_PORT = 8888
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

    private val executor = Executors.newCachedThreadPool()
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
        if (isBroadcasting) return
        isBroadcasting = true
        acquireMulticastLock()
        startLatencyMeasurement()
        executor.execute {
            try {
                discoverySocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(java.net.InetSocketAddress(DISCOVERY_PORT))
                }
                val buffer = ByteArray(1024)
                while (isBroadcasting) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    discoverySocket?.receive(packet)
                    val msg = String(packet.data, 0, packet.length)
                    Log.d(TAG, "Received discovery message: $msg from ${packet.address}")
                    if (msg == DISCOVERY_MSG) {
                        val response = "$HOST_MSG_PREFIX;$deviceName;${Build.MODEL};${getIpAddress()};$STREAMING_PORT;$PROTOCOL_VERSION"
                        val responseData = response.toByteArray()
                        val responsePacket = DatagramPacket(
                            responseData,
                            responseData.size,
                            packet.address,
                            packet.port
                        )
                        discoverySocket?.send(responsePacket)
                        Log.d(TAG, "Sent host info to ${packet.address}")
                        
                        // Add to streaming list if not already there
                        if (!_clientIps.value.containsKey(packet.address)) {
                            _clientIps.update { current ->
                                if (current.containsKey(packet.address)) current
                                else current + (packet.address to null)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isBroadcasting) {
                    Log.e(TAG, "Broadcasting error", e)
                }
            } finally {
                isBroadcasting = false
                discoverySocket?.close()
                discoverySocket = null
                releaseMulticastLock()
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
        executor.execute {
            var latSocket: DatagramSocket? = null
            try {
                latSocket = DatagramSocket().apply {
                    soTimeout = 800
                }
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
        executor.execute {
            try {
                pingResponderSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(LATENCY_PORT))
                }
                val buffer = ByteArray(1024)
                while (isRespondingToPings) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    pingResponderSocket?.receive(packet)
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
                            pingResponderSocket?.send(responsePacket)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRespondingToPings) Log.e(TAG, "Ping responder error", e)
            } finally {
                pingResponderSocket?.close()
                pingResponderSocket = null
                isRespondingToPings = false
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
        if (isDiscovering) return
        isDiscovering = true
        Log.d(TAG, "Starting discovery...")
        acquireMulticastLock()
        executor.execute {
            var clientSocket: DatagramSocket? = null
            try {
                clientSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1000
                }
                clientSocket.bind(null)
                
                val targets = getDiscoveryTargets()
                Log.d(TAG, "Sending discovery to targets: $targets from port ${clientSocket.localPort}")
                val msg = DISCOVERY_MSG.toByteArray()
                
                // Send discovery packets multiple times to all targets
                repeat(3) {
                    for (target in targets) {
                        try {
                            val packet = DatagramPacket(msg, msg.size, target, DISCOVERY_PORT)
                            clientSocket.send(packet)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to send discovery to $target: ${e.message}")
                        }
                    }
                    Thread.sleep(200)
                }

                val buffer = ByteArray(1024)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 3500) {
                    try {
                        val responsePacket = DatagramPacket(buffer, buffer.size)
                        clientSocket.receive(responsePacket)
                        val response = String(responsePacket.data, 0, responsePacket.length)
                        Log.d(TAG, "Received response: $response from ${responsePacket.address}")
                        if (response.startsWith(HOST_MSG_PREFIX)) {
                            val parts = response.split(";")
                            if (parts.size >= 6) {
                                val reportedIp = parts[3]
                                val actualIp = responsePacket.address.hostAddress ?: reportedIp
                                
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
                    } catch (e: java.net.SocketTimeoutException) {
                        // Continue until time limit
                    } catch (e: Exception) {
                        Log.e(TAG, "Receive error during discovery", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error", e)
            } finally {
                clientSocket?.close()
                isDiscovering = false
                releaseMulticastLock()
                Log.d(TAG, "Discovery finished")
            }
        }
    }

    // --- Streaming (Host Mode) ---

    fun sendFft(fft: IntArray) {
        if (!isBroadcasting || fft.size != 512) return
        val packed = packUint12(fft)
        executor.execute {
            try {
                if (streamingSocket == null) {
                    synchronized(this) {
                        if (streamingSocket == null) {
                            streamingSocket = DatagramSocket()
                            Log.d(TAG, "Created streaming socket")
                        }
                    }
                }
                val clients = _clientIps.value.keys
                if (clients.isEmpty()) return@execute
                for (ip in clients) {
                    val packet = DatagramPacket(packed, packed.size, ip, STREAMING_PORT)
                    streamingSocket?.send(packet)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Streaming send error", e)
            }
        }
    }

    // --- Streaming (Client Mode) ---

    fun startListening(onFftReceived: (IntArray) -> Unit) {
        if (isListening) return
        isListening = true
        acquireMulticastLock()
        startPingResponder()
        Log.d(TAG, "Starting to listen for FFT on port $STREAMING_PORT")
        executor.execute {
            try {
                listeningSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(STREAMING_PORT))
                }
                val buffer = ByteArray(768)
                while (isListening) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    listeningSocket?.receive(packet)
                    if (packet.length == 768) {
                        val fft = unpackUint12(packet.data)
                        onFftReceived(fft)
                    } else {
                        Log.w(TAG, "Received packet with unexpected length: ${packet.length}")
                    }
                }
            } catch (e: Exception) {
                if (isListening) {
                    Log.e(TAG, "Streaming listen error", e)
                }
            } finally {
                listeningSocket?.close()
                listeningSocket = null
                isListening = false
                stopPingResponder()
                releaseMulticastLock()
                Log.d(TAG, "Stopped listening")
            }
        }
    }

    fun stopListening() {
        isListening = false
        stopPingResponder()
        listeningSocket?.close()
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
