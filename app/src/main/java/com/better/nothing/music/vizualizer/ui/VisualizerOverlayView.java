package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class VisualizerOverlayView extends View {
    private int[] mFftRaw;
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
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
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

    public void updateMagnitudes(int[] fftraw) {
        if (fftraw == null || fftraw.length == 0) return;
        this.mFftRaw = fftraw;
        
        // Focus on audible range (up to ~8kHz)
        int focusBins = (int) (fftraw.length * 0.75f);

        for (int i = 0; i < NUM_BARS; i++) {
            float t = (float) i / NUM_BARS;
            int startBin = (int) (Math.pow(t, 1.5) * focusBins);
            int endBin = (int) (Math.pow((float) (i + 1) / NUM_BARS, 1.5) * focusBins);
            if (endBin <= startBin) endBin = startBin + 1;

            int maxInBar = 0;
            for (int j = startBin; j < endBin && j < fftraw.length; j++) {
                if (fftraw[j] > maxInBar) maxInBar = fftraw[j];
            }
            
            float val = maxInBar / 4095f;
            if (mTopEnabled) {
                float currentTop = val * 1.5f * mTopSensitivity;
                if (currentTop > mSmoothedMagnitudesTop[i]) {
                    mSmoothedMagnitudesTop[i] = mSmoothedMagnitudesTop[i] * 0.4f + currentTop * 0.6f;
                } else {
                    mSmoothedMagnitudesTop[i] = mSmoothedMagnitudesTop[i] * 0.7f + currentTop * 0.3f;
                }
            }
            if (mBottomEnabled) {
                float currentBottom = val * 1.5f * mBottomSensitivity;
                if (currentBottom > mSmoothedMagnitudesBottom[i]) {
                    mSmoothedMagnitudesBottom[i] = mSmoothedMagnitudesBottom[i] * 0.4f + currentBottom * 0.6f;
                } else {
                    mSmoothedMagnitudesBottom[i] = mSmoothedMagnitudesBottom[i] * 0.7f + currentBottom * 0.3f;
                }
            }
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mFftRaw == null) return;

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
