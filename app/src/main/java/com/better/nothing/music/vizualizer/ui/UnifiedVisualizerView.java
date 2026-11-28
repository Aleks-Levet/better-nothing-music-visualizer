package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.PowerManager;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import java.util.Arrays;

public class UnifiedVisualizerView extends View {
    private int[] mFftRaw;
    private final Paint mPaint = new Paint();
    private final Paint mGlowPaint = new Paint();
    private final Paint mGlowPaintInner = new Paint();
    private float mDensity;

    // --- Edge Properties ---
    private boolean mEdgeEnabled = false;
    private int mEdgeThicknessPx = 0;
    private float mEdgeSensitivity = 1.0f;
    private int mEdgeBarCountHoriz = 20;
    private int mEdgeBarCountVert = 40;
    private float mEdgeCornerRadiusPx = 0f;
    private boolean mEdgeTopEnabled = true;
    private boolean mEdgeBottomEnabled = true;
    private int mEdgeColor = Color.WHITE;
    private float mEdgeOpacity = 1.0f;
    private float mEdgeGlowBlurRadius = 24f;
    private boolean mEdgeGlowSmooth = true;
    private BlurMaskFilter mEdgeGlowFilter;
    private BlurMaskFilter mEdgeGlowFilterInner;
    private VisualizerStyle mEdgeStyle = VisualizerStyle.BARS;

    private float[] mSmoothedEdgeTop = new float[0];
    private float[] mSmoothedEdgeBottom = new float[0];
    private float[] mSmoothedEdgeLeft = new float[0];
    private float[] mSmoothedEdgeRight = new float[0];
    private float[][] mEdgeGlowPoints = new float[0][2];

    private final Path mEdgePath = new Path();
    private final Path mEdgeGlowPath = new Path();
    private final Path mLensPath = new Path();
    private final Path mLensGlowPath = new Path();
    private final PathMeasure mPathMeasure = new PathMeasure();
    private final float[] mPos = new float[2];
    private final float[] mTan = new float[2];
    private final RectF mArcRect = new RectF();

    // --- Overlay (Navbar) Properties ---
    private boolean mOverlayEnabled = false;
    private int mOverlayWidthPx = 0;
    private int mOverlayHeightTopPx = 0;
    private int mOverlayHeightBottomPx = 0;
    private int mOverlayYOffsetPx = 0;
    private float mOverlaySensitivityTop = 1.0f;
    private float mOverlaySensitivityBottom = 1.0f;
    private boolean mOverlayTopEnabled = true;
    private boolean mOverlayBottomEnabled = false;
    private int mOverlayColor = Color.WHITE;
    private float mOverlayOpacity = 1.0f;
    private float mOverlayGlowBlurRadius = 24f;
    private boolean mOverlayGlowSmooth = true;
    private BlurMaskFilter mOverlayGlowFilter;
    private BlurMaskFilter mOverlayGlowFilterInner;
    private VisualizerStyle mOverlayStyle = VisualizerStyle.BARS;
    private int mOverlayPaddingPx = 0;
    private float mEmulateHdrOpacity = 0f;
    private final Paint mHdrPaint = new Paint();

    private final Path mOverlayGlowPathTop = new Path();
    private final Path mOverlayGlowPathBottom = new Path();

    private static final int OVERLAY_NUM_BARS = 16;
    private final float[] mSmoothedOverlayTop = new float[OVERLAY_NUM_BARS];
    private final float[] mSmoothedOverlayBottom = new float[OVERLAY_NUM_BARS];

    // --- Lens Properties ---
    private boolean mLensEnabled = false;
    private float mLensRadiusPx = 40f;
    private float mLensWidthPx = 0f;
    private float mLensXPos = 180f;
    private float mLensYPos = 24f;
    private float mLensBarWidthPx = 3f;
    private float mLensMaxHeightPx = 20f;
    private int mLensBarCount = 24;
    private float mLensSensitivity = 1.0f;
    private int mLensColor = Color.WHITE;
    private float mLensOpacity = 1.0f;
    private float mLensGlowBlurRadius = 24f;
    private boolean mLensGlowSmooth = true;
    private BlurMaskFilter mLensGlowFilter;
    private BlurMaskFilter mLensGlowFilterInner;
    private VisualizerStyle mLensStyle = VisualizerStyle.BARS;

    private float[] mSmoothedLensMagnitudes = new float[0];
    private float[][] mLensGlowPoints = new float[0][2];

    // --- Common ---
    private boolean mRoundedBarsEnabled = false;
    private boolean mScreenOn = true;

