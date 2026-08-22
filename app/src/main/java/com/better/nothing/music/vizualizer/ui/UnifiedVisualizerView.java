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
    private BlurMaskFilter mEdgeGlowFilter;
    private BlurMaskFilter mEdgeGlowFilterInner;
    private VisualizerStyle mEdgeStyle = VisualizerStyle.BARS;

    private float[] mSmoothedEdgeTop = new float[0];
    private float[] mSmoothedEdgeBottom = new float[0];
    private float[] mSmoothedEdgeLeft = new float[0];
    private float[] mSmoothedEdgeRight = new float[0];

    private final Path mEdgePath = new Path();
    private final Path mLensPath = new Path();
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
    private BlurMaskFilter mOverlayGlowFilter;
    private BlurMaskFilter mOverlayGlowFilterInner;
    private VisualizerStyle mOverlayStyle = VisualizerStyle.BARS;
    private int mOverlayPaddingPx = 0;
    private float mEmulateHdrOpacity = 0f;
    private final Paint mHdrPaint = new Paint();

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
    private BlurMaskFilter mLensGlowFilter;
    private BlurMaskFilter mLensGlowFilterInner;
    private VisualizerStyle mLensStyle = VisualizerStyle.BARS;

    private float[] mSmoothedLensMagnitudes = new float[0];

    // --- Common ---
    private boolean mRoundedBarsEnabled = false;

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
                                  float opacity, float glowRadius, VisualizerStyle style) {
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
        }
        this.mEdgeCornerRadiusPx = radiusPx;
        this.mEdgeTopEnabled = topEnabled;
        this.mEdgeBottomEnabled = bottomEnabled;
        this.mEdgeColor = color;
        this.mEdgeOpacity = opacity;
        if (this.mEdgeGlowBlurRadius != glowRadius || mEdgeGlowFilter == null) {
            this.mEdgeGlowBlurRadius = glowRadius;
            this.mEdgeGlowFilter = new BlurMaskFilter(Math.max(1f, glowRadius), BlurMaskFilter.Blur.NORMAL);
            this.mEdgeGlowFilterInner = new BlurMaskFilter(Math.max(1f, glowRadius * 0.7f), BlurMaskFilter.Blur.NORMAL);
        }
        this.mEdgeStyle = style;
        invalidate();
    }

    public void setOverlayProperties(boolean enabled, int widthPx, int topHeightPx, int bottomHeightPx, int yOffsetPx,
                                     float topSensitivity, float bottomSensitivity, boolean topEnabled, boolean bottomEnabled,
                                     int color, float opacity, float glowRadius, VisualizerStyle style, int paddingPx,
                                     float emulateHdrOpacity) {
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
        if (this.mOverlayGlowBlurRadius != glowRadius || mOverlayGlowFilter == null) {
            this.mOverlayGlowBlurRadius = glowRadius;
            this.mOverlayGlowFilter = new BlurMaskFilter(Math.max(1f, glowRadius), BlurMaskFilter.Blur.NORMAL);
            this.mOverlayGlowFilterInner = new BlurMaskFilter(Math.max(1f, glowRadius * 0.7f), BlurMaskFilter.Blur.NORMAL);
        }
        this.mOverlayStyle = style;
        this.mOverlayPaddingPx = paddingPx;
        this.mEmulateHdrOpacity = emulateHdrOpacity;
        invalidate();
    }

    public void setLensProperties(boolean enabled, float radiusPx, float widthPx, float xPos, float yPos, float barWidthPx,
                                  float maxHeightPx, int barCount, float sensitivity, int color, float opacity,
                                  float glowRadius, VisualizerStyle style) {
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
        }
        this.mLensSensitivity = sensitivity;
        this.mLensColor = color;
        this.mLensOpacity = opacity;
        if (this.mLensGlowBlurRadius != glowRadius || mLensGlowFilter == null) {
            this.mLensGlowBlurRadius = glowRadius;
            this.mLensGlowFilter = new BlurMaskFilter(Math.max(1f, glowRadius), BlurMaskFilter.Blur.NORMAL);
            this.mLensGlowFilterInner = new BlurMaskFilter(Math.max(1f, glowRadius * 0.65f), BlurMaskFilter.Blur.NORMAL);
        }
        this.mLensStyle = style;
        invalidate();
    }

    public void updateMagnitudes(int[] fftraw) {
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
                float current = val * 1.5f * mEdgeSensitivity;
                mSmoothedEdgeTop[i] = (current > mSmoothedEdgeTop[i]) ? (mSmoothedEdgeTop[i] * 0.4f + current * 0.6f) : (mSmoothedEdgeTop[i] * 0.7f + current * 0.3f);
                mSmoothedEdgeBottom[i] = mSmoothedEdgeTop[i];
            }
            for (int i = 0; i < mEdgeBarCountVert; i++) {
                float normPosR = 1.0f - ((float) i / (mEdgeBarCountVert - 1));
                int binIdxR = (int) (Math.pow(normPosR, 1.5) * focusBins);
                float currentR = (fftraw[Math.min(fftraw.length - 1, binIdxR)] / 4095f) * 1.5f * mEdgeSensitivity;
                mSmoothedEdgeRight[i] = (currentR > mSmoothedEdgeRight[i]) ? (mSmoothedEdgeRight[i] * 0.4f + currentR * 0.6f) : (mSmoothedEdgeRight[i] * 0.7f + currentR * 0.3f);

                float normPosL = (float) i / (mEdgeBarCountVert - 1);
                int binIdxL = (int) (Math.pow(normPosL, 1.5) * focusBins);
                float currentL = (fftraw[Math.min(fftraw.length - 1, binIdxL)] / 4095f) * 1.5f * mEdgeSensitivity;
                mSmoothedEdgeLeft[i] = (currentL > mSmoothedEdgeLeft[i]) ? (mSmoothedEdgeLeft[i] * 0.4f + currentL * 0.6f) : (mSmoothedEdgeLeft[i] * 0.7f + currentL * 0.3f);
            }
        }

        // Update Overlay smoothing
        if (mOverlayEnabled) {
            for (int i = 0; i < OVERLAY_NUM_BARS; i++) {
                float t = (float) i / OVERLAY_NUM_BARS;
                int startBin = (int) (Math.pow(t, 1.5) * focusBins);
                int endBin = (int) (Math.pow((float) (i + 1) / OVERLAY_NUM_BARS, 1.5) * focusBins);
                if (endBin <= startBin) endBin = startBin + 1;
                int maxInBar = 0;
                for (int j = startBin; j < endBin && j < fftraw.length; j++) if (fftraw[j] > maxInBar) maxInBar = fftraw[j];
                float val = maxInBar / 4095f;
                if (mOverlayTopEnabled) {
                    float curT = val * 1.5f * mOverlaySensitivityTop;
                    mSmoothedOverlayTop[i] = (curT > mSmoothedOverlayTop[i]) ? (mSmoothedOverlayTop[i] * 0.4f + curT * 0.6f) : (mSmoothedOverlayTop[i] * 0.7f + curT * 0.3f);
                }
                if (mOverlayBottomEnabled) {
                    float curB = val * 1.5f * mOverlaySensitivityBottom;
                    mSmoothedOverlayBottom[i] = (curB > mSmoothedOverlayBottom[i]) ? (mSmoothedOverlayBottom[i] * 0.4f + curB * 0.6f) : (mSmoothedOverlayBottom[i] * 0.7f + curB * 0.3f);
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
                float current = (maxVal / 4095f) * 1.5f * mLensSensitivity;
                mSmoothedLensMagnitudes[i] = (current > mSmoothedLensMagnitudes[i]) ? (mSmoothedLensMagnitudes[i] * 0.4f + current * 0.6f) : (mSmoothedLensMagnitudes[i] * 0.8f + current * 0.2f);
            }
        }

        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
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
            mGlowPaint.setAlpha((int) (100 * mEdgeOpacity));
            mGlowPaint.setMaskFilter(mEdgeGlowFilter);

            mGlowPaintInner.setColor(mEdgeColor);
            mGlowPaintInner.setAlpha((int) (180 * mEdgeOpacity));
            mGlowPaintInner.setMaskFilter(mEdgeGlowFilterInner);
        } else {
            mPaint.setColor(mEdgeColor);
            mPaint.setAlpha((int) (255 * mEdgeOpacity));
            mPaint.setMaskFilter(null);
        }

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

            if (mEdgeStyle == VisualizerStyle.GLOW) {
                float glowHeight = barHeight * 2.2f;
                float glowThickness = barThickness * 3.5f;
                canvas.drawRoundRect(-1.5f, -glowThickness / 2f, glowHeight + 1.5f, glowThickness / 2f, cornerRadius * 2.8f, cornerRadius * 2.8f, mGlowPaint);
                canvas.drawRoundRect(0, -glowThickness / 2.5f, glowHeight * 0.95f, glowThickness / 2.5f, cornerRadius * 2f, cornerRadius * 2f, mGlowPaintInner);
            } else {
                canvas.drawRoundRect(0, -barThickness / 2, barHeight, barThickness / 2, cornerRadius, cornerRadius, mPaint);
            }
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
            mGlowPaint.setAlpha((int) (120 * mOverlayOpacity));
            mGlowPaint.setMaskFilter(mOverlayGlowFilter);

            mGlowPaintInner.setColor(mOverlayColor);
            mGlowPaintInner.setAlpha((int) (180 * mOverlayOpacity));
            mGlowPaintInner.setMaskFilter(mOverlayGlowFilterInner);
        } else {
            mPaint.setColor(mOverlayColor);
            mPaint.setAlpha((int) (255 * mOverlayOpacity));
            mPaint.setMaskFilter(null);
        }

        for (int i = 0; i < OVERLAY_NUM_BARS; i++) {
            float left = startX + i * barWidth + spacing;
            float right = startX + (i + 1) * barWidth - spacing;
            if (mOverlayTopEnabled) {
                float hT = Math.max(1.0f, Math.min(mSmoothedOverlayTop[i] * mOverlayHeightTopPx, mOverlayHeightTopPx));
                if (mOverlayStyle == VisualizerStyle.GLOW) {
                    float gP = Math.max(18f, hT * 0.55f);
                    canvas.drawRoundRect(left - 3f, baselineY - hT - gP, right + 3f, baselineY + gP, cornerRadius * 2.2f, cornerRadius * 2.2f, mGlowPaint);
                    canvas.drawRoundRect(left - 1.5f, baselineY - hT - gP * 0.35f, right + 1.5f, baselineY + gP * 0.35f, cornerRadius * 2f, cornerRadius * 2f, mGlowPaintInner);
                } else {
                    canvas.drawRoundRect(left, baselineY - hT, right, baselineY, cornerRadius, cornerRadius, mPaint);
                }
            }
            if (mOverlayBottomEnabled) {
                float hB = Math.max(1.0f, Math.min(mSmoothedOverlayBottom[i] * mOverlayHeightBottomPx, mOverlayHeightBottomPx));
                if (mOverlayStyle == VisualizerStyle.GLOW) {
                    float gP = Math.max(18f, hB * 0.55f);
                    canvas.drawRoundRect(left - 3f, baselineY - gP, right + 3f, baselineY + hB + gP, cornerRadius * 2.2f, cornerRadius * 2.2f, mGlowPaint);
                    canvas.drawRoundRect(left - 1.5f, baselineY - gP * 0.35f, right + 1.5f, baselineY + hB + gP * 0.35f, cornerRadius * 2f, cornerRadius * 2f, mGlowPaintInner);
                } else {
                    canvas.drawRoundRect(left, baselineY, right, baselineY + hB, cornerRadius, cornerRadius, mPaint);
                }
            }
        }
    }

    private void drawLens(Canvas canvas) {
        int count = mSmoothedLensMagnitudes.length;
        if (count == 0) return;

        mLensPath.reset();
        if (mLensWidthPx <= 0.1f) {
            mLensPath.addCircle(mLensXPos, mLensYPos, mLensRadiusPx, Path.Direction.CW);
        } else {
            float w = mLensWidthPx;
            float r = mLensRadiusPx;
            float left = mLensXPos - w / 2f;
            float right = mLensXPos + w / 2f;
            mLensPath.moveTo(left, mLensYPos - r);
            mLensPath.lineTo(right, mLensYPos - r);
            mArcRect.set(right - r, mLensYPos - r, right + r, mLensYPos + r);
            mLensPath.arcTo(mArcRect, -90, 180, false);
            mLensPath.lineTo(left, mLensYPos + r);
            mArcRect.set(left - r, mLensYPos - r, left + r, mLensYPos + r);
            mLensPath.arcTo(mArcRect, 90, 180, false);
            mLensPath.close();
        }

        mPathMeasure.setPath(mLensPath, false);
        float totalLength = mPathMeasure.getLength();
        float step = totalLength / count;

        if (mLensStyle == VisualizerStyle.GLOW) {
            float gW = Math.max(16f, mLensBarWidthPx * 4.5f);
            mGlowPaint.setColor(mLensColor);
            mGlowPaint.setStrokeWidth(gW);
            mGlowPaint.setStrokeCap(Paint.Cap.ROUND);
            mGlowPaint.setAlpha((int) (100 * mLensOpacity));
            mGlowPaint.setMaskFilter(mLensGlowFilter);

            mGlowPaintInner.setColor(mLensColor);
            mGlowPaintInner.setStrokeWidth(gW);
            mGlowPaintInner.setStrokeCap(Paint.Cap.ROUND);
            mGlowPaintInner.setAlpha((int) (180 * mLensOpacity));
            mGlowPaintInner.setMaskFilter(mLensGlowFilterInner);
        } else {
            mPaint.setColor(mLensColor);
            mPaint.setStrokeWidth(mLensBarWidthPx);
            mPaint.setStrokeCap((mLensStyle == VisualizerStyle.ROUNDED_BARS || mRoundedBarsEnabled) ? Paint.Cap.ROUND : Paint.Cap.BUTT);
            mPaint.setAlpha((int) (255 * mLensOpacity));
            mPaint.setMaskFilter(null);
        }

        for (int i = 0; i < count; i++) {
            float dist = i * step;
            mPathMeasure.getPosTan(dist, mPos, mTan);
            float barLen = mSmoothedLensMagnitudes[i] * mLensMaxHeightPx;
            
            canvas.save();
            canvas.translate(mPos[0], mPos[1]);
            float angle = (float) Math.toDegrees(Math.atan2(mTan[1], mTan[0]));
            canvas.rotate(angle - 90);

            if (mLensStyle == VisualizerStyle.GLOW) {
                float gL = barLen * 2.2f;
                canvas.drawLine(0, 0, gL, 0, mGlowPaint);
                canvas.drawLine(0, 0, barLen, 0, mGlowPaintInner);
            } else {
                canvas.drawLine(0, 0, barLen, 0, mPaint);
            }
            canvas.restore();
        }
    }
}
