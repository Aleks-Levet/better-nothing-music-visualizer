# Accessible Intents:
## Other apps can send broadcasts with the following actions:
From version 4.0.1 of the app:

### Start/Stop/Toggle Visualizer:

* `com.better.nothing.music.vizualizer.ACTION_START`

* `com.better.nothing.music.vizualizer.ACTION_STOP`

* `com.better.nothing.music.vizualizer.ACTION_TOGGLE`

### Settings Control (supports boolean extra enabled):

* `com.better.nothing.music.vizualizer.ACTION_TOGGLE_GLYPHS`

* `com.better.nothing.music.vizualizer.ACTION_TOGGLE_HAPTICS`

* `com.better.nothing.music.vizualizer.ACTION_TOGGLE_TORCH`

* `com.better.nothing.music.vizualizer.ACTION_TOGGLE_BROADCAST`

* `com.better.nothing.music.vizualizer.ACTION_TOGGLE_OVERLAY`

* `com.better.nothing.music.vizualizer.ACTION_TOGGLE_EDGE`

* `com.better.nothing.music.vizualizer.ACTION_TOGGLE_LENS`

### Source & Presets:

* `com.better.nothing.music.vizualizer.ACTION_SET_SOURCE` (String extra source: `INTERNAL`, `MIC`, `VIZUALIZER`, `NETWORK`)

* `com.better.nothing.music.vizualizer.ACTION_SET_PRESET` (String extra preset: the key of the preset, e.g., `np2`)


# Integrate BNMV in your app:
 This lets your users use BNMV right from you app, without having the "screen share" prompt and icon in the staus bar. Feel free to integrate it to your music players for example!

 So your app needs to send the **FFT of the currently playing audio** at 60fps over UDP.

### External Audio Data (UDP):

* `com.better.nothing.music.vizualizer.ACTION_CONNECT_UDP`
  * `ip` (String): The IP address of your device/server.
  * `port` (Int): The port your server is listening on for a handshake (default: 8888).

#### Upon receiving this intent, BNMV will:
1. Switch its audio source to `NETWORK`.
2. Start the visualizer if it's not already running.
3. Send a "Handshake" UDP packet containing the string `BNMV_DISCOVER` to your specified `ip` and `port`.
4. Your app should then start sending audio data to BNMV's IP on port `8889`. (Note: You can get BNMV's IP address from the source address of the handshake packet).

#### Optional host discovery response

If you are implementing the LAN host-discovery protocol, respond to `BNMV_DISCOVER` with a UDP packet sent to the requester's source IP on port `8891`:

```text
BNMV_HOST;<deviceName>;<model>;<hostIp>;<streamingPort>;<protocolVersion>
```

For example:

```text
BNMV_HOST;My Music App;Nothing Phone (2);192.168.1.15;8889;6.0.made.by.aleks.levet
```

The fields are, in order: the message prefix, host/device name, device model, host IP address, FFT streaming port, and protocol version. The current protocol version name is `6.0.made.by.aleks.levet` 

For the explicit `ACTION_CONNECT_UDP` integration above, no response is required; BNMV uses the source IP of `BNMV_DISCOVER` and expects the host to send FFT data to UDP port `8889`.

#### Latency ping response

After discovery, BNMV may send latency probes to the host over UDP port `8890`. A probe has this format:

```text
BNMV_PING;<timestamp>
```

Reply immediately with `BNMV_PONG` and the exact same timestamp, sending the response to the source IP and source port of the received probe:

```text
BNMV_PONG;<timestamp>
```

For example, if BNMV sends `BNMV_PING;1724567890123`, respond with `BNMV_PONG;1724567890123`. Do not replace or reinterpret the timestamp; BNMV uses it to calculate the round-trip latency.

#### Audio Data Format:
BNMV expects **512 frequency magnitudes** packed into a **768-byte** UDP packet, sent approximately 60 times per second.

**Crucial Details:**
* **Logarithmic Spacing:** The 512 bins are NOT linear. They are logarithmically spaced from **30Hz** to **16kHz**.
* **Bin Calculation:** The lower frequency edge of bin `i` is calculated as:
  `f(i) = 30 * (16000 / 30) ^ (i / 512)`
* **Magnitudes:** Each magnitude should be an integer between `0` and `4095` (12-bit).

**Packing format:**
Two 12-bit magnitudes (v1, v2) are packed into 3 bytes:
* Byte 0: `v1 & 0xFF` (Low 8 bits of v1)
* Byte 1: `((v1 >> 8) & 0x0F) | ((v2 << 4) & 0xF0)` (High 4 bits of v1 and Low 4 bits of v2)
* Byte 2: `(v2 >> 4) & 0xFF` (High 8 bits of v2)

Total packet size = (512 / 2) * 3 = 768 bytes.

---

### Quick Start Integration (Kotlin)

1. **Broadcast to BNMV:**
   ```kotlin
   val intent = Intent("com.better.nothing.music.vizualizer.ACTION_CONNECT_UDP").apply {
       putExtra("ip", getLocalIpAddress()) 
       putExtra("port", 8888)
   }
   sendBroadcast(intent)
   ```

2. **Wait for Handshake:**
   Listen on UDP port `8888`. When you receive `"BNMV_DISCOVER"`, you have the target IP.

3. **Send Audio Data:**
   Send the 768-byte packed FFT to port `8889` of the target IP at 60fps.

**Mapping to Log Bins:**
The visualizer expects 512 bins logarithmically distributed from 30Hz to 16kHz. If your FFT provides linear bins, interpolate them using the center frequency for each log bin:
`fCenter(i) = 30 * (16000 / 30) ^ ((i + 0.5) / 512)`

For a full implementation guide including the packing algorithm, see the [UDP Integration Guide](file:///data/Documents/GitProjects/better-nothing-music-visualizer/Docs/UDP%20Integration%20Guide.md).
