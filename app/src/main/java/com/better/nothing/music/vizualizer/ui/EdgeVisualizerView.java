package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.View;

import com.better.nothing.music.vizualizer.ui.MainViewModel;

public class EdgeVisualizerView extends View {
    private int[] mFftRaw;
    private final Paint mPaint = new Paint();
    private final Paint mGlowPaint = new Paint();
    private float mGlowBlurRadius = 24f;
    
    private int mBarCountHoriz = 20;
    private int mBarCountVert = 40;
    
    private float[] mSmoothedTop = new float[0];
    private float[] mSmoothedBottom = new float[0];
    private float[] mSmoothedLeft = new float[0];
    private float[] mSmoothedRight = new float[0];
    
    private int mColor = Color.WHITE;
    private float mSensitivity = 1.0f;
    private int mBarHeightPx = 0;
    private float mScreenRadiusPx = 0f;

    private boolean mTopEnabled = true;
    private boolean mBottomEnabled = true;
    private boolean mRoundedBarsEnabled = false;
    private VisualizerStyle mStyle = VisualizerStyle.BARS;
    private float mOpacity = 1.0f;

    private final Path mEdgePath = new Path();
    private final PathMeasure mPathMeasure = new PathMeasure();
    private final float[] mPos = new float[2];
    private final float[] mTan = new float[2];
    private final RectF mArcRect = new RectF();

