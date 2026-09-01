#!/usr/bin/env python3
"""
bnmv_stream_windows.py

Streams this Windows PC's currently-playing audio to Better Nothing Music
Visualizer (BNMV) on a phone, using BNMV's "External Audio Data (UDP)"
protocol.

Flow (per BNMV's docs):
  1. connect directly from BNMV's UI using this PC's IP and that port.
  2. BNMV then sends a UDP handshake packet containing the literal bytes
     "BNMV_DISCOVER" to that ip:port.
  3. This script is what's listening on that port. When it sees a
     handshake, it adds the sender's IP to its set of connected devices and
     streams FFT frames to it on UDP port 8889, ~60 times/sec.

Any number of devices can be connected at once. Since BNMV only sends the
handshake once, at connection time (not periodically), devices are never
automatically dropped -- once connected, a device stays in the target list
until this script is restarted.

Audio capture on Windows uses WASAPI loopback via the `soundcard` package
(no separate "Stereo Mix" device needed -- it captures whatever the chosen
output device is playing, directly). Dependencies: numpy, soundcard.
"""

import argparse
import socket
import sys
import threading
import time

import numpy as np
import soundcard as sc

DISCOVER_MAGIC = b"BNMV_DISCOVER"
NUM_BINS = 512
PACKET_BYTES = 768  # (512 / 2) * 3
LOW_FREQ = 30.0
HIGH_FREQ = 16000.0
PROTOCOL_VERSION = "6.0.made.by.aleks.levet"
HOST_REPLY_PORT = 8891
PING_PORT = 8890


