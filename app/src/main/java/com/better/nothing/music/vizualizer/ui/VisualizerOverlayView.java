package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class VisualizerOverlayView extends View {
    private float[] mMagnitudes;
    private final Paint mPaint = new Paint();
    private static final int NUM_BARS = 16;
    private final float[] mSmoothedMagnitudesTop = new float[NUM_BARS];
    private final float[] mSmoothedMagnitudesBottom = new float[NUM_BARS];
    private int mColor = Color.WHITE;
    
    private boolean mTopEnabled = true;
    private boolean mBottomEnabled = false;
    private float mTopSensitivity = 1.0f;
    private float mBottomSensitivity = 1.0f;
    private int mTopHeightPx = 0;
    private int mBottomHeightPx = 0;

    public VisualizerOverlayView(Context context) {
        super(context);
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
    }

    public void setColor(int color) {
        this.mColor = color;
        mPaint.setColor(color);
        invalidate();
    }

    public void setTopEnabled(boolean enabled) {
        this.mTopEnabled = enabled;
        invalidate();
    }

    public void setBottomEnabled(boolean enabled) {
        this.mBottomEnabled = enabled;
        invalidate();
    }

    public void setTopSensitivity(float sensitivity) {
        this.mTopSensitivity = sensitivity;
    }

    public void setBottomSensitivity(float sensitivity) {
        this.mBottomSensitivity = sensitivity;
    }

    public void setHeights(int topPx, int bottomPx) {
        this.mTopHeightPx = topPx;
        this.mBottomHeightPx = bottomPx;
        invalidate();
    }

    public void updateMagnitudes(float[] magnitudes) {
        if (magnitudes == null || magnitudes.length == 0) return;
        this.mMagnitudes = magnitudes;
        
        // Logarithmic grouping for better visual representation
        float minFreq = 20f;
        float maxFreq = 12000f; // Human hearing energy mostly below here for visuals
        float sampleRate = 44100f;
        float hzPerBin = sampleRate / (2f * (magnitudes.length - 1));

        for (int i = 0; i < NUM_BARS; i++) {
            float lowFreq = (float) (minFreq * Math.pow(maxFreq / minFreq, (double) i / NUM_BARS));
            float highFreq = (float) (minFreq * Math.pow(maxFreq / minFreq, (double) (i + 1) / NUM_BARS));
            
            int binLo = Math.max(0, (int) (lowFreq / hzPerBin));
            int binHi = Math.min(magnitudes.length - 1, (int) (highFreq / hzPerBin));
            
            float sum = 0;
            int count = 0;
            for (int j = binLo; j <= binHi; j++) {
                sum += magnitudes[j];
                count++;
            }
            float avg = count > 0 ? sum / count : 0f;
            
            // Smoothing for visual stability
            if (mTopEnabled) {
                float currentTop = avg * 60.0f * mTopSensitivity;
                mSmoothedMagnitudesTop[i] = mSmoothedMagnitudesTop[i] * 0.7f + currentTop * 0.3f;
            }
            if (mBottomEnabled) {
                float currentBottom = avg * 60.0f * mBottomSensitivity;
                mSmoothedMagnitudesBottom[i] = mSmoothedMagnitudesBottom[i] * 0.7f + currentBottom * 0.3f;
            }
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mMagnitudes == null) return;

        int width = getWidth();
        float barWidth = (float) width / NUM_BARS;
        float spacing = 1.5f;
        float cornerRadius = 2f;

        float baselineY = mTopEnabled ? mTopHeightPx : 0;

        for (int i = 0; i < NUM_BARS; i++) {
            float left = i * barWidth + spacing;
            float right = (i + 1) * barWidth - spacing;

            if (mTopEnabled) {
                float valTop = mSmoothedMagnitudesTop[i];
                float barHeightTop = valTop * mTopHeightPx;
                if (barHeightTop > mTopHeightPx) barHeightTop = mTopHeightPx;
                if (barHeightTop < 1.0f) barHeightTop = 1.0f;

                float top = baselineY - barHeightTop;
                float bottom = baselineY;
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, mPaint);
            }

            if (mBottomEnabled) {
                float valBottom = mSmoothedMagnitudesBottom[i];
                float barHeightBottom = valBottom * mBottomHeightPx;
                if (barHeightBottom > mBottomHeightPx) barHeightBottom = mBottomHeightPx;
                if (barHeightBottom < 1.0f) barHeightBottom = 1.0f;

                float top = baselineY;
                float bottom = baselineY + barHeightBottom;
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, mPaint);
            }
        }
    }
}