    public UnifiedVisualizerView(Context context) {
        super(context);
        mDensity = getResources().getDisplayMetrics().density;
        mPaint.setAntiAlias(true);
        mGlowPaint.setAntiAlias(true);
        mGlowPaintInner.setAntiAlias(true);
        mHdrPaint.setColor(Color.BLACK);
        
        setFitsSystemWindows(false);
        setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    // --- Setters ---
    public void setRoundedBarsEnabled(boolean enabled) { this.mRoundedBarsEnabled = enabled; invalidate(); }

    public void setEdgeProperties(boolean enabled, int thicknessPx, float sensitivity, int horizCount, int vertCount, 
                                  float radiusPx, boolean topEnabled, boolean bottomEnabled, int color, 
                                  float opacity, float glowRadius, VisualizerStyle style, boolean glowSmooth) {
        this.mEdgeEnabled = enabled;
        this.mEdgeThicknessPx = thicknessPx;
        this.mEdgeSensitivity = sensitivity;
        if (this.mEdgeBarCountHoriz != horizCount || this.mEdgeBarCountVert != vertCount || mSmoothedEdgeTop.length == 0) {
            this.mEdgeBarCountHoriz = horizCount;
            this.mEdgeBarCountVert = vertCount;
            this.mSmoothedEdgeTop = new float[horizCount];
            this.mSmoothedEdgeBottom = new float[horizCount];
            this.mSmoothedEdgeLeft = new float[vertCount];
            this.mSmoothedEdgeRight = new float[vertCount];
            int totalBars = (horizCount + vertCount) * 2;
            this.mEdgeGlowPoints = new float[totalBars][2];
        }
        this.mEdgeCornerRadiusPx = radiusPx;
        this.mEdgeTopEnabled = topEnabled;
        this.mEdgeBottomEnabled = bottomEnabled;
        this.mEdgeColor = color;
        this.mEdgeOpacity = opacity;
        this.mEdgeGlowSmooth = glowSmooth;
        if (this.mEdgeGlowBlurRadius != glowRadius || mEdgeGlowFilter == null) {
            this.mEdgeGlowBlurRadius = glowRadius;
            if (glowRadius > 0f) {
                this.mEdgeGlowFilter = new BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL);
                this.mEdgeGlowFilterInner = new BlurMaskFilter(Math.max(1f, glowRadius * 0.7f), BlurMaskFilter.Blur.NORMAL);
            } else {
                this.mEdgeGlowFilter = null;
                this.mEdgeGlowFilterInner = null;
            }
        }
        this.mEdgeStyle = style;
        invalidate();
    }

