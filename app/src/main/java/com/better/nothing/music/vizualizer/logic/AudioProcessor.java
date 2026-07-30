package com.better.nothing.music.vizualizer.logic;

import android.util.Log;
import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Handles audio capture, FFT processing, and frequency analysis.
 * Refactored to use a centralized 512-bin logarithmic FFT.
 */
public class AudioProcessor {

    public enum ReadMethod {
        MAX, MEAN, RMS
    }

    private ReadMethod mReadMethod = ReadMethod.MAX;

    private int sampleRate = 44100;
    private static final float SPECTRUM_LEAKAGE_FLOOR_RATIO = 0.12f;
    private static final float EPSILON = 0.000001f;

    private int fftSize;
    private int analysisWindow;
    private float hzPerBin;

    private float[] ring;
    private int ringPosition = 0;
    private int filled = 0;

    private double[] fftData;
    private float[] magnitude;
    private float[] hann;
    private DoubleFFT_1D fft;
    private FrequencyRange mUiRange;

    // Centralized 512-bin FFT (values 0-4095)
    private final int[] mRawFFT = new int[512];
    private final int[] mDecayedFFT = new int[512];
    private final int[][] mLogBinToLinearRange = new int[512][2];

    // Hardcoded frequency ranges for 512 logarithmic bins (20Hz - 20kHz)
    public static final float[][] FFT_FREQ_RANGES = {
        {20.00f, 20.27f}, {20.27f, 20.55f}, {20.55f, 20.83f}, {20.83f, 21.11f}, {21.11f, 21.40f}, {21.40f, 21.69f}, {21.69f, 21.98f}, {21.98f, 22.28f}, {22.28f, 22.58f}, {22.58f, 22.89f}, {22.89f, 23.20f}, {23.20f, 23.51f}, {23.51f, 23.83f}, {23.83f, 24.16f}, {24.16f, 24.49f}, {24.49f, 24.82f}, {24.82f, 25.16f}, {25.16f, 25.50f}, {25.50f, 25.84f}, {25.84f, 26.19f}, {26.19f, 26.55f}, {26.55f, 26.91f}, {26.91f, 27.28f}, {27.28f, 27.65f}, {27.65f, 28.02f}, {28.02f, 28.40f}, {28.40f, 28.79f}, {28.79f, 29.18f}, {29.18f, 29.58f}, {29.58f, 29.98f}, {29.98f, 30.39f}, {30.39f, 30.80f}, {30.80f, 31.22f}, {31.22f, 31.64f}, {31.64f, 32.07f}, {32.07f, 32.51f}, {32.51f, 32.95f}, {32.95f, 33.40f}, {33.40f, 33.85f}, {33.85f, 34.31f}, {34.31f, 34.77f}, {34.77f, 35.25f}, {35.25f, 35.73f}, {35.73f, 36.21f}, {36.21f, 36.70f}, {36.70f, 37.20f}, {37.20f, 37.71f}, {37.71f, 38.22f}, {38.22f, 38.74f}, {38.74f, 39.26f}, {39.26f, 39.80f}, {39.80f, 40.34f}, {40.34f, 40.89f}, {40.89f, 41.44f}, {41.44f, 42.00f}, {42.00f, 42.58f}, {42.58f, 43.15f}, {43.15f, 43.74f}, {43.74f, 44.33f}, {44.33f, 44.94f}, {44.94f, 45.55f}, {45.55f, 46.16f}, {46.16f, 46.79f}, {46.79f, 47.43f}, {47.43f, 48.07f}, {48.07f, 48.72f}, {48.72f, 49.39f}, {49.39f, 50.06f}, {50.06f, 50.74f}, {50.74f, 51.43f}, {51.43f, 52.12f}, {52.12f, 52.83f}, {52.83f, 53.55f}, {53.55f, 54.28f}, {54.28f, 55.02f}, {55.02f, 55.76f}, {55.76f, 56.52f}, {56.52f, 57.29f}, {57.29f, 58.07f}, {58.07f, 58.85f}, {58.85f, 59.65f}, {59.65f, 60.46f}, {60.46f, 61.29f}, {61.29f, 62.12f}, {62.12f, 62.96f}, {62.96f, 63.82f}, {63.82f, 64.68f}, {64.68f, 65.56f}, {65.56f, 66.45f}, {66.45f, 67.36f}, {67.36f, 68.27f}, {68.27f, 69.20f}, {69.20f, 70.14f}, {70.14f, 71.09f}, {71.09f, 72.06f}, {72.06f, 73.03f}, {73.03f, 74.03f}, {74.03f, 75.03f}, {75.03f, 76.05f}, {76.05f, 77.08f}, {77.08f, 78.13f}, {78.13f, 79.19f}, {79.19f, 80.27f}, {80.27f, 81.36f}, {81.36f, 82.46f}, {82.46f, 83.58f}, {83.58f, 84.72f}, {84.72f, 85.87f}, {85.87f, 87.04f}, {87.04f, 88.22f}, {88.22f, 89.42f}, {89.42f, 90.63f}, {90.63f, 91.86f}, {91.86f, 93.11f}, {93.11f, 94.38f}, {94.38f, 95.66f}, {95.66f, 96.96f}, {96.96f, 98.27f}, {98.27f, 99.61f}, {99.61f, 100.96f}, {100.96f, 102.33f}, {102.33f, 103.72f}, {103.72f, 105.13f}, {105.13f, 106.56f}, {106.56f, 108.01f}, {108.01f, 109.47f}, {109.47f, 110.96f}, {110.96f, 112.47f}, {112.47f, 114.00f}, {114.00f, 115.54f}, {115.54f, 117.11f}, {117.11f, 118.70f}, {118.70f, 120.32f}, {120.32f, 121.95f}, {121.95f, 123.61f}, {123.61f, 125.29f}, {125.29f, 126.99f}, {126.99f, 128.71f}, {128.71f, 130.46f}, {130.46f, 132.23f}, {132.23f, 134.03f}, {134.03f, 135.85f}, {135.85f, 137.70f}, {137.70f, 139.57f}, {139.57f, 141.46f}, {141.46f, 143.38f}, {143.38f, 145.33f}, {145.33f, 147.31f}, {147.31f, 149.31f}, {149.31f, 151.33f}, {151.33f, 153.39f}, {153.39f, 155.47f}, {155.47f, 157.58f}, {157.58f, 159.73f}, {159.73f, 161.89f}, {161.89f, 164.09f}, {164.09f, 166.32f}, {166.32f, 168.58f}, {168.58f, 170.87f}, {170.87f, 173.19f}, {173.19f, 175.55f}, {175.55f, 177.93f}, {177.93f, 180.35f}, {180.35f, 182.80f}, {182.80f, 185.28f}, {185.28f, 187.80f}, {187.80f, 190.35f}, {190.35f, 192.93f}, {192.93f, 195.55f}, {195.55f, 198.21f}, {198.21f, 200.90f}, {200.90f, 203.63f}, {203.63f, 206.40f}, {206.40f, 209.20f}, {209.20f, 212.04f}, {212.04f, 214.92f}, {214.92f, 217.84f}, {217.84f, 220.80f}, {220.80f, 223.80f}, {223.80f, 226.84f}, {226.84f, 229.92f}, {229.92f, 233.04f}, {233.04f, 236.21f}, {236.21f, 239.42f}, {239.42f, 242.67f}, {242.67f, 245.97f}, {245.97f, 249.31f}, {249.31f, 252.69f}, {252.69f, 256.12f}, {256.12f, 259.60f}, {259.60f, 263.13f}, {263.13f, 266.70f}, {266.70f, 270.33f}, {270.33f, 274.00f}, {274.00f, 277.72f}, {277.72f, 281.49f}, {281.49f, 285.32f}, {285.32f, 289.19f}, {289.19f, 293.12f}, {293.12f, 297.10f}, {297.10f, 301.14f}, {301.14f, 305.23f}, {305.23f, 309.37f}, {309.37f, 313.58f}, {313.58f, 317.84f}, {317.84f, 322.15f}, {322.15f, 326.53f}, {326.53f, 330.96f}, {330.96f, 335.46f}, {335.46f, 340.02f}, {340.02f, 344.63f}, {344.63f, 349.32f}, {349.32f, 354.06f}, {354.06f, 358.87f}, {358.87f, 363.74f}, {363.74f, 368.68f}, {368.68f, 373.69f}, {373.69f, 378.77f}, {378.77f, 383.91f}, {383.91f, 389.13f}, {389.13f, 394.41f}, {394.41f, 399.77f}, {399.77f, 405.20f}, {405.20f, 410.71f}, {410.71f, 416.28f}, {416.28f, 421.94f}, {421.94f, 427.67f}, {427.67f, 433.48f}, {433.48f, 439.37f}, {439.37f, 445.33f}, {445.33f, 451.38f}, {451.38f, 457.51f}, {457.51f, 463.73f}, {463.73f, 470.03f}, {470.03f, 476.41f}, {476.41f, 482.88f}, {482.88f, 489.44f}, {489.44f, 496.09f}, {496.09f, 502.83f}, {502.83f, 509.66f}, {509.66f, 516.58f}, {516.58f, 523.60f}, {523.60f, 530.71f}, {530.71f, 537.92f}, {537.92f, 545.23f}, {545.23f, 552.63f}, {552.63f, 560.14f}, {560.14f, 567.75f}, {567.75f, 575.46f}, {575.46f, 583.28f}, {583.28f, 591.20f}, {591.20f, 599.23f}, {599.23f, 607.37f}, {607.37f, 615.62f}, {615.62f, 623.98f}, {623.98f, 632.46f}, {632.46f, 641.05f}, {641.05f, 649.75f}, {649.75f, 658.58f}, {658.58f, 667.52f}, {667.52f, 676.59f}, {676.59f, 685.78f}, {685.78f, 695.10f}, {695.10f, 704.54f}, {704.54f, 714.11f}, {714.11f, 723.81f}, {723.81f, 733.64f}, {733.64f, 743.61f}, {743.61f, 753.71f}, {753.71f, 763.94f}, {763.94f, 774.32f}, {774.32f, 784.84f}, {784.84f, 795.50f}, {795.50f, 806.30f}, {806.30f, 817.26f}, {817.26f, 828.36f}, {828.36f, 839.61f}, {839.61f, 851.01f}, {851.01f, 862.57f}, {862.57f, 874.29f}, {874.29f, 886.16f}, {886.16f, 898.20f}, {898.20f, 910.40f}, {910.40f, 922.77f}, {922.77f, 935.30f}, {935.30f, 948.01f}, {948.01f, 960.88f}, {960.88f, 973.94f}, {973.94f, 987.16f}, {987.16f, 1000.57f}, {1000.57f, 1014.16f}, {1014.16f, 1027.94f}, {1027.94f, 1041.90f}, {1041.90f, 1056.05f}, {1056.05f, 1070.40f}, {1070.40f, 1084.94f}, {1084.94f, 1099.68f}, {1099.68f, 1114.61f}, {1114.61f, 1129.75f}, {1129.75f, 1145.10f}, {1145.10f, 1160.65f}, {1160.65f, 1176.42f}, {1176.42f, 1192.40f}, {1192.40f, 1208.59f}, {1208.59f, 1225.01f}, {1225.01f, 1241.65f}, {1241.65f, 1258.51f}, {1258.51f, 1275.61f}, {1275.61f, 1292.94f}, {1292.94f, 1310.50f}, {1310.50f, 1328.30f}, {1328.30f, 1346.34f}, {1346.34f, 1364.63f}, {1364.63f, 1383.16f}, {1383.16f, 1401.95f}, {1401.95f, 1420.99f}, {1420.99f, 1440.30f}, {1440.30f, 1459.86f}, {1459.86f, 1479.69f}, {1479.69f, 1499.79f}, {1499.79f, 1520.16f}, {1520.16f, 1540.81f}, {1540.81f, 1561.74f}, {1561.74f, 1582.95f}, {1582.95f, 1604.45f}, {1604.45f, 1626.25f}, {1626.25f, 1648.34f}, {1648.34f, 1670.73f}, {1670.73f, 1693.42f}, {1693.42f, 1716.42f}, {1716.42f, 1739.74f}, {1739.74f, 1763.37f}, {1763.37f, 1787.32f}, {1787.32f, 1811.60f}, {1811.60f, 1836.20f}, {1836.20f, 1861.14f}, {1861.14f, 1886.42f}, {1886.42f, 1912.05f}, {1912.05f, 1938.02f}, {1938.02f, 1964.34f}, {1964.34f, 1991.03f}, {1991.03f, 2018.07f}, {2018.07f, 2045.48f}, {2045.48f, 2073.27f}, {2073.27f, 2101.43f}, {2101.43f, 2129.97f}, {2129.97f, 2158.90f}, {2158.90f, 2188.23f}, {2188.23f, 2217.95f}, {2217.95f, 2248.08f}, {2248.08f, 2278.61f}, {2278.61f, 2309.56f}, {2309.56f, 2340.94f}, {2340.94f, 2372.73f}, {2372.73f, 2404.96f}, {2404.96f, 2437.63f}, {2437.63f, 2470.74f}, {2470.74f, 2504.30f}, {2504.30f, 2538.32f}, {2538.32f, 2572.79f}, {2572.79f, 2607.74f}, {2607.74f, 2643.16f}, {2643.16f, 2679.06f}, {2679.06f, 2715.45f}, {2715.45f, 2752.34f}, {2752.34f, 2789.72f}, {2789.72f, 2827.62f}, {2827.62f, 2866.03f}, {2866.03f, 2904.95f}, {2904.95f, 2944.41f}, {2944.41f, 2984.41f}, {2984.41f, 3024.95f}, {3024.95f, 3066.03f}, {3066.03f, 3107.68f}, {3107.68f, 3149.89f}, {3149.89f, 3192.68f}, {3192.68f, 3236.04f}, {3236.04f, 3280.00f}, {3280.00f, 3324.55f}, {3324.55f, 3369.71f}, {3369.71f, 3415.48f}, {3415.48f, 3461.87f}, {3461.87f, 3508.90f}, {3508.90f, 3556.56f}, {3556.56f, 3604.87f}, {3604.87f, 3653.83f}, {3653.83f, 3703.46f}, {3703.46f, 3753.77f}, {3753.77f, 3804.76f}, {3804.76f, 3856.44f}, {3856.44f, 3908.82f}, {3908.82f, 3961.91f}, {3961.91f, 4015.73f}, {4015.73f, 4070.27f}, {4070.27f, 4125.56f}, {4125.56f, 4181.60f}, {4181.60f, 4238.40f}, {4238.40f, 4295.97f}, {4295.97f, 4354.32f}, {4354.32f, 4413.47f}, {4413.47f, 4473.42f}, {4473.42f, 4534.18f}, {4534.18f, 4595.77f}, {4595.77f, 4658.19f}, {4658.19f, 4721.47f}, {4721.47f, 4785.60f}, {4785.60f, 4850.60f}, {4850.60f, 4916.49f}, {4916.49f, 4983.27f}, {4983.27f, 5050.96f}, {5050.96f, 5119.57f}, {5119.57f, 5189.11f}, {5189.11f, 5259.59f}, {5259.59f, 5331.03f}, {5331.03f, 5403.44f}, {5403.44f, 5476.84f}, {5476.84f, 5551.23f}, {5551.23f, 5626.64f}, {5626.64f, 5703.06f}, {5703.06f, 5780.53f}, {5780.53f, 5859.05f}, {5859.05f, 5938.63f}, {5938.63f, 6019.29f}, {6019.29f, 6101.06f}, {6101.06f, 6183.93f}, {6183.93f, 6267.92f}, {6267.92f, 6353.06f}, {6353.06f, 6439.36f}, {6439.36f, 6526.82f}, {6526.82f, 6615.48f}, {6615.48f, 6705.34f}, {6705.34f, 6796.42f}, {6796.42f, 6888.73f}, {6888.73f, 6982.30f}, {6982.30f, 7077.15f}, {7077.15f, 7173.28f}, {7173.28f, 7270.71f}, {7270.71f, 7369.47f}, {7369.47f, 7469.57f}, {7469.57f, 7571.03f}, {7571.03f, 7673.87f}, {7673.87f, 7778.10f}, {7778.10f, 7883.76f}, {7883.76f, 7990.84f}, {7990.84f, 8099.38f}, {8099.38f, 8209.40f}, {8209.40f, 8320.91f}, {8320.91f, 8433.93f}, {8433.93f, 8548.49f}, {8548.49f, 8664.60f}, {8664.60f, 8782.30f}, {8782.30f, 8901.59f}, {8901.59f, 9022.50f}, {9022.50f, 9145.05f}, {9145.05f, 9269.27f}, {9269.27f, 9395.18f}, {9395.18f, 9522.79f}, {9522.79f, 9652.14f}, {9652.14f, 9783.25f}, {9783.25f, 9916.14f}, {9916.14f, 10050.83f}, {10050.83f, 10187.35f}, {10187.35f, 10325.73f}, {10325.73f, 10465.98f}, {10465.98f, 10608.14f}, {10608.14f, 10752.23f}, {10752.23f, 10898.28f}, {10898.28f, 11046.32f}, {11046.32f, 11196.36f}, {11196.36f, 11348.44f}, {11348.44f, 11502.59f}, {11502.59f, 11658.83f}, {11658.83f, 11817.19f}, {11817.19f, 11977.71f}, {11977.71f, 12140.40f}, {12140.40f, 12305.31f}, {12305.31f, 12472.45f}, {12472.45f, 12641.87f}, {12641.87f, 12813.58f}, {12813.58f, 12987.63f}, {12987.63f, 13164.05f}, {13164.05f, 13342.85f}, {13342.85f, 13524.09f}, {13524.09f, 13707.79f}, {13707.79f, 13893.99f}, {13893.99f, 14082.71f}, {14082.71f, 14274.00f}, {14274.00f, 14467.88f}, {14467.88f, 14664.40f}, {14664.40f, 14863.59f}, {14863.59f, 15065.49f}, {15065.49f, 15270.12f}, {15270.12f, 15477.54f}, {15477.54f, 15687.77f}, {15687.77f, 15900.86f}, {15900.86f, 16116.84f}, {16116.84f, 16335.76f}, {16335.76f, 16557.65f}, {16557.65f, 16782.56f}, {16782.56f, 17010.52f}, {17010.52f, 17241.57f}, {17241.57f, 17475.77f}, {17475.77f, 17713.14f}, {17713.14f, 17953.74f}, {17953.74f, 18197.61f}, {18197.61f, 18444.79f}, {18444.79f, 18695.33f}, {18695.33f, 18949.27f}, {18949.27f, 19206.66f}, {19206.66f, 19467.55f}, {19467.55f, 19731.98f}, {19731.98f, 20000.00f},
    };

