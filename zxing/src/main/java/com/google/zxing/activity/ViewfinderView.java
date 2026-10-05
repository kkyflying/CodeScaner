/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.activity;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.google.zxing.R;
import com.google.zxing.camera.FramingRectCalculator;

import androidx.core.content.ContextCompat;

/**
 * 叠加在相机预览之上的取景框视图：绘制取景框外半透明遮罩、边框、四角、
 * 扫描线动画（ValueAnimator 平滑往返）、提示文字与点击对焦指示器。
 * <p>
 * 取景框由 {@link FramingRectCalculator} 按自身尺寸计算，不依赖相机层。
 */
public final class ViewfinderView extends View {

    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int CORNER_RECT_WIDTH = 8;
    private static final int CORNER_RECT_HEIGHT = 40;

    /** 扫描线单程时长（ms），REVERSE 往返即一个完整周期 3.2s */
    private static final long SCANNER_ANIM_DURATION = 1600L;
    /** 对焦结果指示器停留时长（ms） */
    private static final long FOCUS_INDICATOR_DISMISS_DELAY = 1500L;

    private final Paint paint;
    private Bitmap resultBitmap;
    private final int maskColor;
    private final int resultColor;
    private final int laserColor;
    private final int cornerColor;
    private final int frameColor;
    private final int labelTextColor;
    private final float labelTextSize;
    private final String labelText;

    /** 扫描线渐变高度（px，来自 dimen） */
    private final int scanLineHeight;
    /** 对焦指示器边长（px，来自 dimen） */
    private final int focusIndicatorSize;
    private final int focusIndicatorColor;
    private final int focusIndicatorFailColor;

    /** 自持取景框：onSizeChanged 时计算，尺寸变化自然重算 */
    private Rect frame;
    /** 扫描线动画驱动，null 表示未附着 */
    private ValueAnimator scannerAnimator;
    /** 扫描线位置比例 0..1，由动画更新 */
    private float scannerFraction;
    /** 扫描线渐变着色器，尺寸变化时置空重建 */
    private Shader laserShader;

    // 对焦指示器状态机
    private static final int FOCUS_STATE_NONE = 0;
    private static final int FOCUS_STATE_FOCUSING = 1;
    private static final int FOCUS_STATE_SUCCESS = 2;
    private static final int FOCUS_STATE_FAIL = 3;

    private int focusState = FOCUS_STATE_NONE;
    private int focusX;
    private int focusY;

