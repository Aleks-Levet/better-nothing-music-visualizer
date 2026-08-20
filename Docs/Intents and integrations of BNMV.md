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

### External Audio Data (UDP):

* `com.better.nothing.music.vizualizer.ACTION_CONNECT_UDP`
  * `ip` (String): The IP address of your device/server.
  * `port` (Int): The port your server is listening on for a handshake (default: 8888).

Upon receiving this intent, BNMV will:
1. Switch its audio source to `NETWORK`.
2. Start the visualizer if it's not already running.
3. Send a "Handshake" UDP packet containing the string `BNMV_DISCOVER` to your specified `ip` and `port`.
4. Your app should then start sending audio data to BNMV's IP on port `8889`. (Note: You can get BNMV's IP address from the source address of the handshake packet).

#### Audio Data Format:
BNMV expects **512 frequency magnitudes** packed into a **768-byte** UDP packet, sent approximately 60 times per second.

Each magnitude should be an integer between `0` and `4095`.
Two 12-bit magnitudes (v1, v2) are packed into 3 bytes:
* Byte 0: `v1 & 0xFF` (Low 8 bits of v1)
* Byte 1: `((v1 >> 8) & 0x0F) | ((v2 << 4) & 0xF0)` (High 4 bits of v1 and Low 4 bits of v2)
* Byte 2: `(v2 >> 4) & 0xFF` (High 8 bits of v2)

Total packet size = (512 / 2) * 3 = 768 bytes.