    public EdgeVisualizerView(Context context) {
        super(context);
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
        mGlowPaint.setColor(mColor);
        mGlowPaint.setStyle(Paint.Style.FILL);
        mGlowPaint.setAntiAlias(true);
        setFitsSystemWindows(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setBarCounts(20, 40);
    }

    public void setColor(int color) {
        this.mColor = color;
        mPaint.setColor(color);
        invalidate();
    }

    public void setSensitivity(float sensitivity) {
        this.mSensitivity = sensitivity;
    }

    public void setThickness(int heightPx) {
        this.mBarHeightPx = heightPx;
        invalidate();
    }
    
    public void setScreenRadius(float radiusPx) {
        this.mScreenRadiusPx = radiusPx;
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
    
    public void setRoundedBarsEnabled(boolean enabled) {
        this.mRoundedBarsEnabled = enabled;
        invalidate();
    }
    
    public void setStyle(VisualizerStyle style) {
        this.mStyle = style;
        invalidate();
    }

    public void setOpacity(float opacity) {
        this.mOpacity = Math.max(0f, Math.min(1f, opacity));
        invalidate();
    }

    public void setGlowBlurRadius(float radius) {
        this.mGlowBlurRadius = Math.max(0f, radius);
        invalidate();
    }
    
    public void setBarCounts(int horiz, int vert) {
        if (horiz == mBarCountHoriz && vert == mBarCountVert && mSmoothedTop.length > 0) return;
        this.mBarCountHoriz = horiz;
        this.mBarCountVert = vert;
        this.mSmoothedTop = new float[horiz];
        this.mSmoothedBottom = new float[horiz];
        this.mSmoothedLeft = new float[vert];
        this.mSmoothedRight = new float[vert];
        invalidate();
    }

    public void updateMagnitudes(int[] fftraw) {
        if (fftraw == null || fftraw.length == 0) return;
        this.mFftRaw = fftraw;
        
        int focusBins = (int)(fftraw.length * 0.75f);
        
        for (int i = 0; i < mBarCountHoriz; i++) {
            float center = (mBarCountHoriz - 1) / 2.0f;
            float normDist = Math.abs(i - center) / (mBarCountHoriz / 2f);
            
            // Log-ish sampling
            int binIdx = (int) (Math.pow(normDist, 1.5) * focusBins);
            float val = fftraw[Math.min(fftraw.length - 1, binIdx)] / 4095f;
            
            float current = val * 1.5f * mSensitivity;
            if (current > mSmoothedTop[i]) {
                mSmoothedTop[i] = mSmoothedTop[i] * 0.4f + current * 0.6f;
            } else {
                mSmoothedTop[i] = mSmoothedTop[i] * 0.7f + current * 0.3f;
            }
            mSmoothedBottom[i] = mSmoothedTop[i];
        }

        for (int i = 0; i < mBarCountVert; i++) {
            float normPos = 1.0f - ((float) i / (mBarCountVert - 1));
            int binIdx = (int) (Math.pow(normPos, 1.5) * focusBins);
            float val = fftraw[Math.min(fftraw.length - 1, binIdx)] / 4095f;
            
            float current = val * 1.5f * mSensitivity;
            if (current > mSmoothedRight[i]) {
                mSmoothedRight[i] = mSmoothedRight[i] * 0.4f + current * 0.6f;
            } else {
                mSmoothedRight[i] = mSmoothedRight[i] * 0.7f + current * 0.3f;
            }
        }

        for (int i = 0; i < mBarCountVert; i++) {
            float normPos = (float) i / (mBarCountVert - 1);
            int binIdx = (int) (Math.pow(normPos, 1.5) * focusBins);
            float val = fftraw[Math.min(fftraw.length - 1, binIdx)] / 4095f;
            
            float current = val * 1.5f * mSensitivity;
            if (current > mSmoothedLeft[i]) {
                mSmoothedLeft[i] = mSmoothedLeft[i] * 0.4f + current * 0.6f;
            } else {
                mSmoothedLeft[i] = mSmoothedLeft[i] * 0.7f + current * 0.3f;
            }
        }
        
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mFftRaw == null || mBarHeightPx <= 0) return;

        int w = getWidth();
        int h = getHeight();
        float r = mScreenRadiusPx;

        mEdgePath.reset();
        mEdgePath.moveTo(r, 0);
        mEdgePath.lineTo(w - r, 0);
        mArcRect.set(w - 2 * r, 0, w, 2 * r);
        mEdgePath.arcTo(mArcRect, -90, 90, false);
        mEdgePath.lineTo(w, h - r);
        mArcRect.set(w - 2 * r, h - 2 * r, w, h);
        mEdgePath.arcTo(mArcRect, 0, 90, false);
        mEdgePath.lineTo(r, h);
        mArcRect.set(0, h - 2 * r, 2 * r, h);
        mEdgePath.arcTo(mArcRect, 90, 90, false);
        mEdgePath.lineTo(0, r);
        mArcRect.set(0, 0, 2 * r, 2 * r);
        mEdgePath.arcTo(mArcRect, 180, 90, false);
        mEdgePath.close();

        mPathMeasure.setPath(mEdgePath, false);
        float totalLength = mPathMeasure.getLength();
        
        float horizLen = w - 2 * r;
        float vertLen = h - 2 * r;
        float arcLen = (float) (Math.PI * r / 2.0);

        int totalBars = (mBarCountHoriz + mBarCountVert) * 2;
        float step = totalLength / totalBars;
        float barThickness = step * 0.8f;
        if (barThickness < 1f) barThickness = 1f;

        for (int i = 0; i < totalBars; i++) {
            float dist = i * step + step / 2f;
            if (!isSegmentEnabled(dist, horizLen, vertLen, arcLen)) continue;
            mPathMeasure.getPosTan(dist, mPos, mTan);
            float val = sampleMagnitudeAt(dist, horizLen, vertLen, arcLen);
            float barHeight = Math.min(val * mBarHeightPx, mBarHeightPx);
            if (barHeight < 1f) barHeight = 1f;
            canvas.save();
            canvas.translate(mPos[0], mPos[1]);
            float angle = (float) Math.toDegrees(Math.atan2(mTan[1], mTan[0]));
            canvas.rotate(angle + 90);
            float cornerRadius = (mStyle == VisualizerStyle.ROUNDED_BARS || mRoundedBarsEnabled) ? barThickness / 2f : 0f;

            if (mStyle == VisualizerStyle.GLOW) {
                float glowHeight = barHeight * 2.2f;
                float glowThickness = barThickness * 3.5f;
                mGlowPaint.setColor(mColor);
                mGlowPaint.setAlpha((int) (100 * mOpacity));
                mGlowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, mGlowBlurRadius), BlurMaskFilter.Blur.NORMAL));
                canvas.drawRoundRect(-1.5f, -glowThickness / 2f, glowHeight + 1.5f, glowThickness / 2f, cornerRadius * 2.8f, cornerRadius * 2.8f, mGlowPaint);
                mGlowPaint.setAlpha((int) (180 * mOpacity));
                mGlowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, mGlowBlurRadius * 0.7f), BlurMaskFilter.Blur.NORMAL));
                canvas.drawRoundRect(0, -glowThickness / 2.5f, glowHeight * 0.95f, glowThickness / 2.5f, cornerRadius * 2f, cornerRadius * 2f, mGlowPaint);
                mGlowPaint.setMaskFilter(null);
                canvas.restore();
                continue;
            }

            mPaint.setMaskFilter(null);
            mPaint.setAlpha((int) (255 * mOpacity));
            canvas.drawRoundRect(0, -barThickness / 2, barHeight, barThickness / 2, cornerRadius, cornerRadius, mPaint);
            canvas.restore();
        }
    }

    private boolean isSegmentEnabled(float dist, float horizLen, float vertLen, float arcLen) {
        if (dist < horizLen) return mTopEnabled;
        if (dist < horizLen + arcLen) return mTopEnabled || true;
        if (dist < horizLen + arcLen + vertLen) return true;
        if (dist < horizLen + 2 * arcLen + vertLen) return mBottomEnabled || true;
        if (dist < 2 * horizLen + 2 * arcLen + vertLen) return mBottomEnabled;
        return true;
    }

    private float sampleMagnitudeAt(float dist, float horizLen, float vertLen, float arcLen) {
        if (dist < horizLen) {
            int idx = (int) (dist / horizLen * mBarCountHoriz);
            return mSmoothedTop[Math.min(idx, mBarCountHoriz - 1)];
        }
        if (dist < horizLen + arcLen) {
            float t = (dist - horizLen) / arcLen;
            return mSmoothedTop[mBarCountHoriz - 1] * (1 - t) + mSmoothedRight[0] * t;
        }
        if (dist < horizLen + arcLen + vertLen) {
            int idx = (int) ((dist - (horizLen + arcLen)) / vertLen * mBarCountVert);
            return mSmoothedRight[Math.min(idx, mBarCountVert - 1)];
        }
        if (dist < horizLen + 2 * arcLen + vertLen) {
            float t = (dist - (2 * horizLen + arcLen + vertLen)) / arcLen;
            return mSmoothedRight[mBarCountVert - 1] * (1 - t) + mSmoothedBottom[0] * t;
        }
        if (dist < 2 * horizLen + 2 * arcLen + vertLen) {
            int idx = (int) ((dist - (horizLen + 2 * arcLen + vertLen)) / horizLen * mBarCountHoriz);
            return mSmoothedBottom[Math.min(idx, mBarCountHoriz - 1)];
        }
        if (dist < 2 * horizLen + 3 * arcLen + vertLen) {
            float t = (dist - (2 * horizLen + 2 * arcLen + vertLen)) / arcLen;
            return mSmoothedBottom[mBarCountHoriz - 1] * (1 - t) + mSmoothedLeft[0] * t;
        }
        if (dist < 2 * horizLen + 3 * arcLen + 2 * vertLen) {
            int idx = (int) ((dist - (2 * horizLen + 3 * arcLen + vertLen)) / vertLen * mBarCountVert);
            return mSmoothedLeft[Math.min(idx, mBarCountVert - 1)];
        }
        float t = (dist - (2 * horizLen + 3 * arcLen + 2 * vertLen)) / arcLen;
        return mSmoothedLeft[mBarCountVert - 1] * (1 - t) + mSmoothedTop[0] * t;
    }
}
