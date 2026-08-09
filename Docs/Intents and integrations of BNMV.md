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