package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class LensVisualizerView extends View {
    private float[] mMagnitudes;
    private final Paint mPaint = new Paint();
    
    private float mRadius = 40f;
    private float mXPos = 0.5f;
    private float mYPos = 0.05f;
    private float mBarWidth = 3f;
    private float mMaxHeight = 20f;
    private int mBarCount = 24;
    private float mSensitivity = 1.0f;
    
    private float[] mSmoothedMagnitudes = new float[0];

    public LensVisualizerView(Context context) {
        super(context);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
    }

    public void setRadius(float radius) { this.mRadius = radius; }
    public void setXPosition(float x) { this.mXPos = x; }
    public void setYPosition(float y) { this.mYPos = y; }
    public void setBarWidth(float width) { this.mBarWidth = width; }
    public void setMaxHeight(float height) { this.mMaxHeight = height; }
    public void setBarCount(int count) { this.mBarCount = count; }
    public void setSensitivity(float sensitivity) { this.mSensitivity = sensitivity; }

    public void updateMagnitudes(float[] magnitudes) {
        if (magnitudes == null || magnitudes.length == 0) return;
        this.mMagnitudes = magnitudes;
        
        if (mSmoothedMagnitudes.length != mBarCount) {
            mSmoothedMagnitudes = new float[mBarCount];
        }

        float hzPerBin = 44100f / (2f * (magnitudes.length - 1));

        for (int i = 0; i < mBarCount; i++) {
            // Focus on lower frequencies for better visual impact
            float lowFreq = 40f + (i * 1500f / mBarCount);
            float highFreq = 40f + ((i + 1) * 1500f / mBarCount);
            
            int binLo = Math.max(0, (int) (lowFreq / hzPerBin));
            int binHi = Math.min(magnitudes.length - 1, (int) (highFreq / hzPerBin));
            
            float sum = 0;
            int count = 0;
            for (int j = binLo; j <= binHi; j++) {
                sum += magnitudes[j];
                count++;
            }
            float avg = count > 0 ? sum / count : 0f;
            
            float current = avg * 50.0f * mSensitivity;
            mSmoothedMagnitudes[i] = mSmoothedMagnitudes[i] * 0.8f + current * 0.2f;
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
        
        float centerX = width * mXPos;
        float centerY = height * mYPos;
        float radius = mRadius * density;
        
        for (int i = 0; i < barCount; i++) {
            float angle = (float) (i * 2 * Math.PI / barCount);
            float magnitude = mSmoothedMagnitudes[i];
            float barLen = magnitude * mMaxHeight * density;
            
            float startX = (float) (centerX + radius * Math.cos(angle));
            float startY = (float) (centerY + radius * Math.sin(angle));
            float endX = (float) (centerX + (radius + barLen) * Math.cos(angle));
            float endY = (float) (centerY + (radius + barLen) * Math.sin(angle));
            
            mPaint.setStrokeWidth(mBarWidth * density);
            canvas.drawLine(startX, startY, endX, endY, mPaint);
        }
    }
}