    private final Runnable hideFocusIndicatorRunnable = () -> {
        focusState = FOCUS_STATE_NONE;
        invalidate();
    };

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.ViewfinderView);
        laserColor = array.getColor(R.styleable.ViewfinderView_laser_color, 0x00FF00);
        cornerColor = array.getColor(R.styleable.ViewfinderView_corner_color, 0x00FF00);
        frameColor = array.getColor(R.styleable.ViewfinderView_frame_color, 0xFFFFFF);
        maskColor = array.getColor(R.styleable.ViewfinderView_mask_color, 0x60000000);
        resultColor = array.getColor(R.styleable.ViewfinderView_result_color, 0xB0000000);
        labelTextColor = array.getColor(R.styleable.ViewfinderView_label_text_color, 0x90FFFFFF);
        labelText = array.getString(R.styleable.ViewfinderView_label_text);
        labelTextSize = array.getFloat(R.styleable.ViewfinderView_label_text_size, 36f);
        array.recycle();

        scanLineHeight = getResources().getDimensionPixelSize(R.dimen.zxing_scan_line_height);
        focusIndicatorSize = getResources().getDimensionPixelSize(R.dimen.zxing_focus_indicator_size);
        focusIndicatorColor = ContextCompat.getColor(context, R.color.zxing_focus_indicator);
        focusIndicatorFailColor = ContextCompat.getColor(context, R.color.zxing_focus_indicator_fail);
    }

    /** 供解码层获取当前取景框（屏幕坐标系） */
    public Rect getFramingRect() {
        return frame;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 尺寸变化即重算取景框，同时作废扫描线着色器缓存
        frame = FramingRectCalculator.compute(w, h);
        laserShader = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startScannerAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopScannerAnimation();
        removeCallbacks(hideFocusIndicatorRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // 不可见时暂停扫描线动画省电，恢复可见时续跑
        if (visibility == VISIBLE) {
            startScannerAnimation();
        } else {
            stopScannerAnimation();
        }
    }

    private void startScannerAnimation() {
        if (scannerAnimator != null) {
            if (!scannerAnimator.isStarted()) {
                scannerAnimator.start();
            }
            return;
        }
        scannerAnimator = ValueAnimator.ofFloat(0f, 1f);
        scannerAnimator.setDuration(SCANNER_ANIM_DURATION);
        scannerAnimator.setInterpolator(new LinearInterpolator());
        scannerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        // 往返扫描，避免瞬间跳回顶部
        scannerAnimator.setRepeatMode(ValueAnimator.REVERSE);
        scannerAnimator.addUpdateListener(animation -> {
            scannerFraction = (float) animation.getAnimatedValue();
            Rect frame = this.frame;
            if (frame != null) {
                // 只重绘取景框区域，不刷整个遮罩
                invalidate(frame.left, frame.top, frame.right, frame.bottom);
            }
        });
        scannerAnimator.start();
    }

    private void stopScannerAnimation() {
        if (scannerAnimator != null) {
            scannerAnimator.cancel();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        Rect frame = this.frame;
        if (frame == null) {
            return;
        }
        int width = getWidth();
        int height = getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
            paint.setAlpha(0xFF);
            stopScannerAnimation();
        } else {
            drawFrame(canvas, frame); //画框框
            drawCorner(canvas, frame);
            drawScanLine(canvas, frame);
            drawTextInfo(canvas, frame);
        }
        drawFocusIndicator(canvas);
    }

    private void drawTextInfo(Canvas canvas, Rect frame) {
        if (labelText == null) {
            return;
        }
        paint.setColor(labelTextColor);
        paint.setTextSize(labelTextSize);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(labelText, frame.left + frame.width() / 2, frame.bottom + CORNER_RECT_HEIGHT * 2, paint);
    }

    private void drawFrame(Canvas canvas, Rect frame) {
        paint.setColor(frameColor);
        canvas.drawRect(frame.left, frame.top, frame.right + 1, frame.top + 2, paint);
        canvas.drawRect(frame.left, frame.top + 2, frame.left + 2, frame.bottom - 1, paint);
        canvas.drawRect(frame.right - 1, frame.top, frame.right + 1, frame.bottom - 1, paint);
        canvas.drawRect(frame.left, frame.bottom - 1, frame.right + 1, frame.bottom + 1, paint);
    }

    //绘制边角
    private void drawCorner(Canvas canvas, Rect frame) {
        paint.setColor(cornerColor);
        //左上
        canvas.drawRect(frame.left, frame.top, frame.left + CORNER_RECT_WIDTH, frame.top + CORNER_RECT_HEIGHT, paint);
        canvas.drawRect(frame.left, frame.top, frame.left + CORNER_RECT_HEIGHT, frame.top + CORNER_RECT_WIDTH, paint);
        //右上
        canvas.drawRect(frame.right - CORNER_RECT_WIDTH, frame.top, frame.right, frame.top + CORNER_RECT_HEIGHT, paint);
        canvas.drawRect(frame.right - CORNER_RECT_HEIGHT, frame.top, frame.right, frame.top + CORNER_RECT_WIDTH, paint);
        //左下
        canvas.drawRect(frame.left, frame.bottom - CORNER_RECT_WIDTH, frame.left + CORNER_RECT_HEIGHT, frame.bottom, paint);
        canvas.drawRect(frame.left, frame.bottom - CORNER_RECT_HEIGHT, frame.left + CORNER_RECT_WIDTH, frame.bottom, paint);
        //右下
        canvas.drawRect(frame.right - CORNER_RECT_WIDTH, frame.bottom - CORNER_RECT_HEIGHT, frame.right, frame.bottom, paint);
        canvas.drawRect(frame.right - CORNER_RECT_HEIGHT, frame.bottom - CORNER_RECT_WIDTH, frame.right, frame.bottom, paint);
    }

    /**
     * 扫描线：垂直 LinearGradient（上透明-中实-下透明）随动画比例在取景框内往返。
     * 着色器仅在首次绘制或尺寸变化时创建。
     */
    private void drawScanLine(Canvas canvas, Rect frame) {
        if (frame.height() <= scanLineHeight) {
            return;
        }
        if (laserShader == null) {
            laserShader = new LinearGradient(
                    0, 0, 0, scanLineHeight,
                    new int[]{laserColor & 0x00FFFFFF, laserColor, laserColor & 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
        }
        float top = frame.top + scannerFraction * (frame.height() - scanLineHeight);
        paint.setShader(laserShader);
        canvas.drawRect(frame.left, top, frame.right, top + scanLineHeight, paint);
        paint.setShader(null);
    }

    /**
     * 点击对焦指示器：四角括号框，对焦中白色，成功白色，失败红色，结果停留 1.5s 后消失。
     */
    private void drawFocusIndicator(Canvas canvas) {
        if (focusState == FOCUS_STATE_NONE) {
            return;
        }
        paint.setColor(focusState == FOCUS_STATE_FAIL ? focusIndicatorFailColor : focusIndicatorColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        int half = focusIndicatorSize / 2;
        int arm = focusIndicatorSize / 4;
        int left = focusX - half;
        int top = focusY - half;
        int right = focusX + half;
        int bottom = focusY + half;
        //左上
        canvas.drawLine(left, top + arm, left, top, paint);
        canvas.drawLine(left, top, left + arm, top, paint);
        //右上
        canvas.drawLine(right - arm, top, right, top, paint);
        canvas.drawLine(right, top, right, top + arm, paint);
        //左下
        canvas.drawLine(left, bottom - arm, left, bottom, paint);
        canvas.drawLine(left, bottom, left + arm, bottom, paint);
        //右下
        canvas.drawLine(right - arm, bottom, right, bottom, paint);
        canvas.drawLine(right, bottom - arm, right, bottom, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** 显示对焦指示器（对焦中），坐标为视图坐标系 */
    public void showFocusIndicator(int x, int y) {
        removeCallbacks(hideFocusIndicatorRunnable);
        focusX = x;
        focusY = y;
        focusState = FOCUS_STATE_FOCUSING;
        invalidate();
    }

    /** 对焦结果回调：成功/失败变色，延迟自动隐藏 */
    public void updateFocusIndicator(boolean success) {
        if (focusState == FOCUS_STATE_NONE) {
            return;
        }
        focusState = success ? FOCUS_STATE_SUCCESS : FOCUS_STATE_FAIL;
        removeCallbacks(hideFocusIndicatorRunnable);
        postDelayed(hideFocusIndicatorRunnable, FOCUS_INDICATOR_DISMISS_DELAY);
        invalidate();
    }

    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }

}