    // Improved Autogain state
    private float mRunningMax = 0.01f;
    private float mTargetPeak = 0.45f;
    private float mAutoGain = 1.0f;

    private static final float DECAY_SLOW = 0.998f;
    private static final float GAIN_SMOOTHING_ATTACK = 0.15f;
    private static final float GAIN_SMOOTHING_DECAY = 0.02f;

    public AudioProcessor() {
        updateFFTSize(); // Default
    }

    public void updateFFTSize() {
        updateFFTSize(44100);
    }

    public void updateFFTSize(int sampleRate) {
        int newFftSize = 2048; 

        if (this.fftSize == newFftSize && this.fft != null && this.sampleRate == sampleRate) {
            return;
        }

        this.sampleRate = sampleRate;
        this.fftSize = newFftSize;
        this.analysisWindow = fftSize;
        this.hzPerBin = (float) sampleRate / (float) fftSize;

        this.fft = new DoubleFFT_1D(fftSize);
        this.fftData = new double[fftSize * 2];
        this.magnitude = new float[fftSize / 2 + 1];
        this.hann = buildHannWindow(fftSize);

        this.ring = new float[analysisWindow];
        this.ringPosition = 0;
        this.filled = 0;
        
        this.mUiRange = new FrequencyRange(70f, 130f);

        // Update log bin to linear bin mapping
        for (int i = 0; i < 512; i++) {
            float fStart = FFT_FREQ_RANGES[i][0];
            float fEnd = FFT_FREQ_RANGES[i][1];
            mLogBinToLinearRange[i][0] = Math.max(0, (int) Math.floor(fStart / hzPerBin));
            mLogBinToLinearRange[i][1] = Math.max(mLogBinToLinearRange[i][0], (int) Math.floor(fEnd / hzPerBin));
        }
    }

