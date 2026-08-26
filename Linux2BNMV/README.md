# Connecting BNMV to Your Linux PC

This guide walks you through streaming your PC's audio to **Better Nothing
Music Visualizer (BNMV)** on your phone, so the visualizer reacts to
whatever's playing on your computer instead of your phone's mic or local
music.

You'll need two things from your PC setup: `run-bnmv-stream.sh` and
`bnmv_stream.py`, kept in the same folder.

---

## What you need

- **PC**: Linux with PulseAudio or PipeWire, Python 3, and the
  `pulseaudio-utils` package (provides `pactl` and `parec`).
- **Phone**: BNMV installed, connected to **the same Wi-Fi network** as your
  PC.
- A way to send BNMV a one-time "connect" command — see **Step 3** below.

### Distro-specific dependencies

| Distro | Install command |
|---|---|
| **Debian / Ubuntu / KDE Neon** | `sudo apt install pulseaudio-utils python3-venv` |
| **Arch Linux** | `sudo pacman -S python python-pipewire` (or `python-pulseaudio` if you use PulseAudio) |
| **Fedora** | `sudo dnf install pulseaudio-utils python3` |

> **Note:** On Arch Linux and other distros that mark their Python as
> [externally managed (PEP 668)](https://peps.python.org/pep-0668/),
> `pip install --user` will fail. The script handles this automatically by
> creating a local virtual environment (`.venv/`). If you prefer to set it up
> manually first:
> ```bash
> python -m venv .venv
> source .venv/bin/activate.fish   # or: source .venv/bin/activate (bash)
> pip install numpy
> ```

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

The first run will install the `numpy` Python package automatically if it's
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

---

## Step 3: Connect from BNMV

Open BNMV on your phone and use the **External Audio / Another device or
app...** option to connect to your PC using its LAN IP and port `8888`.

On the PC, the terminal should show a handshake line and keep running
silently after that (that's normal — it's streaming continuously).

On the phone, confirm the visualizer is reacting to your PC's audio.

---

## Step 4: Open your firewall (if needed)

If your PC has a firewall enabled, you need to allow BNMV's UDP ports.

**ufw** (Debian / Ubuntu / Arch):
```bash
sudo ufw allow 8888:8891/udp
```

**firewalld** (Fedora / RHEL):
```bash
sudo firewall-cmd --add-port=8888-8891/udp --permanent
sudo firewall-cmd --reload
```

---

## Troubleshooting

**Nothing happens after connecting from BNMV**
- Double-check the PC's LAN IP hasn't changed (Wi-Fi/router restarts can
  reassign it) — re-check the IP printed by `run-bnmv-stream.sh`.
- Confirm phone and PC are genuinely on the same network/subnet.
- Make sure your firewall allows UDP ports `8888`–`8891` (see Step 4).

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
- You'll need to repeat Step 3 (reconnect from BNMV), since
  BNMV needs to be told the PC's current IP each time it changes.

---

That's it — once connected, just leave `run-bnmv-stream.sh` running in the
background on your PC whenever you want your phone's visualizer synced to
your computer's audio.
