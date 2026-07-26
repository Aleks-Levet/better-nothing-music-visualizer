package com.better.nothing.music.vizualizer.server;

import android.media.AudioFormat;
import android.media.AudioRecord;
import java.io.OutputStream;

/**
 * A standalone Java program to be run via Shizuku as shell user.
 * Captures system audio via REMOTE_SUBMIX and pipes raw PCM to stdout.
 */
public class AudioServer {

    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    public static void main(String[] args) {
        // Send sync bytes to app IMMEDIATELY so it knows we are alive
        try {
            System.out.write(new byte[]{0x42, 0x4E, 0x4D, 0x56}); // "BNMV" magic
            System.out.flush();
        } catch (Exception e) {
            System.err.println("AudioServer: Failed to write magic: " + e.getMessage());
        }

        System.err.println("AudioServer: Starting...");
        
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING);
        if (bufferSize <= 0) bufferSize = 4096;

        // Source 8 is REMOTE_SUBMIX. 
        AudioRecord recorder = null;
        try {
            int source = 8; 
            int bufSize = bufferSize * 4; // Larger buffer for stability
            recorder = new AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, bufSize);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                System.err.println("AudioServer: Failed to initialize AudioRecord (Source " + source + "). Trying Source 1...");
                if (recorder != null) recorder.release();
                recorder = new AudioRecord(1, SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, bufSize);
            }
        } catch (Exception e) {
            System.err.println("AudioServer: Exception during AudioRecord init: " + e.getMessage());
        }

        if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            System.err.println("AudioServer: Fatal - Could not initialize AudioRecord");
            System.exit(1);
        }

        System.err.println("AudioServer: Initialized with Source " + recorder.getAudioSource());

        if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            System.err.println("AudioServer: Fatal - Could not initialize AudioRecord");
            System.exit(1);
        }

        try {
            recorder.startRecording();
            System.err.println("AudioServer: Capture active, streaming to stdout...");
            
            byte[] buffer = new byte[2048];
            OutputStream out = System.out;
            long totalWritten = 0;
            long lastPeakLogMs = System.currentTimeMillis();
            int peak = 0;

            while (true) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    out.write(buffer, 0, read);
                    totalWritten += read;
                    
                    // Simple peak detection for logging
                    for (int i = 0; i < read; i += 2) {
                        if (i + 1 < read) {
                            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                            int abs = Math.abs(sample);
                            if (abs > peak) peak = abs;
                        }
                    }

                    if (System.currentTimeMillis() - lastPeakLogMs > 3000) {
                        System.err.println("AudioServer: Stats - Peak: " + peak + ", Total bytes: " + totalWritten);
                        peak = 0;
                        lastPeakLogMs = System.currentTimeMillis();
                    }
                } else if (read < 0) {
                    System.err.println("AudioServer: Read error: " + read);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("AudioServer: Fatal error: " + e.getMessage());
        } finally {
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) {}
                recorder.release();
            }
            System.err.println("AudioServer: Stopped");
        }
    }
}