    public void setReadMethod(ReadMethod method) {
        this.mReadMethod = method;
    }

    public float getHzPerBin() {
        return hzPerBin;
    }

    public int getFFTSize() {
        return fftSize;
    }

    public AudioFrameResult processAudioFrame(short[] hopBuffer, VisualizerConfig config, FrequencyRange hapticRange, FrequencyRange flashlightRange, boolean isInternalSource) {
        if (hopBuffer == null || ring == null || hann == null || fftData == null) {
            Log.e("AudioProcessor", "processAudioFrame: One or more buffers are null");
            return null;
        }

        // Fill ring buffer
        for (short value : hopBuffer) {
            if (ringPosition >= 0 && ringPosition < ring.length) {
                ring[ringPosition] = value / 32768f;
                ringPosition = (ringPosition + 1) % analysisWindow;
            }
        }
        filled = Math.min(filled + hopBuffer.length, analysisWindow);

        if (filled < analysisWindow) {
            return null; // Not enough data yet
        }

        // Process FFT
        for (int i = 0; i < fftSize; i++) {
            if (i < fftData.length && i < hann.length) {
                fftData[i] = ring[(ringPosition + i) % analysisWindow] * hann[i];
            }
        }

        try {
            fft.realForwardFull(fftData);
        } catch (Exception e) {
            Log.e("AudioProcessor", "FFT processing failed", e);
            return null;
        }
        
        int halfFftSize = fftSize / 2;
        float frameMax = 0f;

        // First pass: compute raw magnitudes and find frame peak
        for (int i = 0; i <= halfFftSize; i++) {
            if (2 * i + 1 >= fftData.length) break;
            
            double re = fftData[2 * i];
            double im = fftData[2 * i + 1];
            float mag = (float) (Math.hypot(re, im) / (fftSize / 2.0));

            // Amplify high frequencies
            float freq = i * hzPerBin;
            float boost = 1f + (freq / 10000f) * 4f;
            float rawMag = mag * boost;
            
            if (i < magnitude.length) {
                magnitude[i] = rawMag;
                if (rawMag > frameMax) frameMax = rawMag;
            }
        }

        // Global Auto-Gain Logic
        float decay = frameMax > mRunningMax ? 0.7f : DECAY_SLOW;
        mRunningMax = Math.max(mRunningMax * decay, frameMax);
        float effectiveMax = Math.max(mRunningMax, 0.001f);
        float targetPeak = isInternalSource ? 0.55f : mTargetPeak;
        float desiredGain = targetPeak / effectiveMax;
        desiredGain = Math.max(0.1f, Math.min(200.0f, desiredGain));
        float smoothing = desiredGain < mAutoGain ? GAIN_SMOOTHING_ATTACK : GAIN_SMOOTHING_DECAY;
        mAutoGain = (mAutoGain * (1f - smoothing)) + (desiredGain * smoothing);

        // Apply gain to linear magnitudes
        for (int i = 0; i < magnitude.length; i++) {
            magnitude[i] *= mAutoGain;
        }

        // Map linear magnitude to 512 centralized logarithmic bins
        float decayFactor = 0.85f; // Usual decay at ~60fps
        for (int i = 0; i < 512; i++) {
            int startBin = mLogBinToLinearRange[i][0];
            int endBin = mLogBinToLinearRange[i][1];
            float logMag = 0f;
            
            // Use MAX for building the centralized FFT to preserve peaks
            for (int b = startBin; b <= endBin && b < magnitude.length; b++) {
                if (magnitude[b] > logMag) logMag = magnitude[b];
            }
            
            int rawVal = (int) Math.min(4095, logMag * 4095f);
            mRawFFT[i] = rawVal;
            
            // Apply decay
            if (rawVal > mDecayedFFT[i]) {
                mDecayedFFT[i] = rawVal;
            } else {
                mDecayedFFT[i] = (int) (mDecayedFFT[i] * decayFactor + rawVal * (1f - decayFactor));
            }
        }

        // Compute peaks using the decayed FFT and the selected ReadMethod
        float hapticPeak = hapticRange != null ? computeRangeMagnitude(hapticRange) : 0f;
        float flashlightPeak = flashlightRange != null ? computeRangeMagnitude(flashlightRange) : 0f;
        float uiPeak = mUiRange != null ? computeRangeMagnitude(mUiRange) : 0f;

        // Compute zone magnitudes (uniqueRanges now work with centralized decayed FFT)
        float[] uniqueMagnitudes = computeUniqueMagnitudes(config);

        return new AudioFrameResult(uniqueMagnitudes, hapticPeak, uiPeak, flashlightPeak, mRawFFT.clone(), mDecayedFFT.clone());
    }

