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

    public void updateMagnitudes(float[] magnitudes, int sampleRate) {
        if (magnitudes == null || magnitudes.length == 0) return;
        this.mMagnitudes = magnitudes;
        
        // magnitudes is now 512 log-spaced bins (20Hz - 20kHz)
        int binsPerBar = magnitudes.length / NUM_BARS;

        for (int i = 0; i < NUM_BARS; i++) {
            float maxInBar = 0f;
            for (int j = i * binsPerBar; j < (i + 1) * binsPerBar && j < magnitudes.length; j++) {
                if (magnitudes[j] > maxInBar) maxInBar = magnitudes[j];
            }
            
            // Smoothing for visual stability
            if (mTopEnabled) {
                float currentTop = maxInBar * 1.5f * mTopSensitivity;
                mSmoothedMagnitudesTop[i] = mSmoothedMagnitudesTop[i] * 0.7f + currentTop * 0.3f;
            }
            if (mBottomEnabled) {
                float currentBottom = maxInBar * 1.5f * mBottomSensitivity;
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
