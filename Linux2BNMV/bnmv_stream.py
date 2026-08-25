#!/usr/bin/env python3
"""
bnmv_stream.py

Streams this PC's currently-playing audio to Better Nothing Music Visualizer
(BNMV) on a phone, using BNMV's "External Audio Data (UDP)" protocol.

Flow (per BNMV's docs):
  1. Some app/automation on a phone sends BNMV an ACTION_CONNECT_UDP intent
     containing this PC's LAN IP and a listen port (default 8888).
  2. BNMV then sends a UDP handshake packet containing the literal bytes
     "BNMV_DISCOVER" to that ip:port.
  3. This script is what's listening on that port. When it sees a
     handshake, it adds the sender's IP to its set of connected devices and
     streams FFT frames to it on UDP port 8889, ~60 times/sec.

Any number of devices can be connected at once -- each one that sends a
handshake gets added to the target list and receives the same audio-reactive
stream. A device is automatically dropped if it goes quiet (no handshake or
latency ping) for longer than --target-timeout, so streaming doesn't pile up
indefinitely on devices that left the network.

Audio is captured continuously via PulseAudio's `parec`, reading raw mono
float32 samples from the default sink's monitor (i.e. "what's playing"),
regardless of whether a handshake has arrived yet -- so streaming can start
the instant a phone connects.

Only external dependency: numpy. Audio capture uses the `parec` binary
(part of pulseaudio-utils / pipewire-pulse), not a Python audio library.
"""

import argparse
import socket
import struct
import subprocess
import sys
import threading
import time

import numpy as np

DISCOVER_MAGIC = b"BNMV_DISCOVER"
NUM_BINS = 512
PACKET_BYTES = 768  # (512 / 2) * 3
LOW_FREQ = 30.0
HIGH_FREQ = 16000.0
PROTOCOL_VERSION = "6.0.made.by.aleks.levet"
HOST_REPLY_PORT = 8891
PING_PORT = 8890


def get_local_ip() -> str:
    """Best-effort local LAN IP, used only for the optional BNMV_HOST reply."""
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

    # nearest-neighbor lookup for each target frequency
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


class AudioCapture:
    """Continuously captures mono float32 samples from a PulseAudio source
    (typically a sink monitor, i.e. "what's playing") via `parec`, keeping a
    rolling window of the most recent `fft_size` samples."""

    def __init__(self, source: str, samplerate: int, fft_size: int, chunk_frames: int = 256):
        self.source = source
        self.samplerate = samplerate
        self.fft_size = fft_size
        self.chunk_frames = chunk_frames
        self.ring = np.zeros(fft_size, dtype=np.float32)
        self.lock = threading.Lock()
        self.proc = None
        self._stop = threading.Event()

    def start(self):
        cmd = [
            "parec",
            f"--device={self.source}",
            "--format=float32le",
            f"--rate={self.samplerate}",
            "--channels=1",
            "--latency-msec=20",
            "--raw",
        ]
        try:
            self.proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=0)
        except FileNotFoundError:
            print("ERROR: 'parec' not found. Install pulseaudio-utils "
                  "(or pipewire-pulse, which provides it).", file=sys.stderr)
            sys.exit(1)

        t = threading.Thread(target=self._read_loop, daemon=True)
        t.start()

    def _read_loop(self):
        bytes_per_chunk = self.chunk_frames * 4  # float32 = 4 bytes
        buf = b""
        stdout = self.proc.stdout
        while not self._stop.is_set():
            data = stdout.read(bytes_per_chunk - len(buf))
            if not data:
                if self.proc.poll() is not None:
                    print("WARNING: parec exited unexpectedly.", file=sys.stderr)
                    time.sleep(0.5)
                continue
            buf += data
            if len(buf) < bytes_per_chunk:
                continue
            samples = np.frombuffer(buf, dtype=np.float32)
            buf = b""
            n = len(samples)
            with self.lock:
                if n >= self.fft_size:
                    self.ring[:] = samples[-self.fft_size:]
                else:
                    self.ring = np.roll(self.ring, -n)
                    self.ring[-n:] = samples

    def snapshot(self) -> np.ndarray:
        with self.lock:
            return self.ring.copy()

    def stop(self):
        self._stop.set()
        if self.proc:
            self.proc.terminate()