    private float[] computeUniqueMagnitudes(VisualizerConfig config) {
        if (config == null) return new float[0];
        float[] uniqueMagnitudes = new float[config.uniqueRanges.length];
        float dominantMagnitude = 0f;
        for (int i = 0; i < config.uniqueRanges.length; i++) {
            float magnitudeVal = computeRangeMagnitude(config.uniqueRanges[i]);
            uniqueMagnitudes[i] = magnitudeVal;
            if (magnitudeVal > dominantMagnitude) {
                dominantMagnitude = magnitudeVal;
            }
        }

        if (dominantMagnitude <= EPSILON) {
            return uniqueMagnitudes;
        }

        float leakageFloor = dominantMagnitude * SPECTRUM_LEAKAGE_FLOOR_RATIO;
        boolean hasFilteredEnergy = false;
        for (int i = 0; i < uniqueMagnitudes.length; i++) {
            uniqueMagnitudes[i] = Math.max(0f, uniqueMagnitudes[i] - leakageFloor);
            if (uniqueMagnitudes[i] > EPSILON) {
                hasFilteredEnergy = true;
            }
        }

        if (!hasFilteredEnergy) {
            for (int i = 0; i < config.uniqueRanges.length; i++) {
                uniqueMagnitudes[i] = computeRangeMagnitude(config.uniqueRanges[i]);
            }
        }
        return uniqueMagnitudes;
    }

