#!/usr/bin/env bash
#
# run-bnmv-stream.sh
#
# Streams this PC's playing audio to BNMV (Better Nothing Music Visualizer)
# on your phone, using BNMV's External Audio Data (UDP) protocol.
#
# Usage:
#   ./run-bnmv-stream.sh [--gain N] [--db-min N] [--db-max N] [--listen-port N]
#
# Setup on the phone side (one-time, done by whatever app/automation you use
# to trigger BNMV, e.g. Tasker or your own app):
#   Send BNMV the intent com.better.nothing.music.vizualizer.ACTION_CONNECT_UDP
#   with extras:
#     ip   = <this PC's LAN IP, printed below when you run this script>
#     port = 8888   (or whatever --listen-port you pass)
#
# Once that intent fires, BNMV sends a handshake to this PC, and this script
# starts streaming audio to your phone automatically. No further action
# needed on the PC side -- just leave this running.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY_SCRIPT="$SCRIPT_DIR/bnmv_stream.py"

# ---------------------------------------------------------------------------
# 0. Banner
# ---------------------------------------------------------------------------
print_banner() {
    local c="" r=""
    if [ -t 1 ] && command -v tput >/dev/null 2>&1; then
        c="$(tput setaf 6)$(tput bold)"
        r="$(tput sgr0)"
    fi
    printf '%s' "$c"
    cat << "BANNER"
+--------------------------------------+
| ######   #     #  #     #  #     #   |
| #     #  ##    #  ##   ##  #     #   |
| #     #  # #   #  # # # #  #     #   |
| ######   #  #  #  #  #  #   #   #    |
| #     #  #   # #  #     #   #   #    |
| #     #  #    ##  #     #    # #     |
| ######   #     #  #     #     #      |
|                                      |
|      PC -> Phone Audio Streamer      |
|         (Linux / PulseAudio)         |
+--------------------------------------+
BANNER
    printf '%s\n' "$r"
}

print_banner

# ---------------------------------------------------------------------------
# 1. Dependency checks
# ---------------------------------------------------------------------------
missing=()

command -v python3 >/dev/null 2>&1 || missing+=("python3")
command -v pactl    >/dev/null 2>&1 || missing+=("pulseaudio-utils (for pactl)")
command -v parec    >/dev/null 2>&1 || missing+=("pulseaudio-utils / pipewire-pulse (for parec)")

if [ ${#missing[@]} -ne 0 ]; then
    echo "Missing required tools:" >&2
    printf '  - %s\n' "${missing[@]}" >&2
    echo "On KDE Neon: sudo apt install pulseaudio-utils" >&2
    exit 1
fi

if ! python3 -c "import numpy" >/dev/null 2>&1; then
    echo "Python 'numpy' not found. Installing for your user..."
    pip3 install --user numpy
fi

# ---------------------------------------------------------------------------
# 2. Find the monitor source for the default sink (i.e. "what's playing")
# ---------------------------------------------------------------------------
DEFAULT_SINK="$(pactl get-default-sink)"
MONITOR_SOURCE="${DEFAULT_SINK}.monitor"

if ! pactl list short sources | grep -q "^[0-9]*[[:space:]]*${MONITOR_SOURCE}[[:space:]]"; then
    echo "WARNING: could not confirm monitor source '${MONITOR_SOURCE}' exists." >&2
    echo "Available sources:" >&2
    pactl list short sources >&2
    echo "Pass a specific one with: $0 --source <name>" >&2
fi

# ---------------------------------------------------------------------------
# 3. Figure out and print this machine's LAN IP, for the phone-side config
# ---------------------------------------------------------------------------
LAN_IP="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if ($i=="src") print $(i+1)}')"
echo "=================================================================="
echo " BNMV PC audio streamer"
echo " Default sink monitor : ${MONITOR_SOURCE}"
echo " This PC's LAN IP     : ${LAN_IP:-<could not detect>}"
echo " Listen port          : 8888  (override with --listen-port)"
echo " Point BNMV's ACTION_CONNECT_UDP intent at that ip:port from your phone."
echo "=================================================================="

# ---------------------------------------------------------------------------
# 4. Parse a few passthrough options, then hand off to the Python streamer
# ---------------------------------------------------------------------------
EXTRA_ARGS=()
SOURCE="$MONITOR_SOURCE"

while [ $# -gt 0 ]; do
    case "$1" in
        --source)
            SOURCE="$2"; shift 2 ;;
        --listen-port|--send-port|--samplerate|--fft-size|--chunk-frames|--fps|--gain|--db-min|--db-max|--agc-target-db|--agc-tau|--agc-min-db|--agc-max-db|--device-name|--model|--host-ip)
            EXTRA_ARGS+=("$1" "$2"); shift 2 ;;
        --no-autogain)
            EXTRA_ARGS+=("$1"); shift ;;
        *)
            echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done

exec python3 "$PY_SCRIPT" --source "$SOURCE" "${EXTRA_ARGS[@]}"