def get_local_ip() -> str:
    """Best-effort local LAN IP, used for on-screen info and the optional
    BNMV_HOST reply."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("1.1.1.1", 80))
        ip = s.getsockname()[0]
    except OSError:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip


def build_bin_mapping(fft_size: int, samplerate: int) -> np.ndarray:
    """
    Precompute, for each of the 512 log-spaced output bins, the index into
    the rfft output array whose frequency is closest to that bin's center
    frequency:
        fCenter(i) = 30 * (16000/30) ^ ((i + 0.5) / 512)
    """
    i = np.arange(NUM_BINS)
    f_center = LOW_FREQ * (HIGH_FREQ / LOW_FREQ) ** ((i + 0.5) / NUM_BINS)

    rfft_freqs = np.fft.rfftfreq(fft_size, d=1.0 / samplerate)

    idx = np.searchsorted(rfft_freqs, f_center)
    idx = np.clip(idx, 1, len(rfft_freqs) - 1)
    left = rfft_freqs[idx - 1]
    right = rfft_freqs[idx]
    choose_left = (f_center - left) < (right - f_center)
    idx = np.where(choose_left, idx - 1, idx)
    return idx.astype(np.int64)


def pack_frame(vals: np.ndarray) -> bytes:
    """
    Pack 512 12-bit magnitudes (0-4095) into a 768-byte buffer per BNMV spec:
      Byte0 = v1 & 0xFF
      Byte1 = ((v1>>8)&0x0F) | ((v2<<4)&0xF0)
      Byte2 = (v2>>4)&0xFF
    """
    v = vals.astype(np.uint16)
    v1 = v[0::2]
    v2 = v[1::2]

    out = np.empty(PACKET_BYTES, dtype=np.uint8)
    out[0::3] = (v1 & 0xFF).astype(np.uint8)
    out[1::3] = (((v1 >> 8) & 0x0F) | ((v2 << 4) & 0xF0)).astype(np.uint8)
    out[2::3] = ((v2 >> 4) & 0xFF).astype(np.uint8)
    return out.tobytes()


def list_output_devices():
    print("Available output devices (use the exact name with --device):\n")
    default = sc.default_speaker()
    for spk in sc.all_speakers():
        marker = "  (default)" if spk.name == default.name else ""
        print(f"  - {spk.name}{marker}")


class AudioCapture:
    """Continuously captures mono float32 samples via WASAPI loopback on a
    chosen (or default) output device, keeping a rolling window of the most
    recent `fft_size` samples."""

    def __init__(self, device_name: str, samplerate: int, fft_size: int, chunk_frames: int = 256):
        self.device_name = device_name
        self.samplerate = samplerate
        self.fft_size = fft_size
        self.chunk_frames = chunk_frames
        self.ring = np.zeros(fft_size, dtype=np.float32)
        self.lock = threading.Lock()
        self._stop = threading.Event()
        self.mic = None
        self.speaker_label = None

    def start(self):
        try:
            speaker = sc.get_speaker(self.device_name) if self.device_name else sc.default_speaker()
        except Exception as e:
            print(f"ERROR: could not find output device: {e}", file=sys.stderr)
            print("Run with --list-devices to see available device names.", file=sys.stderr)
            sys.exit(1)

        self.speaker_label = speaker.name

        try:
            self.mic = sc.get_microphone(id=str(speaker.name), include_loopback=True)
        except Exception as e:
            print(f"ERROR: could not open loopback capture for '{speaker.name}': {e}", file=sys.stderr)
            sys.exit(1)

        t = threading.Thread(target=self._read_loop, daemon=True)
        t.start()

    def _read_loop(self):
        try:
            with self.mic.recorder(samplerate=self.samplerate, blocksize=self.chunk_frames) as rec:
                while not self._stop.is_set():
                    data = rec.record(numframes=self.chunk_frames)  # (frames, channels)
                    mono = data.mean(axis=1) if data.ndim > 1 else data
                    n = len(mono)
                    with self.lock:
                        if n >= self.fft_size:
                            self.ring[:] = mono[-self.fft_size:]
                        else:
                            self.ring = np.roll(self.ring, -n)
                            self.ring[-n:] = mono
        except Exception as e:
            print(f"ERROR: audio capture failed: {e}", file=sys.stderr)

    def snapshot(self) -> np.ndarray:
        with self.lock:
            return self.ring.copy()

    def stop(self):
        self._stop.set()


class BNMVStreamer:
    def __init__(self, args):
        self.args = args
        self.capture = AudioCapture(args.device, args.samplerate, args.fft_size, args.chunk_frames)
        self.bin_idx = build_bin_mapping(args.fft_size, args.samplerate)
        self.window = np.hanning(args.fft_size).astype(np.float32)

        self.send_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.listen_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.listen_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.listen_sock.bind(("0.0.0.0", args.listen_port))

        self.ping_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.ping_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.ping_sock.bind(("0.0.0.0", PING_PORT))

        self.host_ip = args.host_ip or get_local_ip()

        # Connected devices: a set of IPs that have sent a handshake.
        # Unlimited devices can be connected at once. BNMV only sends the
        # handshake once at connection time, so there's no "last seen" to
        # expire on -- devices stay connected until this script restarts.
        self.targets = set()
        self.targets_lock = threading.Lock()
        self._stop = threading.Event()

        # --- slow, non-aggressive auto-gain state ---
        dt = 1.0 / args.fps
        self._agc_alpha = 1.0 - np.exp(-dt / max(args.agc_tau, 0.05))
        self.agc_level_db = args.agc_target_db  # start neutral -> initial gain ~= manual gain

    # ---- connected-device tracking --------------------------------------------
    def add_target(self, ip: str) -> bool:
        """Add `ip` to the connected-device set. Returns True if it's a
        newly seen device (not already connected)."""
        with self.targets_lock:
            is_new = ip not in self.targets
            self.targets.add(ip)
        return is_new

    # ---- discovery listener -------------------------------------------------
    def listen_loop(self):
        print(f"[bnmv] Listening for handshake on UDP :{self.args.listen_port} ...")
        while not self._stop.is_set():
            try:
                data, addr = self.listen_sock.recvfrom(2048)
            except OSError:
                break
            if data.startswith(DISCOVER_MAGIC):
                is_new = self.add_target(addr[0])
                if is_new:
                    with self.targets_lock:
                        count = len(self.targets)
                    print(f"[bnmv] Handshake received from {addr[0]} -> streaming to "
                          f"{addr[0]}:{self.args.send_port} ({count} device(s) connected)")
                self.send_host_reply(addr[0])

    def send_host_reply(self, ip: str):
        msg = (
            f"BNMV_HOST;{self.args.device_name};{self.args.model};"
            f"{self.host_ip};{self.args.send_port};{PROTOCOL_VERSION}"
        )
        try:
            self.send_sock.sendto(msg.encode("utf-8"), (ip, HOST_REPLY_PORT))
        except OSError:
            pass

    # ---- latency ping/pong ----------------------------------------------------
    def ping_loop(self):
        print(f"[bnmv] Listening for latency pings on UDP :{PING_PORT} ...")
        while not self._stop.is_set():
            try:
                data, addr = self.ping_sock.recvfrom(1024)
            except OSError:
                break
            try:
                text = data.decode("utf-8", errors="ignore")
            except Exception:
                continue
            if text.startswith("BNMV_PING;"):
                timestamp = text.split(";", 1)[1]
                reply = f"BNMV_PONG;{timestamp}"
                try:
                    self.ping_sock.sendto(reply.encode("utf-8"), addr)
                except OSError:
                    pass

    # ---- auto-gain --------------------------------------------------------
    def update_autogain(self, mags: np.ndarray) -> float:
        peak = float(np.max(mags)) if mags.size else 0.0
        peak_db = 20.0 * np.log10(peak + 1e-9)

        self.agc_level_db += self._agc_alpha * (peak_db - self.agc_level_db)

        gain_db = self.args.agc_target_db - self.agc_level_db
        gain_db = float(np.clip(gain_db, self.args.agc_min_db, self.args.agc_max_db))
        return self.args.gain * (10.0 ** (gain_db / 20.0))

    # ---- FFT -> packet computation -------------------------------------------
    def compute_frame_bytes(self) -> bytes:
        samples = self.capture.snapshot()
        spec = np.abs(np.fft.rfft(samples * self.window))
        mags = spec[self.bin_idx]

        gain = self.update_autogain(mags) if self.args.autogain else self.args.gain

        db = 20.0 * np.log10(mags * gain + 1e-9)
        db = np.clip(db, self.args.db_min, self.args.db_max)
        norm = (db - self.args.db_min) / (self.args.db_max - self.args.db_min)
        vals = np.round(norm * 4095.0).astype(np.uint16)
        return pack_frame(vals)

    # ---- 60fps sender ---------------------------------------------------------
    def send_loop(self):
        period = 1.0 / self.args.fps
        next_t = time.monotonic()
        while not self._stop.is_set():
            with self.targets_lock:
                ips = list(self.targets)
            if ips:
                packet = self.compute_frame_bytes()
                for ip in ips:
                    try:
                        self.send_sock.sendto(packet, (ip, self.args.send_port))
                    except OSError:
                        pass
            next_t += period
            sleep_for = next_t - time.monotonic()
            if sleep_for > 0:
                time.sleep(sleep_for)
            else:
                next_t = time.monotonic()

    def run(self):
        self.capture.start()
        time.sleep(0.3)

        device_label = self.capture.speaker_label or self.args.device or "(default output)"
        print("==================================================================")
        print(" BNMV PC audio streamer (Windows)")
        print(f" Capturing from      : {device_label}")
        print(f" This PC's LAN IP    : {self.host_ip}")
        print(f" Listen port         : {self.args.listen_port}  (override with --listen-port)")
        print(" Connect from BNMV using this IP and port, or point an")
        print(" ACTION_CONNECT_UDP intent at it.")
        print("==================================================================")

        listener = threading.Thread(target=self.listen_loop, daemon=True)
        listener.start()

        pinger = threading.Thread(target=self.ping_loop, daemon=True)
        pinger.start()

        try:
            self.send_loop()
        except KeyboardInterrupt:
            pass
        finally:
            self._stop.set()
            self.capture.stop()
            self.listen_sock.close()
            self.ping_sock.close()


def main():
    p = argparse.ArgumentParser(description="Stream this Windows PC's audio to BNMV over UDP.")
    p.add_argument("--device", default=None,
                    help="Exact name of the output device to capture (see --list-devices). "
                         "Defaults to the current default playback device.")
    p.add_argument("--list-devices", action="store_true",
                    help="List available output devices and exit.")
    p.add_argument("--listen-port", type=int, default=8888,
                    help="UDP port to listen for the BNMV_DISCOVER handshake on (default 8888)")
    p.add_argument("--send-port", type=int, default=8889,
                    help="UDP port on the phone to send audio frames to (default 8889, per spec)")
    p.add_argument("--samplerate", type=int, default=48000)
    p.add_argument("--fft-size", type=int, default=2048)
    p.add_argument("--chunk-frames", type=int, default=256,
                    help="Samples read per capture callback; smaller = lower latency")
    p.add_argument("--fps", type=float, default=60.0)
    p.add_argument("--gain", type=float, default=0.5,
                    help="Manual linear gain, applied on top of auto-gain (or alone if --no-autogain)")
    p.add_argument("--no-autogain", dest="autogain", action="store_false",
                    help="Disable auto-gain; use only the fixed --gain value")
    p.set_defaults(autogain=True)
    p.add_argument("--agc-target-db", type=float, default=-8.0,
                    help="Target headroom (dB) auto-gain tries to keep the signal's peak bin at")
    p.add_argument("--agc-tau", type=float, default=4.0,
                    help="Auto-gain adaptation time constant in seconds (higher = gentler/slower)")
    p.add_argument("--agc-min-db", type=float, default=-50.0,
                    help="Lower clamp on the auto-gain correction, in dB")
    p.add_argument("--agc-max-db", type=float, default=20.0,
                    help="Upper clamp on the auto-gain correction, in dB")
    p.add_argument("--db-min", type=float, default=-40.0,
                    help="dB value mapped to output 0")
    p.add_argument("--db-max", type=float, default=0.0,
                    help="dB value mapped to output 4095")
    p.add_argument("--device-name", default=socket.gethostname(),
                    help="Name reported in the optional BNMV_HOST discovery reply")
    p.add_argument("--model", default="Windows PC",
                    help="Model string reported in the optional BNMV_HOST discovery reply")
    p.add_argument("--host-ip", default=None,
                    help="Override the IP reported in the BNMV_HOST reply (auto-detected by default)")
    args = p.parse_args()

    if args.list_devices:
        list_output_devices()
        return

    streamer = BNMVStreamer(args)
    streamer.run()


if __name__ == "__main__":
    main()