    /**
     * Reads from the centralized 512-bin FFT using the current ReadMethod.
     * Returns a value normalized to 0.0 - 1.0 (though underlying values are 0-4095).
     */
    public float computeRangeMagnitude(FrequencyRange range) {
        if (range == null) return 0f;

        int start = Math.max(0, Math.min(range.logBinLo, 511));
        int end = Math.max(start, Math.min(range.logBinHi, 511));
        
        switch (mReadMethod) {
            case MEAN: {
                long sum = 0;
                for (int i = start; i <= end; i++) sum += mDecayedFFT[i];
                return (sum / (float) (end - start + 1)) / 4095f;
            }
            case RMS: {
                double sumSq = 0;
                for (int i = start; i <= end; i++) sumSq += Math.pow(mDecayedFFT[i], 2);
                return (float) (Math.sqrt(sumSq / (end - start + 1)) / 4095.0);
            }
            case MAX:
            default: {
                int max = 0;
                for (int i = start; i <= end; i++) if (mDecayedFFT[i] > max) max = mDecayedFFT[i];
                return max / 4095f;
            }
        }
    }

    private static float[] buildHannWindow(int size) {
        float[] hann = new float[size];
        double denom = Math.max(1d, size - 1d);
        for (int i = 0; i < size; i++) {
            double phase = (2d * Math.PI * i) / denom;
            hann[i] = (float) (0.5d * (1d - Math.cos(phase)));
        }
        return hann;
    }

