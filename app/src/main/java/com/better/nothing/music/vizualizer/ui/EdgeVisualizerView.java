package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.View;

public class EdgeVisualizerView extends View {
    private float[] mMagnitudes;
    private final Paint mPaint = new Paint();
    
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
        setFitsSystemWindows(false);
        setClickable(false);
        setFocusable(false);
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

    public void updateMagnitudes(float[] magnitudes, int sampleRate) {
        if (magnitudes == null || magnitudes.length == 0) return;
        this.mMagnitudes = magnitudes;
        
        // magnitudes is now 512 log-spaced bins (20Hz - 20kHz)
        for (int i = 0; i < mBarCountHoriz; i++) {
            float center = (mBarCountHoriz - 1) / 2.0f;
            float normDist = Math.abs(i - center) / (mBarCountHoriz / 2f);
            float val = getMagnitudeAt(magnitudes, normDist);
            float current = val * 1.5f * mSensitivity;
            mSmoothedTop[i] = mSmoothedTop[i] * 0.7f + current * 0.3f;
            mSmoothedBottom[i] = mSmoothedBottom[i] * 0.7f + current * 0.3f;
        }

        for (int i = 0; i < mBarCountVert; i++) {
            float normPos = 1.0f - ((float) i / (mBarCountVert - 1));
            float val = getMagnitudeAt(magnitudes, normPos);
            float current = val * 1.5f * mSensitivity;
            mSmoothedRight[i] = mSmoothedRight[i] * 0.7f + current * 0.3f;
        }

        for (int i = 0; i < mBarCountVert; i++) {
            float normPos = (float) i / (mBarCountVert - 1);
            float val = getMagnitudeAt(magnitudes, normPos);
            float current = val * 1.5f * mSensitivity;
            mSmoothedLeft[i] = mSmoothedLeft[i] * 0.7f + current * 0.3f;
        }
        
        postInvalidateOnAnimation();
    }

    private float getMagnitudeAt(float[] magnitudes, float normalizedIndex) {
        int idx = (int) (normalizedIndex * (magnitudes.length - 1));
        return magnitudes[Math.max(0, Math.min(magnitudes.length - 1, idx))];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mMagnitudes == null || mBarHeightPx <= 0) return;

        int w = getWidth();
        int h = getHeight();
        float r = mScreenRadiusPx;

        mEdgePath.reset();
        mEdgePath.moveTo(r, 0);
        mEdgePath.lineTo(w - r, 0); // Top
        mArcRect.set(w - 2 * r, 0, w, 2 * r);
        mEdgePath.arcTo(mArcRect, -90, 90, false); // TR
        mEdgePath.lineTo(w, h - r); // Right
        mArcRect.set(w - 2 * r, h - 2 * r, w, h);
        mEdgePath.arcTo(mArcRect, 0, 90, false); // BR
        mEdgePath.lineTo(r, h); // Bottom
        mArcRect.set(0, h - 2 * r, 2 * r, h);
        mEdgePath.arcTo(mArcRect, 90, 90, false); // BL
        mEdgePath.lineTo(0, r); // Left
        mArcRect.set(0, 0, 2 * r, 2 * r);
        mEdgePath.arcTo(mArcRect, 180, 90, false); // TL
        mEdgePath.close();

        mPathMeasure.setPath(mEdgePath, false);
        float totalLength = mPathMeasure.getLength();
        
        float horizLen = w - 2 * r;
        float vertLen = h - 2 * r;
        float arcLen = (float) (Math.PI * r / 2.0);

        // Distribute bars along the ENTIRE path length
        int totalBars = (mBarCountHoriz + mBarCountVert) * 2;
        float step = totalLength / totalBars;
        float barThickness = step * 0.8f;
        if (barThickness < 1f) barThickness = 1f;

        for (int i = 0; i < totalBars; i++) {
            float dist = i * step + step / 2f;
            
            // Check if segment is enabled
            if (!isSegmentEnabled(dist, horizLen, vertLen, arcLen)) continue;

            mPathMeasure.getPosTan(dist, mPos, mTan);
            float val = sampleMagnitudeAt(dist, horizLen, vertLen, arcLen);
            float barHeight = Math.min(val * mBarHeightPx, mBarHeightPx);
            if (barHeight < 1f) barHeight = 1f;

            canvas.save();
            canvas.translate(mPos[0], mPos[1]);
            float angle = (float) Math.toDegrees(Math.atan2(mTan[1], mTan[0]));
            canvas.rotate(angle + 90);
            canvas.drawRect(0, -barThickness / 2, barHeight, barThickness / 2, mPaint);
            canvas.restore();
        }
    }

    private boolean isSegmentEnabled(float dist, float horizLen, float vertLen, float arcLen) {
        if (dist < horizLen) return mTopEnabled; // Top
        if (dist < horizLen + arcLen) return mTopEnabled || true; // TR corner
        if (dist < horizLen + arcLen + vertLen) return true; // Right
        if (dist < horizLen + 2 * arcLen + vertLen) return mBottomEnabled || true; // BR corner
        if (dist < 2 * horizLen + 2 * arcLen + vertLen) return mBottomEnabled; // Bottom
        return true;
    }

    private float sampleMagnitudeAt(float dist, float horizLen, float vertLen, float arcLen) {
        // Top Edge (0 to horizLen)
        if (dist < horizLen) {
            int idx = (int) (dist / horizLen * mBarCountHoriz);
            return mSmoothedTop[Math.min(idx, mBarCountHoriz - 1)];
        }
        // TR Arc
        if (dist < horizLen + arcLen) {
            float t = (dist - horizLen) / arcLen;
            return mSmoothedTop[mBarCountHoriz - 1] * (1 - t) + mSmoothedRight[0] * t;
        }
        // Right Edge
        if (dist < horizLen + arcLen + vertLen) {
            int idx = (int) ((dist - (horizLen + arcLen)) / vertLen * mBarCountVert);
            return mSmoothedRight[Math.min(idx, mBarCountVert - 1)];
        }
        // BR Arc
        if (dist < horizLen + 2 * arcLen + vertLen) {
            float t = (dist - (horizLen + arcLen + vertLen)) / arcLen;
            return mSmoothedRight[mBarCountVert - 1] * (1 - t) + mSmoothedBottom[0] * t;
        }
        // Bottom Edge
        if (dist < 2 * horizLen + 2 * arcLen + vertLen) {
            int idx = (int) ((dist - (horizLen + 2 * arcLen + vertLen)) / horizLen * mBarCountHoriz);
            // Bottom is reversed in smoothing loop (bass in middle logic)
            return mSmoothedBottom[Math.min(idx, mBarCountHoriz - 1)];
        }
        // BL Arc
        if (dist < 2 * horizLen + 3 * arcLen + vertLen) {
            float t = (dist - (2 * horizLen + 2 * arcLen + vertLen)) / arcLen;
            return mSmoothedBottom[mBarCountHoriz - 1] * (1 - t) + mSmoothedLeft[0] * t;
        }
        // Left Edge
        if (dist < 2 * horizLen + 3 * arcLen + 2 * vertLen) {
            int idx = (int) ((dist - (2 * horizLen + 3 * arcLen + vertLen)) / vertLen * mBarCountVert);
            return mSmoothedLeft[Math.min(idx, mBarCountVert - 1)];
        }
        // TL Arc
        float t = (dist - (2 * horizLen + 3 * arcLen + 2 * vertLen)) / arcLen;
        return mSmoothedLeft[mBarCountVert - 1] * (1 - t) + mSmoothedTop[0] * t;
    }
}
