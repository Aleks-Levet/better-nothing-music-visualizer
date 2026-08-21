# BNMV UDP Integration Guide

To integrate BNMV as an external visualizer for your app, follow these steps to send audio data correctly.

## 1. Trigger Connection
Send a broadcast intent from your app to tell BNMV to connect to you.

```kotlin
val intent = Intent("com.better.nothing.music.vizualizer.ACTION_CONNECT_UDP").apply {
    putExtra("ip", "YOUR_DEVICE_IP") // e.g. "192.168.1.15"
    putExtra("port", 8888)           // The port you will listen for handshake on
}
context.sendBroadcast(intent)
```

## 2. Handle the Handshake
BNMV will switch to `NETWORK` mode and send a `BNMV_DISCOVER` packet to your IP/Port.

```kotlin
val socket = DatagramSocket(8888)
val buffer = ByteArray(1024)
val packet = DatagramPacket(buffer, buffer.size)

socket.receive(packet)
val message = String(packet.data, 0, packet.length)

if (message == "BNMV_DISCOVER") {
    val bnmvAddress = packet.address
    // Start sending audio data to bnmvAddress on port 8889
}
```

## 3. Map FFT to Logarithmic Bins
BNMV expects 512 logarithmic bins. If you have a standard linear FFT result (magnitudes), you must map them.

```kotlin
fun mapToLogBins(linearMagnitudes: FloatArray, sampleRate: Int, fftSize: Int): IntArray {
    val logBins = IntArray(512)
    val hzPerBin = sampleRate.toFloat() / fftSize
    val fMin = 30f
    val fMax = 16000f

    for (i in 0 until 512) {
        // Calculate center frequency for this log bin
        val fLow = fMin * Math.pow(fMax / fMin, i / 512.0).toFloat()
        val fHigh = fMin * Math.pow(fMax / fMin, (i + 1) / 512.0).toFloat()
        val fCenter = (fLow + fHigh) / 2f

        // Linear interpolation from your FFT magnitudes
        val continuousIndex = fCenter / hzPerBin
        val idx0 = continuousIndex.toInt().coerceIn(0, linearMagnitudes.size - 1)
        val idx1 = (idx0 + 1).coerceIn(0, linearMagnitudes.size - 1)
        val t = continuousIndex - idx0

        val magnitude = linearMagnitudes[idx0] * (1f - t) + linearMagnitudes[idx1] * t
        
        // Scale to 12-bit (0-4095). 
        // Adjust the multiplier based on your audio engine's output scale.
        logBins[i] = (magnitude * 4095f).toInt().coerceIn(0, 4095)
    }
    return logBins
}
```

## 4. Pack and Send (768 bytes)
Pack the 512 `Int` values into the 24-bit (3 bytes per 2 values) format.

```kotlin
fun packData(logBins: IntArray): ByteArray {
    val packed = ByteArray(768)
    for (i in 0 until 256) {
        val v1 = logBins[i * 2] and 0xFFF
        val v2 = logBins[i * 2 + 1] and 0xFFF
        
        // Byte 0: v1 low 8 bits
        packed[i * 3] = (v1 and 0xFF).toByte()
        // Byte 1: v1 high 4 bits | v2 low 4 bits
        packed[i * 3 + 1] = (((v1 shr 8) and 0x0F) or ((v2 shl 4) and 0xF0)).toByte()
        // Byte 2: v2 high 8 bits
        packed[i * 3 + 2] = ((v2 shr 4) and 0xFF).toByte()
    }
    return packed
}

// Send loop (approx 60 times per second)
val data = packData(logBins)
val packet = DatagramPacket(data, data.size, bnmvAddress, 8889)
streamingSocket.send(packet)
```

> [!TIP]
> **Performance:** Ensure your FFT calculation and packing loop runs efficiently. Using a pre-calculated mapping table for the frequencies will significantly reduce CPU usage in the 60fps loop.
