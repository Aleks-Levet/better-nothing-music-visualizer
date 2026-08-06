package com.better.nothing.music.vizualizer.logic

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
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
        private const val DISCOVERY_MSG = "BNMV_DISCOVER"
        private const val HOST_MSG_PREFIX = "BNMV_HOST"
        private const val PROTOCOL_VERSION = "4.0.made.by.aleks.levet"
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
    
    private val clientIps = mutableSetOf<InetAddress>()
    private var isBroadcasting = false
    private var isDiscovering = false
    private var isListening = false
    
    private var multicastLock: WifiManager.MulticastLock? = null

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock(TAG)
            }
            multicastLock?.acquire()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire MulticastLock", e)
        }
    }

    private fun releaseMulticastLock() {
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

    // --- Discovery (Host Mode) ---

    fun startBroadcasting(deviceName: String) {
        if (isBroadcasting) return
        isBroadcasting = true
        acquireMulticastLock()
        executor.execute {
            try {
                discoverySocket = DatagramSocket(DISCOVERY_PORT).apply { broadcast = true }
                val buffer = ByteArray(1024)
                while (isBroadcasting) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    discoverySocket?.receive(packet)
                    val msg = String(packet.data, 0, packet.length)
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
                        
                        // Add to streaming list if not already there
                        synchronized(clientIps) {
                            clientIps.add(packet.address)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isBroadcasting) {
                    Log.e(TAG, "Broadcasting error", e)
                }
            } finally {
                discoverySocket?.close()
                discoverySocket = null
                releaseMulticastLock()
            }
        }
    }

    fun stopBroadcasting() {
        isBroadcasting = false
        discoverySocket?.close()
        streamingSocket?.close()
        streamingSocket = null
        synchronized(clientIps) {
            clientIps.clear()
        }
    }

    // --- Discovery (Client Mode) ---

    fun discoverHosts(onHostFound: (HostInfo) -> Unit) {
        if (isDiscovering) return
        isDiscovering = true
        acquireMulticastLock()
        executor.execute {
            var clientSocket: DatagramSocket? = null
            try {
                clientSocket = DatagramSocket().apply { broadcast = true; soTimeout = 3000 }
                val broadcastAddr = getBroadcastAddress() ?: return@execute
                val msg = DISCOVERY_MSG.toByteArray()
                val packet = DatagramPacket(msg, msg.size, broadcastAddr, DISCOVERY_PORT)
                clientSocket.send(packet)

                val buffer = ByteArray(1024)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 3000) {
                    try {
                        val responsePacket = DatagramPacket(buffer, buffer.size)
                        clientSocket.receive(responsePacket)
                        val response = String(responsePacket.data, 0, responsePacket.length)
                        if (response.startsWith(HOST_MSG_PREFIX)) {
                            val parts = response.split(";")
                            if (parts.size >= 6) {
                                val host = HostInfo(
                                    name = parts[1],
                                    model = parts[2],
                                    ip = parts[3],
                                    port = parts[4].toInt(),
                                    version = parts[5]
                                )
                                onHostFound(host)
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error", e)
            } finally {
                clientSocket?.close()
                isDiscovering = false
                releaseMulticastLock()
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
                    streamingSocket = DatagramSocket()
                }
                synchronized(clientIps) {
                    val it = clientIps.iterator()
                    while (it.hasNext()) {
                        val ip = it.next()
                        val packet = DatagramPacket(packed, packed.size, ip, STREAMING_PORT)
                        streamingSocket?.send(packet)
                    }
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
        executor.execute {
            try {
                listeningSocket = DatagramSocket(STREAMING_PORT)
                val buffer = ByteArray(768)
                while (isListening) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    listeningSocket?.receive(packet)
                    if (packet.length == 768) {
                        val fft = unpackUint12(packet.data)
                        onFftReceived(fft)
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
            }
        }
    }

    fun stopListening() {
        isListening = false
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
            val v2 = (b1 shr 4) or (b2 shl 4)
            
            data[i * 2] = v1
            data[i * 2 + 1] = v2
        }
        return data
    }

    // --- Helpers ---

    private fun getIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wifiManager.connectionInfo
            val ipAddress = connectionInfo.ipAddress
            if (ipAddress == 0) return "0.0.0.0"
            
            return String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
            return "0.0.0.0"
        }
    }

    private fun getBroadcastAddress(): InetAddress? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp: DhcpInfo = wifiManager.dhcpInfo ?: return null
            val broadcast = dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
            val quads = ByteArray(4)
            for (k in 0..3) quads[k] = (broadcast shr k * 8 and 0xff).toByte()
            return InetAddress.getByAddress(quads)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting broadcast address", e)
            return null
        }
    }
}
