# Connecting BNMV to Your Windows PC

This guide walks you through streaming your Windows PC's audio to
**Better Nothing Music Visualizer (BNMV)** on your phone, so the visualizer
reacts to whatever's playing on your computer instead of your phone's mic or
local music.

You'll need two things from this folder: `run-bnmv-stream.bat` and
`bnmv_stream_windows.py`, kept in the same folder.

> Audio capture uses WASAPI loopback (via the `soundcard` package). This
> captures your chosen output device directly — you do **not** need to
> enable "Stereo Mix" or install a virtual audio cable.

---

## What you need

- **PC**: Windows 10 or 11, with [Python 3.10+](https://www.python.org/downloads/)
  installed (check **"Add python.exe to PATH"**).
- **Phone**: BNMV installed, connected to **the same Wi-Fi network** as your
  PC.

---

## Step 1: Set up the scripts on your PC

You have two ways to run this: a small **GUI window** (recommended for
most people) or the **console** version (more tuning flags available).

1. Put `run-bnmv-stream.bat` and `bnmv_stream_windows.py` in the same
   folder.
2. Double-click `run-bnmv-stream.bat` (or run it from a command prompt).

The first run will install `numpy` and `soundcard` automatically. Once
running, you'll see something like:

```
==================================================================
 BNMV PC audio streamer (Windows)
 Capturing from      : Speakers (Realtek High Definition Audio)
 This PC's LAN IP    : 192.168.1.42
 Listen port         : 8888  (override with --listen-port)
 Connect from BNMV using this IP and port, or point an
 ACTION_CONNECT_UDP intent at it.
==================================================================
[bnmv] Listening for handshake on UDP :8888 ...
[bnmv] Listening for latency pings on UDP :8890 ...
```

**Capturing a different output device?** List available devices, then pass
the exact name:
```bat
run-bnmv-stream.bat --list-devices
run-bnmv-stream.bat --device "Headphones (Realtek High Definition Audio)"
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
app...** option to connect to your PC using its LAN IP and port `8888`
(both shown in Step 1's output).

The console window should print a handshake line and keep running quietly
after that — that's normal, it's streaming continuously. Multiple phones
can connect at the same time; each one that connects gets added to the
console log.

---

## Step 4: Allow it through Windows Firewall (if prompted)

The first time you run it, Windows may show a firewall prompt for Python —
click **Allow access** (for at least "Private networks").

If you don't get a prompt, or blocked it by mistake, add the rule manually:

1. Open **Windows Defender Firewall with Advanced Security**.
2. **Inbound Rules → New Rule… → Port → UDP → Specific local ports:**
   `8888-8891`
3. **Allow the connection**, apply to **Private** networks, name it
   `BNMV Streamer`.

---

## Troubleshooting

**Nothing happens after connecting from BNMV**
- Double-check the PC's LAN IP hasn't changed (Wi-Fi/router restarts can
  reassign it) — re-check the IP printed at startup.
- Confirm phone and PC are genuinely on the same network/subnet.
- Make sure Windows Firewall allows UDP ports `8888`–`8891` (see Step 4).

**"could not find output device" / "could not open loopback capture"**
- Run `run-bnmv-stream.bat --list-devices` and copy the device name
  *exactly* (including anything in parentheses) into `--device "..."`.
- Make sure the device isn't disabled in **Settings → Sound**.

**Visualizer connects but shows no audio movement**
- Make sure audio is actually playing through the device you're capturing
  from. If you normally listen through headphones but the script is
  capturing your speakers (or vice versa), pass `--device` to match, or
  set your default playback device to match what you want captured.
- Check the device isn't muted in the Windows volume mixer.

**The visualizer looks too quiet or too loud**
- Auto-gain adapts over a few seconds, so give it a moment after starting
  playback. If it's still off, tune it directly:
  ```bat
  run-bnmv-stream.bat --agc-target-db -15
  ```

**I switched Wi-Fi networks or restarted my PC**
- You'll need to repeat Step 3 (reconnect from BNMV), since BNMV needs to
  be told the PC's current IP each time it changes.

---

## Packaging as a single .exe (optional)

If you'd rather hand someone one `.exe` instead of a Python script + batch
file, use [PyInstaller](https://pyinstaller.org/):

```bat
python -m pip install pyinstaller numpy soundcard
python -m PyInstaller --onefile --console --name bnmv-stream bnmv_stream_windows.py
```

This produces `dist\bnmv-stream.exe` — a single, self-contained executable
that runs on a machine without Python installed at all. Run it the same way
you'd run the Python script, e.g.:

```bat
dist\bnmv-stream.exe --list-devices
dist\bnmv-stream.exe --gain 0.5
```

Notes:
- Build it *on Windows* — PyInstaller doesn't cross-compile from Linux/Mac
  to Windows.
- Windows Defender / SmartScreen may flag a freshly-built, unsigned
  `.exe` on first run ("Windows protected your PC"). This is normal for
  unsigned executables; click **More info → Run anyway**. Code-signing the
  exe avoids this but requires a paid certificate.
- Keep `--console` so you still see the connection log; drop it for a
  windowless background process once you've confirmed everything works.

---

That's it — once connected, just leave `run-bnmv-stream.bat` (or the
packaged `.exe`) running in the background whenever you want your phone's
visualizer synced to your computer's audio.
