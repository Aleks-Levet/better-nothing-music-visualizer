# Connecting BNMV to Your Linux PC

This guide walks you through streaming your PC's audio to **Better Nothing
Music Visualizer (BNMV)** on your phone, so the visualizer reacts to
whatever's playing on your computer instead of your phone's mic or local
music.

You'll need two things from your PC setup: `run-bnmv-stream.sh` and
`bnmv_stream.py`, kept in the same folder.

---

## What you need

- **PC**: Linux with PulseAudio or PipeWire (KDE Neon has this by default),
  Python 3, and the `pulseaudio-utils` package (provides `pactl` and
  `parec`).
- **Phone**: BNMV installed, connected to **the same Wi-Fi network** as your
  PC.
- A way to send BNMV a one-time "connect" command — see **Step 3** below for
  two easy options.

---

## Step 1: Set up the scripts on your PC

1. Put `run-bnmv-stream.sh` and `bnmv_stream.py` in the same folder.
2. Make the launcher executable:
   ```bash
   chmod +x run-bnmv-stream.sh
   ```
3. Run it:
   ```bash
   ./run-bnmv-stream.sh
   ```

The first run may install the `numpy` Python package automatically if it's
missing. Once running, you'll see something like:

```
==================================================================
 BNMV PC audio streamer
 Default sink monitor : alsa_output.pci-0000_00_1f.3.analog-stereo.monitor
 This PC's LAN IP     : 192.168.1.42
 Listen port          : 8888  (override with --listen-port)
 Point BNMV's ACTION_CONNECT_UDP intent at that ip:port from your phone.
==================================================================
[bnmv] Listening for handshake on UDP :8888 ...
[bnmv] Listening for latency pings on UDP :8890 ...
```


---

## Step 2: Make sure your phone is on the same network

BNMV and the PC talk over your local Wi-Fi. Connect your phone to the same
Wi-Fi network as your PC. This won't work over mobile data, and it usually
won't work on "guest" Wi-Fi networks, since those often block devices from
talking to each other (client isolation).


## Step 3: Confirm it's working

- On the client device, connect to the linux pc with the `Another device or app...` audio source.
- On the PC, the terminal should show a handshake line and keep running
  silently after that (that's normal — it's streaming continuously).
- On the phone, open BNMV and confirm the visualizer is reacting to your
  PC's audio. If BNMV doesn't automatically switch to the network source,
  check its settings for a source selector and choose the network/external
  option.
- Play something on your PC and watch the visualizer respond.

---

## Troubleshooting

**Nothing happens after sending the connect command**
- Double-check the PC's LAN IP hasn't changed (Wi-Fi/router restarts can
  reassign it) — re-check the IP printed by `run-bnmv-stream.sh`.
- Confirm phone and PC are genuinely on the same network/subnet.
- Check your PC's firewall isn't blocking UDP ports `8888`–`8891`:
  ```bash
  sudo ufw allow 8888:8891/udp
  ```

**Visualizer connects but shows no audio movement**
- Make sure audio is actually playing on the PC through the **default**
  output device — `run-bnmv-stream.sh` captures whatever your default sink
  is playing. If you're outputting to a different device (e.g. HDMI,
  headphones via a different sink), switch your PC's default output to
  match, or restart the script after switching.
- Check the PC isn't muted at the system mixer level.

**The visualizer looks too quiet or too loud**
- The streamer includes auto-gain that adapts over a few seconds, so give
  it a moment after starting playback. If it's still off, `run-bnmv-stream.sh`
  accepts tuning flags, e.g.:
  ```bash
  ./run-bnmv-stream.sh --agc-target-db -15
  ```

**I switched Wi-Fi networks or restarted my PC**
- You'll need to repeat Step 3 (send the connect command again), since
  BNMV needs to be told the PC's current IP each time it changes.

---

That's itw once connected, just leave `run-bnmv-stream.sh` running in the
background on your PC whenever you want your phone's visualizer synced to
your computer's audio.
