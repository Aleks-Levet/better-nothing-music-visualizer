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
# 1. Dependency checks
# ---------------------------------------------------------------------------
missing=()

command -v python3 >/dev/null 2>&1 || missing+=("python3")
command -v pactl    >/dev/null 2>&1 || missing+=("pulseaudio-utils (for pactl)")
command -v parec    >/dev/null 2>&1 || missing+=("pulseaudio-utils / pipewire-pulse (for parec)")

if [ ${#missing[@]} -ne 0 ]; then
    echo "Missing required tools:" >&2
    printf '  - %s\n' "${missing[@]}" >&2
    echo "Debian/Ubuntu: sudo apt install pulseaudio-utils python3-venv" >&2
    echo "Arch:          sudo pacman -S python pipewire-pulse (or pulseaudio)" >&2
    echo "Fedora:        sudo dnf install pulseaudio-utils python3" >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# 2. Ensure numpy is available (use a venv if the system is PEP 668 managed
#    or if --user install fails)
# ---------------------------------------------------------------------------
VENV_DIR="$SCRIPT_DIR/.venv"

if ! python3 -c "import numpy" >/dev/null 2>&1; then
    echo "Python 'numpy' not found. Attempting install..."

    # Try a simple --user install first (works on Debian/Ubuntu/Fedora)
    if pip3 install --user numpy 2>/dev/null; then
        echo "numpy installed via pip --user."
    elif [ ! -d "$VENV_DIR" ]; then
        # PEP 668 distros (Arch, Fedora 40+, etc.) block --user installs.
        # Fall back to a local virtual environment.
        echo "Creating virtual environment in $VENV_DIR ..."
        python3 -m venv "$VENV_DIR"
        "$VENV_DIR/bin/pip" install numpy
        echo "numpy installed in venv."
    elif [ -d "$VENV_DIR" ]; then
        "$VENV_DIR/bin/pip" install numpy
        echo "numpy installed in existing venv."
    else
        echo "ERROR: Could not install numpy. Install it manually:" >&2
        echo "  python3 -m venv $VENV_DIR && $VENV_DIR/bin/pip install numpy" >&2
        exit 1
    fi
fi

# Use venv python if it exists, otherwise fall back to system python3
if [ -x "$VENV_DIR/bin/python" ]; then
    PYTHON="$VENV_DIR/bin/python"
else
    PYTHON="python3"
fi

# ---------------------------------------------------------------------------
# 3. Find the monitor source for the default sink (i.e. "what's playing")
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
# 4. Figure out and print this machine's LAN IP, for the phone-side config
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
# 5. Parse a few passthrough options, then hand off to the Python streamer
# ---------------------------------------------------------------------------
EXTRA_ARGS=()
SOURCE="$MONITOR_SOURCE"

while [ $# -gt 0 ]; do
    case "$1" in
        --source)
            SOURCE="$2"; shift 2 ;;
        --listen-port|--send-port|--samplerate|--fft-size|--chunk-frames|--fps|--gain|--db-min|--db-max)
            EXTRA_ARGS+=("$1" "$2"); shift 2 ;;
        *)
            echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done

exec "$PYTHON" "$PY_SCRIPT" --source "$SOURCE" "${EXTRA_ARGS[@]}"
