package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

public class LensVisualizerView extends View {
    private float[] mMagnitudes;
    private final Paint mPaint = new Paint();
    
    private float mRadiusPx = 40f;
    private float mBarWidthPx = 3f;
    private float mMaxHeightPx = 20f;
    private int mBarCount = 24;
    private float mSensitivity = 1.0f;
    private float mCenterXPercent = 0.5f;
    private float mCenterYPercent = 0.05f;
    
    private float[] mSmoothedMagnitudes = new float[0];

    public LensVisualizerView(Context context) {
        super(context);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
        setBarCount(24);
    }

    public void setRadius(float radiusPx) {
        this.mRadiusPx = radiusPx;
        invalidate();
    }

    public void setBarWidth(float widthPx) {
        this.mBarWidthPx = widthPx;
        invalidate();
    }

    public void setMaxHeight(float heightPx) {
        this.mMaxHeightPx = heightPx;
        invalidate();
    }

    public void setBarCount(int count) {
        if (count == mBarCount && mSmoothedMagnitudes.length > 0) return;
        this.mBarCount = count;
        this.mSmoothedMagnitudes = new float[count];
        invalidate();
    }

    public void setSensitivity(float sensitivity) {
        this.mSensitivity = sensitivity;
    }

    public void setCenterPosition(float xPercent, float yPercent) {
        this.mCenterXPercent = xPercent;
        this.mCenterYPercent = yPercent;
        invalidate();
    }

    public void updateMagnitudes(float[] magnitudes, int sampleRate) {
        if (magnitudes == null || magnitudes.length == 0 || mBarCount <= 0) return;
        this.mMagnitudes = magnitudes;

        float minFreq = 20f;
        float maxFreq = 12000f;
        float hzPerBin = (float) sampleRate / (2f * (magnitudes.length - 1));

        for (int i = 0; i < mBarCount; i++) {
            float lowFreq = (float) (minFreq * Math.pow(maxFreq / minFreq, (double) i / mBarCount));
            float highFreq = (float) (minFreq * Math.pow(maxFreq / minFreq, (double) (i + 1) / mBarCount));
            
            int binLo = Math.max(0, (int) (lowFreq / hzPerBin));
            int binHi = Math.min(magnitudes.length - 1, (int) (highFreq / hzPerBin));
            
            float sum = 0;
            int count = 0;
            for (int j = binLo; j <= binHi; j++) {
                sum += magnitudes[j];
                count++;
            }
            float avg = count > 0 ? sum / count : 0f;
            
            float current = avg * 60.0f * mSensitivity;
            mSmoothedMagnitudes[i] = mSmoothedMagnitudes[i] * 0.7f + current * 0.3f;
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mMagnitudes == null || mSmoothedMagnitudes.length == 0) return;

        float centerX = getWidth() * mCenterXPercent;
        float centerY = getHeight() * mCenterYPercent;

        for (int i = 0; i < mBarCount; i++) {
            float angle = (float) (i * 2 * Math.PI / mBarCount);
            float mag = mSmoothedMagnitudes[i];
            float barHeight = Math.min(mag * mMaxHeightPx, mMaxHeightPx);
            if (barHeight < 1.0f) barHeight = 1.0f;

            float startX = (float) (centerX + mRadiusPx * Math.cos(angle));
            float startY = (float) (centerY + mRadiusPx * Math.sin(angle));

            canvas.save();
            canvas.translate(startX, startY);
            canvas.rotate((float) Math.toDegrees(angle));
            canvas.drawRect(0, -mBarWidthPx / 2, barHeight, mBarWidthPx / 2, mPaint);
            canvas.restore();
        }
    }
}
