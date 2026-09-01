@echo off
setlocal

rem run-bnmv-stream.bat
rem
rem Streams this Windows PC's playing audio to BNMV (Better Nothing Music
rem Visualizer) on your phone.
rem
rem Usage:
rem   run-bnmv-stream.bat [--device "Name"] [--gain N] [--agc-target-db N] ...
rem   run-bnmv-stream.bat --list-devices
rem
rem Just double-click this file, or run it from a command prompt with any
rem extra options. All arguments are passed straight through to
rem bnmv_stream_windows.py.

echo +--------------------------------------+
echo ^| ######   #     #  #     #  #     #   ^|
echo ^| #     #  ##    #  ##   ##  #     #   ^|
echo ^| #     #  # #   #  # # # #  #     #   ^|
echo ^| ######   #  #  #  #  #  #   #   #    ^|
echo ^| #     #  #   # #  #     #   #   #    ^|
echo ^| #     #  #    ##  #     #    # #     ^|
echo ^| ######   #     #  #     #     #      ^|
echo ^|                                      ^|
echo ^|      PC -^> Phone Audio Streamer      ^|
echo ^|          (Windows / WASAPI)          ^|
echo +--------------------------------------+
echo.

where python >nul 2>&1
if errorlevel 1 (
    echo Python was not found on PATH.
    echo Install it from https://www.python.org/downloads/
    echo IMPORTANT: check "Add python.exe to PATH" during setup, then re-run this script.
    pause
    exit /b 1
)

python -c "import numpy" >nul 2>&1
if errorlevel 1 (
    echo Installing required package: numpy ...
    python -m pip install --user numpy
    if errorlevel 1 (
        echo Failed to install numpy. Try running as Administrator, or manually:
        echo   python -m pip install numpy
        pause
        exit /b 1
    )
)

python -c "import soundcard" >nul 2>&1
if errorlevel 1 (
    echo Installing required package: soundcard ...
    python -m pip install --user soundcard
    if errorlevel 1 (
        echo Failed to install soundcard. Try running as Administrator, or manually:
        echo   python -m pip install soundcard
        pause
        exit /b 1
    )
)

python "%~dp0bnmv_stream_windows.py" %*

pause