    public void setOverlayProperties(boolean enabled, int widthPx, int topHeightPx, int bottomHeightPx, int yOffsetPx,
                                     float topSensitivity, float bottomSensitivity, boolean topEnabled, boolean bottomEnabled,
                                     int color, float opacity, float glowRadius, VisualizerStyle style, int paddingPx,
                                     float emulateHdrOpacity, boolean glowSmooth) {
        this.mOverlayEnabled = enabled;
        this.mOverlayWidthPx = widthPx;
        this.mOverlayHeightTopPx = topHeightPx;
        this.mOverlayHeightBottomPx = bottomHeightPx;
        this.mOverlayYOffsetPx = yOffsetPx;
        this.mOverlaySensitivityTop = topSensitivity;
        this.mOverlaySensitivityBottom = bottomSensitivity;
        this.mOverlayTopEnabled = topEnabled;
        this.mOverlayBottomEnabled = bottomEnabled;
        this.mOverlayColor = color;
        this.mOverlayOpacity = opacity;
        this.mOverlayGlowSmooth = glowSmooth;
        if (this.mOverlayGlowBlurRadius != glowRadius || mOverlayGlowFilter == null) {
            this.mOverlayGlowBlurRadius = glowRadius;
            if (glowRadius > 0f) {
                this.mOverlayGlowFilter = new BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL);
                this.mOverlayGlowFilterInner = new BlurMaskFilter(Math.max(1f, glowRadius * 0.7f), BlurMaskFilter.Blur.NORMAL);
            } else {
                this.mOverlayGlowFilter = null;
                this.mOverlayGlowFilterInner = null;
            }
        }
        this.mOverlayStyle = style;
        this.mOverlayPaddingPx = paddingPx;
        this.mEmulateHdrOpacity = emulateHdrOpacity;
        invalidate();
    }

    public void setLensProperties(boolean enabled, float radiusPx, float widthPx, float xPos, float yPos, float barWidthPx,
                                  float maxHeightPx, int barCount, float sensitivity, int color, float opacity,
                                  float glowRadius, VisualizerStyle style, boolean glowSmooth) {
        this.mLensEnabled = enabled;
        this.mLensRadiusPx = radiusPx;
        this.mLensWidthPx = widthPx;
        this.mLensXPos = xPos;
        this.mLensYPos = yPos;
        this.mLensBarWidthPx = barWidthPx;
        this.mLensMaxHeightPx = maxHeightPx;
        if (this.mLensBarCount != barCount || mSmoothedLensMagnitudes.length == 0) {
            this.mLensBarCount = barCount;
            this.mSmoothedLensMagnitudes = new float[barCount];
            this.mLensGlowPoints = new float[barCount][2];
        }
        this.mLensSensitivity = sensitivity;
        this.mLensColor = color;
        this.mLensOpacity = opacity;
        this.mLensGlowSmooth = glowSmooth;
        if (this.mLensGlowBlurRadius != glowRadius || mLensGlowFilter == null) {
            this.mLensGlowBlurRadius = glowRadius;
            if (glowRadius > 0f) {
                this.mLensGlowFilter = new BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL);
                this.mLensGlowFilterInner = new BlurMaskFilter(Math.max(1f, glowRadius * 0.65f), BlurMaskFilter.Blur.NORMAL);
            } else {
                this.mLensGlowFilter = null;
                this.mLensGlowFilterInner = null;
            }
        }
        this.mLensStyle = style;
        if (!isRenderingPaused()) invalidate();
    }

    public void setScreenOn(boolean screenOn) {
        boolean wasPaused = isRenderingPaused();
        this.mScreenOn = screenOn;
        if (wasPaused && !isRenderingPaused()) {
            postInvalidateOnAnimation();
        }
    }

    public boolean isRenderingPaused() {
        if (!mScreenOn) return true;
        if (!isScreenInteractive()) return true;
        if (getVisibility() != VISIBLE) return true;
        if (getWindowVisibility() != VISIBLE) return true;
        if (getAlpha() < 0.01f) return true;
        if (!mEdgeEnabled && !mOverlayEnabled && !mLensEnabled) return true;
        return false;
    }

    private boolean isScreenInteractive() {
        try {
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (!isRenderingPaused()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (!isRenderingPaused()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isRenderingPaused()) {
            postInvalidateOnAnimation();
        }
    }

    public void updateMagnitudes(int[] fftraw) {
        if (isRenderingPaused()) return;
        if (fftraw == null || fftraw.length == 0) return;
        this.mFftRaw = fftraw;
        int focusBins = (int)(fftraw.length * 0.75f);

        // Update Edge smoothing
        if (mEdgeEnabled) {
            for (int i = 0; i < mEdgeBarCountHoriz; i++) {
                float center = (mEdgeBarCountHoriz - 1) / 2.0f;
                float normDist = Math.abs(i - center) / (mEdgeBarCountHoriz / 2f);
                int binIdx = (int) (Math.pow(normDist, 1.5) * focusBins);
                float val = fftraw[Math.min(fftraw.length - 1, binIdx)] / 4095f;
                float current = val * mEdgeSensitivity;
                mSmoothedEdgeTop[i] = (current > mSmoothedEdgeTop[i]) ? (mSmoothedEdgeTop[i] * 0.4f + current * 0.6f) : (mSmoothedEdgeTop[i] * 0.7f + current * 0.3f);
                mSmoothedEdgeBottom[i] = mSmoothedEdgeTop[i];
            }
            for (int i = 0; i < mEdgeBarCountVert; i++) {
                float normPosR = 1.0f - ((float) i / (mEdgeBarCountVert - 1));
                int binIdxR = (int) (Math.pow(normPosR, 1.5) * focusBins);
                float currentR = (fftraw[Math.min(fftraw.length - 1, binIdxR)] / 4095f) * mEdgeSensitivity;
                mSmoothedEdgeRight[i] = (currentR > mSmoothedEdgeRight[i]) ? (mSmoothedEdgeRight[i] * 0.4f + currentR * 0.6f) : (mSmoothedEdgeRight[i] * 0.7f + currentR * 0.3f);

                float normPosL = (float) i / (mEdgeBarCountVert - 1);
                int binIdxL = (int) (Math.pow(normPosL, 1.5) * focusBins);
                float currentL = (fftraw[Math.min(fftraw.length - 1, binIdxL)] / 4095f) * mEdgeSensitivity;
                mSmoothedEdgeLeft[i] = (currentL > mSmoothedEdgeLeft[i]) ? (mSmoothedEdgeLeft[i] * 0.4f + currentL * 0.6f) : (mSmoothedEdgeLeft[i] * 0.7f + currentL * 0.3f);
            }
        }

        // Update Overlay smoothing (mirrored with bass at center)
        if (mOverlayEnabled) {
            int halfBars = OVERLAY_NUM_BARS / 2;
            for (int k = 0; k < halfBars; k++) {
                float t = (float) k / halfBars;
                int startBin = (int) (Math.pow(t, 1.5) * focusBins);
                int endBin = (int) (Math.pow((float) (k + 1) / halfBars, 1.5) * focusBins);
                if (endBin <= startBin) endBin = startBin + 1;
                int maxInBar = 0;
                for (int j = startBin; j < endBin && j < fftraw.length; j++) if (fftraw[j] > maxInBar) maxInBar = fftraw[j];
                float val = maxInBar / 4095f;

                int leftIdx = (halfBars - 1) - k;
                int rightIdx = halfBars + k;

                if (mOverlayTopEnabled) {
                    float curT = val * mOverlaySensitivityTop;
                    mSmoothedOverlayTop[leftIdx] = (curT > mSmoothedOverlayTop[leftIdx]) ? (mSmoothedOverlayTop[leftIdx] * 0.4f + curT * 0.6f) : (mSmoothedOverlayTop[leftIdx] * 0.7f + curT * 0.3f);
                    mSmoothedOverlayTop[rightIdx] = mSmoothedOverlayTop[leftIdx];
                }
                if (mOverlayBottomEnabled) {
                    float curB = val * mOverlaySensitivityBottom;
                    mSmoothedOverlayBottom[leftIdx] = (curB > mSmoothedOverlayBottom[leftIdx]) ? (mSmoothedOverlayBottom[leftIdx] * 0.4f + curB * 0.6f) : (mSmoothedOverlayBottom[leftIdx] * 0.7f + curB * 0.3f);
                    mSmoothedOverlayBottom[rightIdx] = mSmoothedOverlayBottom[leftIdx];
                }
            }
        }

        // Update Lens smoothing
        if (mLensEnabled) {
            for (int i = 0; i < mLensBarCount; i++) {
                float t = (float) i / mLensBarCount;
                int startBin = (int) (Math.pow(t, 1.5) * focusBins);
                int endBin = (int) (Math.pow((float) (i + 1) / mLensBarCount, 1.5) * focusBins);
                if (endBin <= startBin) endBin = startBin + 1;
                int maxVal = 0;
                for (int j = startBin; j < endBin && j < fftraw.length; j++) if (fftraw[j] > maxVal) maxVal = fftraw[j];
                float current = (maxVal / 4095f) * mLensSensitivity;
                mSmoothedLensMagnitudes[i] = (current > mSmoothedLensMagnitudes[i]) ? (mSmoothedLensMagnitudes[i] * 0.4f + current * 0.6f) : (mSmoothedLensMagnitudes[i] * 0.8f + current * 0.2f);
            }
        }

        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isRenderingPaused()) return;
        if (mFftRaw == null) return;

        if (mEmulateHdrOpacity > 0.001f) {
            mHdrPaint.setAlpha((int) (255 * mEmulateHdrOpacity));
            canvas.drawPaint(mHdrPaint);
        }

        if (mEdgeEnabled) drawEdge(canvas);
        if (mOverlayEnabled) drawOverlay(canvas);
        if (mLensEnabled) drawLens(canvas);
    }

    private void drawEdge(Canvas canvas) {
        if (mEdgeThicknessPx <= 0) return;
        int w = getWidth();
        int h = getHeight();
        float r = mEdgeCornerRadiusPx;

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

        int totalBars = (mEdgeBarCountHoriz + mEdgeBarCountVert) * 2;
        float step = totalLength / totalBars;
        float barThickness = Math.max(1f, step * 0.8f);

        if (mEdgeStyle == VisualizerStyle.GLOW) {
            mGlowPaint.setColor(mEdgeColor);
            mGlowPaint.setAlpha((int) (220 * mEdgeOpacity));
            mGlowPaint.setStyle(Paint.Style.STROKE);
            mGlowPaint.setStrokeWidth(Math.max(3f, step * 0.8f));
            mGlowPaint.setStrokeJoin(Paint.Join.ROUND);
            mGlowPaint.setStrokeCap(Paint.Cap.ROUND);
            mGlowPaint.setMaskFilter(mEdgeGlowFilter);

            if (mEdgeGlowPoints.length != totalBars) {
                mEdgeGlowPoints = new float[totalBars][2];
            }

            for (int i = 0; i < totalBars; i++) {
                float dist = i * step + step / 2f;
                mPathMeasure.getPosTan(dist, mPos, mTan);
                if (!isEdgeSegmentEnabled(dist, horizLen, vertLen, arcLen)) {
                    mEdgeGlowPoints[i][0] = mPos[0];
                    mEdgeGlowPoints[i][1] = mPos[1];
                    continue;
                }
                float val = sampleEdgeMagnitudeAt(dist, horizLen, vertLen, arcLen);
                float barHeight = Math.max(1f, Math.min(val * mEdgeThicknessPx, mEdgeThicknessPx));
                mEdgeGlowPoints[i][0] = mPos[0] + (-mTan[1]) * barHeight;
                mEdgeGlowPoints[i][1] = mPos[1] + mTan[0] * barHeight;
            }

            mEdgeGlowPath.reset();
            if (mEdgeGlowSmooth) {
                float startX = (mEdgeGlowPoints[totalBars - 1][0] + mEdgeGlowPoints[0][0]) / 2f;
                float startY = (mEdgeGlowPoints[totalBars - 1][1] + mEdgeGlowPoints[0][1]) / 2f;
                mEdgeGlowPath.moveTo(startX, startY);
                for (int i = 0; i < totalBars; i++) {
                    int nextIdx = (i + 1) % totalBars;
                    float midX = (mEdgeGlowPoints[i][0] + mEdgeGlowPoints[nextIdx][0]) / 2f;
                    float midY = (mEdgeGlowPoints[i][1] + mEdgeGlowPoints[nextIdx][1]) / 2f;
                    mEdgeGlowPath.quadTo(mEdgeGlowPoints[i][0], mEdgeGlowPoints[i][1], midX, midY);
                }
            } else {
                mEdgeGlowPath.moveTo(mEdgeGlowPoints[0][0], mEdgeGlowPoints[0][1]);
                for (int i = 1; i < totalBars; i++) {
                    mEdgeGlowPath.lineTo(mEdgeGlowPoints[i][0], mEdgeGlowPoints[i][1]);
                }
                mEdgeGlowPath.close();
            }
            canvas.drawPath(mEdgeGlowPath, mGlowPaint);
            return;
        }

        mPaint.setColor(mEdgeColor);
        mPaint.setAlpha((int) (255 * mEdgeOpacity));
        mPaint.setMaskFilter(null);
        mPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < totalBars; i++) {
            float dist = i * step + step / 2f;
            if (!isEdgeSegmentEnabled(dist, horizLen, vertLen, arcLen)) continue;
            mPathMeasure.getPosTan(dist, mPos, mTan);
            float val = sampleEdgeMagnitudeAt(dist, horizLen, vertLen, arcLen);
            float barHeight = Math.max(1f, Math.min(val * mEdgeThicknessPx, mEdgeThicknessPx));
            
            canvas.save();
            canvas.translate(mPos[0], mPos[1]);
            float angle = (float) Math.toDegrees(Math.atan2(mTan[1], mTan[0]));
            canvas.rotate(angle + 90);
            float cornerRadius = (mEdgeStyle == VisualizerStyle.ROUNDED_BARS || mRoundedBarsEnabled) ? barThickness / 2f : 0f;

            canvas.drawRoundRect(0, -barThickness / 2, barHeight, barThickness / 2, cornerRadius, cornerRadius, mPaint);
            canvas.restore();
        }
    }

    private boolean isEdgeSegmentEnabled(float dist, float horizLen, float vertLen, float arcLen) {
        if (dist < horizLen) return mEdgeTopEnabled;
        if (dist < horizLen + arcLen + vertLen + arcLen) return true;
        if (dist < 2 * horizLen + 2 * arcLen + vertLen) return mEdgeBottomEnabled;
        return true;
    }

    private float sampleEdgeMagnitudeAt(float dist, float horizLen, float vertLen, float arcLen) {
        if (dist < horizLen) return mSmoothedEdgeTop[Math.min((int) (dist / horizLen * mEdgeBarCountHoriz), mEdgeBarCountHoriz - 1)];
        if (dist < horizLen + arcLen) return mSmoothedEdgeTop[mEdgeBarCountHoriz - 1] * (1 - (dist - horizLen) / arcLen) + mSmoothedEdgeRight[0] * ((dist - horizLen) / arcLen);
        if (dist < horizLen + arcLen + vertLen) return mSmoothedEdgeRight[Math.min((int) ((dist - (horizLen + arcLen)) / vertLen * mEdgeBarCountVert), mEdgeBarCountVert - 1)];
        if (dist < horizLen + 2 * arcLen + vertLen) return mSmoothedEdgeRight[mEdgeBarCountVert - 1] * (1 - (dist - (horizLen + arcLen + vertLen)) / arcLen) + mSmoothedEdgeBottom[0] * ((dist - (horizLen + arcLen + vertLen)) / arcLen);
        if (dist < 2 * horizLen + 2 * arcLen + vertLen) return mSmoothedEdgeBottom[Math.min((int) ((dist - (horizLen + 2 * arcLen + vertLen)) / horizLen * mEdgeBarCountHoriz), mEdgeBarCountHoriz - 1)];
        if (dist < 2 * horizLen + 3 * arcLen + vertLen) return mSmoothedEdgeBottom[mEdgeBarCountHoriz - 1] * (1 - (dist - (2 * horizLen + 2 * arcLen + vertLen)) / arcLen) + mSmoothedEdgeLeft[0] * ((dist - (2 * horizLen + 2 * arcLen + vertLen)) / arcLen);
        if (dist < 2 * horizLen + 3 * arcLen + 2 * vertLen) return mSmoothedEdgeLeft[Math.min((int) ((dist - (2 * horizLen + 3 * arcLen + vertLen)) / vertLen * mEdgeBarCountVert), mEdgeBarCountVert - 1)];
        return mSmoothedEdgeLeft[mEdgeBarCountVert - 1] * (1 - (dist - (2 * horizLen + 3 * arcLen + 2 * vertLen)) / arcLen) + mSmoothedEdgeTop[0] * ((dist - (2 * horizLen + 3 * arcLen + 2 * vertLen)) / arcLen);
    }

    private void drawOverlay(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float barWidth = (float) (mOverlayWidthPx - 2 * mOverlayPaddingPx) / OVERLAY_NUM_BARS;
        float spacing = 1.5f;
        float centerX = w / 2f;
        float startX = centerX - mOverlayWidthPx / 2f + mOverlayPaddingPx;
        float baselineY = h - mOverlayYOffsetPx - (mOverlayBottomEnabled ? mOverlayHeightBottomPx : 0) - mOverlayPaddingPx;
        float cornerRadius = (mOverlayStyle == VisualizerStyle.ROUNDED_BARS || mRoundedBarsEnabled) ? barWidth / 2f : 2f;

        if (mOverlayStyle == VisualizerStyle.GLOW) {
            mGlowPaint.setColor(mOverlayColor);
            mGlowPaint.setAlpha((int) (200 * mOverlayOpacity));
            mGlowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mGlowPaint.setStrokeWidth(Math.max(2f, barWidth * 0.4f));
            mGlowPaint.setStrokeJoin(Paint.Join.ROUND);
            mGlowPaint.setStrokeCap(Paint.Cap.ROUND);
            mGlowPaint.setMaskFilter(mOverlayGlowFilter);

            float lastX = startX + OVERLAY_NUM_BARS * barWidth;

            if (mOverlayTopEnabled) {
                mOverlayGlowPathTop.reset();
                mOverlayGlowPathTop.moveTo(startX, baselineY);

                if (mOverlayGlowSmooth) {
                    float prevX = startX;
                    float prevY = baselineY;
                    for (int i = 0; i < OVERLAY_NUM_BARS; i++) {
                        float x = startX + (i + 0.5f) * barWidth;
                        float hT = Math.max(1.0f, Math.min(mSmoothedOverlayTop[i] * mOverlayHeightTopPx, mOverlayHeightTopPx));
                        float y = baselineY - hT;
                        float midX = (prevX + x) / 2f;
                        float midY = (prevY + y) / 2f;
                        mOverlayGlowPathTop.quadTo(prevX, prevY, midX, midY);
                        prevX = x;
                        prevY = y;
                    }
                    float midX = (prevX + lastX) / 2f;
                    float midY = (prevY + baselineY) / 2f;
                    mOverlayGlowPathTop.quadTo(prevX, prevY, midX, midY);
                    mOverlayGlowPathTop.lineTo(lastX, baselineY);
                } else {
                    for (int i = 0; i < OVERLAY_NUM_BARS; i++) {
                        float x = startX + (i + 0.5f) * barWidth;
                        float hT = Math.max(1.0f, Math.min(mSmoothedOverlayTop[i] * mOverlayHeightTopPx, mOverlayHeightTopPx));
                        float y = baselineY - hT;
                        mOverlayGlowPathTop.lineTo(x, y);
                    }
                    mOverlayGlowPathTop.lineTo(lastX, baselineY);
                }
                mOverlayGlowPathTop.close();
                canvas.drawPath(mOverlayGlowPathTop, mGlowPaint);
            }

            if (mOverlayBottomEnabled) {
                mOverlayGlowPathBottom.reset();
                mOverlayGlowPathBottom.moveTo(startX, baselineY);

                if (mOverlayGlowSmooth) {
                    float prevX = startX;
                    float prevY = baselineY;
                    for (int i = 0; i < OVERLAY_NUM_BARS; i++) {
                        float x = startX + (i + 0.5f) * barWidth;
                        float hB = Math.max(1.0f, Math.min(mSmoothedOverlayBottom[i] * mOverlayHeightBottomPx, mOverlayHeightBottomPx));
                        float y = baselineY + hB;
                        float midX = (prevX + x) / 2f;
                        float midY = (prevY + y) / 2f;
                        mOverlayGlowPathBottom.quadTo(prevX, prevY, midX, midY);
                        prevX = x;
                        prevY = y;
                    }
                    float midX = (prevX + lastX) / 2f;
                    float midY = (prevY + baselineY) / 2f;
                    mOverlayGlowPathBottom.quadTo(prevX, prevY, midX, midY);
                    mOverlayGlowPathBottom.lineTo(lastX, baselineY);
                } else {
                    for (int i = 0; i < OVERLAY_NUM_BARS; i++) {
                        float x = startX + (i + 0.5f) * barWidth;
                        float hB = Math.max(1.0f, Math.min(mSmoothedOverlayBottom[i] * mOverlayHeightBottomPx, mOverlayHeightBottomPx));
                        float y = baselineY + hB;
                        mOverlayGlowPathBottom.lineTo(x, y);
                    }
                    mOverlayGlowPathBottom.lineTo(lastX, baselineY);
                }
                mOverlayGlowPathBottom.close();
                canvas.drawPath(mOverlayGlowPathBottom, mGlowPaint);
            }
            return;
        }

        mPaint.setColor(mOverlayColor);
        mPaint.setAlpha((int) (255 * mOverlayOpacity));
        mPaint.setMaskFilter(null);
        mPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < OVERLAY_NUM_BARS; i++) {
            float left = startX + i * barWidth + spacing;
            float right = startX + (i + 1) * barWidth - spacing;
            if (mOverlayTopEnabled) {
                float hT = Math.max(1.0f, Math.min(mSmoothedOverlayTop[i] * mOverlayHeightTopPx, mOverlayHeightTopPx));
                canvas.drawRoundRect(left, baselineY - hT, right, baselineY, cornerRadius, cornerRadius, mPaint);
            }
            if (mOverlayBottomEnabled) {
                float hB = Math.max(1.0f, Math.min(mSmoothedOverlayBottom[i] * mOverlayHeightBottomPx, mOverlayHeightBottomPx));
                canvas.drawRoundRect(left, baselineY, right, baselineY + hB, cornerRadius, cornerRadius, mPaint);
            }
        }
    }

    private void drawLens(Canvas canvas) {
        int count = mSmoothedLensMagnitudes.length;
        if (count == 0) return;

        int canvasW = getWidth();
        int canvasH = getHeight();
        if (canvasW <= 0 || canvasH <= 0) return;

        int rotation = Surface.ROTATION_0;
        Display display = getDisplay();
        if (display != null) {
            rotation = display.getRotation();
        } else {
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) {
                rotation = wm.getDefaultDisplay().getRotation();
            }
        }

        float w0, h0;
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            w0 = canvasW;
            h0 = canvasH;
        } else {
            w0 = canvasH;
            h0 = canvasW;
        }

        float centerX, centerY, rotationDegrees;
        switch (rotation) {
            case Surface.ROTATION_90:
                centerX = mLensYPos;
                centerY = w0 - mLensXPos;
                rotationDegrees = -90f;
                break;
            case Surface.ROTATION_180:
                centerX = w0 - mLensXPos;
                centerY = h0 - mLensYPos;
                rotationDegrees = 180f;
                break;
            case Surface.ROTATION_270:
                centerX = h0 - mLensYPos;
                centerY = mLensXPos;
                rotationDegrees = 90f;
                break;
            case Surface.ROTATION_0:
            default:
                centerX = mLensXPos;
                centerY = mLensYPos;
                rotationDegrees = 0f;
                break;
        }

        mLensPath.reset();
        if (mLensWidthPx <= 0.1f) {
            mLensPath.addCircle(0, 0, mLensRadiusPx, Path.Direction.CW);
        } else {
            float w = mLensWidthPx;
            float r = mLensRadiusPx;
            float left = -w / 2f;
            float right = w / 2f;
            mLensPath.moveTo(left, -r);
            mLensPath.lineTo(right, -r);
            mArcRect.set(right - r, -r, right + r, r);
            mLensPath.arcTo(mArcRect, -90, 180, false);
            mLensPath.lineTo(left, r);
            mArcRect.set(left - r, -r, left + r, r);
            mLensPath.arcTo(mArcRect, 90, 180, false);
            mLensPath.close();
        }

        mPathMeasure.setPath(mLensPath, false);
        float totalLength = mPathMeasure.getLength();
        float step = totalLength / count;

        if (mLensStyle == VisualizerStyle.GLOW) {
            mGlowPaint.setColor(mLensColor);
            mGlowPaint.setAlpha((int) (220 * mLensOpacity));
            mGlowPaint.setStyle(Paint.Style.STROKE);
            mGlowPaint.setStrokeWidth(Math.max(3f, mLensBarWidthPx * 2.5f));
            mGlowPaint.setStrokeJoin(Paint.Join.ROUND);
            mGlowPaint.setStrokeCap(Paint.Cap.ROUND);
            mGlowPaint.setMaskFilter(mLensGlowFilter);

            if (mLensGlowPoints.length != count) {
                mLensGlowPoints = new float[count][2];
            }

            for (int i = 0; i < count; i++) {
                float dist = i * step;
                mPathMeasure.getPosTan(dist, mPos, mTan);
                float barLen = mSmoothedLensMagnitudes[i] * mLensMaxHeightPx;
                mLensGlowPoints[i][0] = mPos[0] + (-mTan[1]) * barLen;
                mLensGlowPoints[i][1] = mPos[1] + mTan[0] * barLen;
            }

            mLensGlowPath.reset();
            if (mLensGlowSmooth) {
                float startX = (mLensGlowPoints[count - 1][0] + mLensGlowPoints[0][0]) / 2f;
                float startY = (mLensGlowPoints[count - 1][1] + mLensGlowPoints[0][1]) / 2f;
                mLensGlowPath.moveTo(startX, startY);
                for (int i = 0; i < count; i++) {
                    int nextIdx = (i + 1) % count;
                    float midX = (mLensGlowPoints[i][0] + mLensGlowPoints[nextIdx][0]) / 2f;
                    float midY = (mLensGlowPoints[i][1] + mLensGlowPoints[nextIdx][1]) / 2f;
                    mLensGlowPath.quadTo(mLensGlowPoints[i][0], mLensGlowPoints[i][1], midX, midY);
                }
            } else {
                mLensGlowPath.moveTo(mLensGlowPoints[0][0], mLensGlowPoints[0][1]);
                for (int i = 1; i < count; i++) {
                    mLensGlowPath.lineTo(mLensGlowPoints[i][0], mLensGlowPoints[i][1]);
                }
                mLensGlowPath.close();
            }

            canvas.save();
            canvas.translate(centerX, centerY);
            if (rotationDegrees != 0f) {
                canvas.rotate(rotationDegrees);
            }
            canvas.drawPath(mLensGlowPath, mGlowPaint);
            canvas.restore();
            return;
        }

        mPaint.setColor(mLensColor);
        mPaint.setStrokeWidth(mLensBarWidthPx);
        mPaint.setStrokeCap((mLensStyle == VisualizerStyle.ROUNDED_BARS || mRoundedBarsEnabled) ? Paint.Cap.ROUND : Paint.Cap.BUTT);
        mPaint.setAlpha((int) (255 * mLensOpacity));
        mPaint.setMaskFilter(null);
        mPaint.setStyle(Paint.Style.FILL);

        canvas.save();
        canvas.translate(centerX, centerY);
        if (rotationDegrees != 0f) {
            canvas.rotate(rotationDegrees);
        }

        for (int i = 0; i < count; i++) {
            float dist = i * step;
            mPathMeasure.getPosTan(dist, mPos, mTan);
            float barLen = mSmoothedLensMagnitudes[i] * mLensMaxHeightPx;
            
            canvas.save();
            canvas.translate(mPos[0], mPos[1]);
            float angle = (float) Math.toDegrees(Math.atan2(mTan[1], mTan[0]));
            canvas.rotate(angle - 90);

            canvas.drawLine(0, 0, barLen, 0, mPaint);
            canvas.restore();
        }

        canvas.restore();
    }
}