    public static int findLogBinIndex(float freq, boolean roundUp) {
        for (int i = 0; i < 512; i++) {
            if (freq >= FFT_FREQ_RANGES[i][0] && freq <= FFT_FREQ_RANGES[i][1]) return i;
        }
        if (freq < 20f) return 0;
        return 511;
    }

    // Inner classes for config
    public static final class VisualizerConfig {
        public final String presetKey;
        public final String description;
        public final float decay;
        public final ZoneSpec[] zones;
        public final FrequencyRange[] uniqueRanges;
        public final int[][] zoneToRangeIndices;

        public VisualizerConfig(
                String presetKey,
                String description,
                float decay,
                ZoneSpec[] zones,
                FrequencyRange[] uniqueRanges,
                int[][] zoneToRangeIndices
        ) {
            this.presetKey = presetKey;
            this.description = description;
            this.decay = decay;
            this.zones = zones;
            this.uniqueRanges = uniqueRanges;
            this.zoneToRangeIndices = zoneToRangeIndices;
        }
    }

    public static final class ZoneSpec {
        public final float lowHz;
        public final float highHz;
        public final float lowPercent;
        public final float highPercent;

        public ZoneSpec(float lowHz, float highHz, float lowPercent, float highPercent) {
            this.lowHz = lowHz;
            this.highHz = highHz;
            this.lowPercent = lowPercent;
            this.highPercent = highPercent;
        }

        boolean hasPercentSlice() {
            return !Float.isNaN(lowPercent) && !Float.isNaN(highPercent);
        }
    }

    public static final class FrequencyRange {
        public final float lowHz;
        public final float highHz;
        public final int logBinLo;
        public final int logBinHi;

        public FrequencyRange(float lowHz, float highHz) {
            this.lowHz = lowHz;
            this.highHz = highHz;
            this.logBinLo = findLogBinIndex(lowHz, true);
            this.logBinHi = findLogBinIndex(highHz, false);
        }
    }

    public static final class AudioFrameResult {
        public final float[] uniqueMagnitudes;
        public final float hapticPeak;
        public final float uiPeak;
        public final float flashlightPeak;
        public final int[] rawFFT;
        public final int[] decayedFFT;

        public AudioFrameResult(float[] uniqueMagnitudes, float hapticPeak, float uiPeak, float flashlightPeak, int[] rawFFT, int[] decayedFFT) {
            this.uniqueMagnitudes = uniqueMagnitudes;
            this.hapticPeak = hapticPeak;
            this.uiPeak = uiPeak;
            this.flashlightPeak = flashlightPeak;
            this.rawFFT = rawFFT;
            this.decayedFFT = decayedFFT;
        }
    }
}