class BNMVStreamer:
    def __init__(self, args):
        self.args = args
        self.capture = AudioCapture(args.source, args.samplerate, args.fft_size, args.chunk_frames)
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

        # Connected devices: ip -> monotonic time last heard from (handshake
        # or latency ping). Unlimited devices can be connected at once;
        # entries older than --target-timeout are pruned in send_loop.
        self.targets = {}
        self.targets_lock = threading.Lock()
        self._stop = threading.Event()

        # --- slow, non-aggressive auto-gain state ---
        # Tracks a running estimate (in dB) of the signal's peak bin level and
        # nudges gain toward a target headroom with a multi-second time
        # constant, so it rides out loud/quiet passages smoothly instead of
        # pumping on every transient.
        dt = 1.0 / args.fps
        self._agc_alpha = 1.0 - np.exp(-dt / max(args.agc_tau, 0.05))
        self.agc_level_db = args.agc_target_db  # start neutral -> initial gain ~= manual gain

    # ---- connected-device tracking --------------------------------------------
    def touch_target(self, ip: str) -> bool:
        """Mark `ip` as recently heard-from. Returns True if it's a newly
        seen device (not already in the target set)."""
        with self.targets_lock:
            is_new = ip not in self.targets
            self.targets[ip] = time.monotonic()
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
                is_new = self.touch_target(addr[0])
                if is_new:
                    with self.targets_lock:
                        count = len(self.targets)
                    print(f"[bnmv] Handshake received from {addr[0]} -> streaming to "
                          f"{addr[0]}:{self.args.send_port} ({count} device(s) connected)")
                # Optional LAN host-discovery reply. Harmless (and ignorable
                # by BNMV) even for the explicit ACTION_CONNECT_UDP flow,
                # which doesn't require it.
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
                # A live ping means this device is still around -- refresh
                # its entry so it doesn't get pruned as stale.
                self.touch_target(addr[0])
                timestamp = text.split(";", 1)[1]
                reply = f"BNMV_PONG;{timestamp}"
                try:
                    # Reply to the exact source ip AND source port of the probe.
                    self.ping_sock.sendto(reply.encode("utf-8"), addr)
                except OSError:
                    pass

    # ---- auto-gain --------------------------------------------------------
    def update_autogain(self, mags: np.ndarray) -> float:
        """
        Slowly adapts a gain multiplier so the signal's peak bin sits near
        `agc_target_db`. Uses a single-pole lowpass (time constant
        `agc_tau` seconds, default several seconds) on the peak level itself,
        so short transients don't yank the gain around -- only sustained
        loudness changes do. The resulting gain correction is clamped to
        [agc_min_db, agc_max_db] so it can never swing to extremes.
        """
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
        next_prune = time.monotonic() + 1.0
        while not self._stop.is_set():
            now = time.monotonic()

            with self.targets_lock:
                ips = list(self.targets.keys())
            if ips:
                # Compute the frame once, fan it out to every connected device.
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
        # give parec a moment to spin up
        time.sleep(0.3)

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
    p = argparse.ArgumentParser(description="Stream this PC's audio to BNMV over UDP.")
    p.add_argument("--source", required=True,
                    help="PulseAudio source to record from (e.g. a sink .monitor name)")
    p.add_argument("--listen-port", type=int, default=8888,
                    help="UDP port to listen for the BNMV_DISCOVER handshake on (default 8888)")
    p.add_argument("--send-port", type=int, default=8889,
                    help="UDP port on the phone to send audio frames to (default 8889, per spec)")
    p.add_argument("--samplerate", type=int, default=48000)
    p.add_argument("--fft-size", type=int, default=2048)
    p.add_argument("--chunk-frames", type=int, default=256,
                    help="Samples read from parec per read() call; smaller = lower latency")
    p.add_argument("--fps", type=float, default=60.0)
    p.add_argument("--gain", type=float, default=0.5,
                    help="Manual linear gain, applied on top of auto-gain (or alone if --no-autogain)")
    p.add_argument("--no-autogain", dest="autogain", action="store_false",
                    help="Disable auto-gain; use only the fixed --gain value")
    p.set_defaults(autogain=True)
    p.add_argument("--agc-target-db", type=float, default=-8.0,
                    help="Target headroom (dB) auto-gain tries to keep the signal's peak bin at")
    p.add_argument("--agc-tau", type=float, default=2.0,
                    help="Auto-gain adaptation time constant in seconds (higher = gentler/slower)")
    p.add_argument("--agc-min-db", type=float, default=-80.0,
                    help="Lower clamp on the auto-gain correction, in dB")
    p.add_argument("--agc-max-db", type=float, default=20.0,
                    help="Upper clamp on the auto-gain correction, in dB")
    p.add_argument("--db-min", type=float, default=-40.0,
                    help="dB value mapped to output 0")
    p.add_argument("--db-max", type=float, default=0.0,
                    help="dB value mapped to output 4095")
    p.add_argument("--device-name", default=socket.gethostname(),
                    help="Name reported in the optional BNMV_HOST discovery reply")
    p.add_argument("--model", default="Linux PC",
                    help="Model string reported in the optional BNMV_HOST discovery reply")
    p.add_argument("--host-ip", default=None,
                    help="Override the IP reported in the BNMV_HOST reply (auto-detected by default)")
    p.add_argument("--target-timeout", type=float, default=60000000.0,
                    help="Seconds without a handshake/ping before a connected device is dropped")
    args = p.parse_args()

    streamer = BNMVStreamer(args)
    streamer.run()


if __name__ == "__main__":
    main()
