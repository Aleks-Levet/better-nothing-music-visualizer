package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import com.better.nothing.music.vizualizer.ui.MainViewModel;

public class LensVisualizerView extends View {
    private int[] mFftRaw;
    private final Paint mPaint = new Paint();
    private final Paint mGlowPaint = new Paint();
    private float mGlowBlurRadius = 24f;
    
    private float mRadius = 40f;
    private float mXPos = 180f;
    private float mYPos = 24f;
    private float mBarWidth = 3f;
    private float mMaxHeight = 20f;
    private int mBarCount = 24;
    private float mSensitivity = 1.0f;
    private int mColor = Color.WHITE;
    private boolean mRoundedBarsEnabled = false;
    private VisualizerStyle mStyle = VisualizerStyle.BARS;
    private float mOpacity = 1.0f;
    
    private float[] mSmoothedMagnitudes = new float[0];

    public LensVisualizerView(Context context) {
        super(context);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
        mGlowPaint.setColor(Color.WHITE);
        mGlowPaint.setStyle(Paint.Style.STROKE);
        mGlowPaint.setAntiAlias(true);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setRadius(float radius) { this.mRadius = radius; }
    public void setXPosition(float x) { this.mXPos = x; }
    public void setYPosition(float y) { this.mYPos = y; }
    public void setBarWidth(float width) { this.mBarWidth = width; }
    public void setMaxHeight(float height) { this.mMaxHeight = height; }
    public void setBarCount(int count) { this.mBarCount = count; }
    public void setSensitivity(float sensitivity) { this.mSensitivity = sensitivity; }
    public void setColor(int color) { this.mColor = color; mPaint.setColor(color); invalidate(); }
    public void setRoundedBarsEnabled(boolean enabled) { this.mRoundedBarsEnabled = enabled; invalidate(); }
    public void setStyle(VisualizerStyle style) { this.mStyle = style; invalidate(); }
    public void setOpacity(float opacity) { this.mOpacity = Math.max(0f, Math.min(1f, opacity)); invalidate(); }
    public void setGlowBlurRadius(float radius) { this.mGlowBlurRadius = Math.max(0f, radius); invalidate(); }

    public void updateMagnitudes(int[] fftraw) {
        if (fftraw == null || fftraw.length == 0) return;
        this.mFftRaw = fftraw;
        if (mSmoothedMagnitudes.length != mBarCount) mSmoothedMagnitudes = new float[mBarCount];

        // Focus on the lower to mid-high frequencies (up to ~8kHz if fftraw is 512 bins for 16kHz)
        int focusBins = (int)(fftraw.length * 0.75f);
        
        for (int i = 0; i < mBarCount; i++) {
            // Use logarithmic-ish distribution for bars
            float t = (float) i / mBarCount;
            int startBin = (int) (Math.pow(t, 1.5) * focusBins);
            int endBin = (int) (Math.pow((float) (i + 1) / mBarCount, 1.5) * focusBins);
            if (endBin <= startBin) endBin = startBin + 1;

            int maxVal = 0;
            for (int j = startBin; j < endBin && j < fftraw.length; j++) {
                if (fftraw[j] > maxVal) maxVal = fftraw[j];
            }
            
            float val = maxVal / 4095f;
            float current = val * 1.5f * mSensitivity;
            
            // Smoothing: attack is fast, decay is slower
            if (current > mSmoothedMagnitudes[i]) {
                mSmoothedMagnitudes[i] = mSmoothedMagnitudes[i] * 0.4f + current * 0.6f;
            } else {
                mSmoothedMagnitudes[i] = mSmoothedMagnitudes[i] * 0.8f + current * 0.2f;
            }
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int barCount = mSmoothedMagnitudes.length;
        if (barCount == 0) return;

        int width = getWidth();
        int height = getHeight();
        float density = getResources().getDisplayMetrics().density;
        
        float centerX = mXPos;
        float centerY = mYPos;
        float radius = mRadius * density;
        
        for (int i = 0; i < barCount; i++) {
            float angle = (float) (i * 2 * Math.PI / barCount);
            float magnitude = mSmoothedMagnitudes[i];
            float barLen = magnitude * mMaxHeight * density;
            float startX = (float) (centerX + radius * Math.cos(angle));
            float startY = (float) (centerY + radius * Math.sin(angle));
            float endX = (float) (centerX + (radius + barLen) * Math.cos(angle));
            float endY = (float) (centerY + (radius + barLen) * Math.sin(angle));

            if (mStyle == VisualizerStyle.GLOW) {
                float glowLen = barLen * 2.2f;
                float glowStartX = (float) (centerX + radius * Math.cos(angle));
                float glowStartY = (float) (centerY + radius * Math.sin(angle));
                float glowEndX = (float) (centerX + (radius + glowLen) * Math.cos(angle));
                float glowEndY = (float) (centerY + (radius + glowLen) * Math.sin(angle));
                float glowWidth = Math.max(16f, mBarWidth * density * 4.5f);
                mGlowPaint.setColor(mColor);
                mGlowPaint.setStrokeWidth(glowWidth);
                mGlowPaint.setStrokeCap(Paint.Cap.ROUND);
                mGlowPaint.setAlpha((int) (100 * mOpacity));
                mGlowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, mGlowBlurRadius), BlurMaskFilter.Blur.NORMAL));
                canvas.drawLine(glowStartX, glowStartY, glowEndX, glowEndY, mGlowPaint);
                mGlowPaint.setAlpha((int) (180 * mOpacity));
                mGlowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, mGlowBlurRadius * 0.65f), BlurMaskFilter.Blur.NORMAL));
                canvas.drawLine(startX, startY, endX, endY, mGlowPaint);
                mGlowPaint.setMaskFilter(null);
                continue;
            }

            mPaint.setStrokeWidth(mBarWidth * density);
            mPaint.setStrokeCap((mStyle == VisualizerStyle.ROUNDED_BARS || mRoundedBarsEnabled) ? Paint.Cap.ROUND : Paint.Cap.BUTT);
            mPaint.setAlpha((int) (255 * mOpacity));
            mPaint.setMaskFilter(null);
            canvas.drawLine(startX, startY, endX, endY, mPaint);
        }
    }
}
