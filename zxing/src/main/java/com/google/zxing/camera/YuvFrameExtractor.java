package com.google.zxing.camera;

import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

/**
 * YUV 帧提取工具：从 {@link ImageProxy} 的 Y（亮度）平面提取数据，
 * 单循环完成紧凑拷贝 + 旋转 + 裁剪。zxing 解码只依赖亮度平面，
 * 因此无需处理 UV 平面（也就天然免疫半平面交错差异）。
 * <p>
 * 统一按 {@code rowStride / pixelStride} 寻址，兼容部分机型 Y 平面
 * 按对齐字节数 padding 的情况（rowStride != width）。
 */
public final class YuvFrameExtractor {

    private static final String TAG = YuvFrameExtractor.class.getSimpleName();

    /** 提取结果：直立（已按 rotationDegrees 旋转）且紧凑的 Y 平面窗口数据 */
    public static final class UprightY {
        public final byte[] data;
        public final int width;
        public final int height;

        public UprightY(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }

    private YuvFrameExtractor() {
    }

    /**
     * 提取直立 Y 平面的指定窗口。
     *
     * @param image           分析帧（调用方负责 close）
     * @param rotationDegrees 需顺时针旋转的角度，取值 0/90/180/270
     * @param uprightWindow   直立坐标系下的目标窗口；null 表示整幅
     * @param scratch         复用缓冲，容量不足时内部新建
     * @return 提取结果；窗口无效时返回 null
     */
    @Nullable
    public static UprightY extract(@NonNull ImageProxy image, int rotationDegrees,
                                   @Nullable Rect uprightWindow, @Nullable byte[] scratch) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            return null;
        }
        ImageProxy.PlaneProxy yPlane = planes[0];
        ByteBuffer yBuffer = yPlane.getBuffer();
        if (yBuffer == null) {
            return null;
        }
        int rowStride = yPlane.getRowStride();
        int pixelStride = yPlane.getPixelStride();

        Rect crop = image.getCropRect();
        int cropW = crop.width();
        int cropH = crop.height();
        if (cropW <= 0 || cropH <= 0) {
            return null;
        }

        // 旋转后直立的整幅尺寸
        boolean swap = rotationDegrees == 90 || rotationDegrees == 270;
        int fullW = swap ? cropH : cropW;
        int fullH = swap ? cropW : cropH;

        // 直立窗口钳制到整幅范围内
        int winLeft = 0;
        int winTop = 0;
        int winW = fullW;
        int winH = fullH;
        if (uprightWindow != null) {
            winLeft = clamp(uprightWindow.left, 0, fullW - 1);
            winTop = clamp(uprightWindow.top, 0, fullH - 1);
            winW = clamp(uprightWindow.right, 0, fullW) - winLeft;
            winH = clamp(uprightWindow.bottom, 0, fullH) - winTop;
            if (winW <= 0 || winH <= 0) {
                return null;
            }
        }

        byte[] out = scratch != null && scratch.length >= winW * winH
                ? scratch : new byte[winW * winH];

        for (int y = 0; y < winH; y++) {
            // 直立坐标系的绝对 y
            int uprightY = winTop + y;
            for (int x = 0; x < winW; x++) {
                // 直立坐标系的绝对 x
                int uprightX = winLeft + x;
                int srcX;
                int srcY;
                switch (rotationDegrees) {
                    case 90:
                        srcX = crop.left + uprightY;
                        srcY = crop.top + (cropH - 1 - uprightX);
                        break;
                    case 180:
                        srcX = crop.left + (cropW - 1 - uprightX);
                        srcY = crop.top + (cropH - 1 - uprightY);
                        break;
                    case 270:
                        srcX = crop.left + (cropW - 1 - uprightY);
                        srcY = crop.top + uprightX;
                        break;
                    case 0:
                    default:
                        srcX = crop.left + uprightX;
                        srcY = crop.top + uprightY;
                        break;
                }
                out[y * winW + x] = yBuffer.get(srcY * rowStride + srcX * pixelStride);
            }
        }
        return new UprightY(out, winW, winH);
    }

    /**
     * 把视图坐标系的矩形映射到直立图像坐标系的指定区域（二者满铺对应，线性缩放）。
     *
     * @param viewRect 视图坐标系矩形（如取景框）
     * @param viewW    视图宽
     * @param viewH    视图高
     * @param region   直立图像中与视图可视内容对应的区域
     * @return 映射结果；参数非法时返回 null
     */
    @Nullable
    public static Rect mapRectToRegion(@Nullable Rect viewRect, int viewW, int viewH, @Nullable Rect region) {
        if (viewRect == null || region == null
                || viewW <= 0 || viewH <= 0 || region.width() <= 0 || region.height() <= 0) {
            return null;
        }
        float scaleX = region.width() / (float) viewW;
        float scaleY = region.height() / (float) viewH;
        int left = region.left + Math.round(viewRect.left * scaleX);
        int top = region.top + Math.round(viewRect.top * scaleY);
        int right = region.left + Math.round(viewRect.right * scaleX);
        int bottom = region.top + Math.round(viewRect.bottom * scaleY);
        // 钳制到区域内
        left = clamp(left, region.left, region.right);
        top = clamp(top, region.top, region.bottom);
        right = clamp(right, region.left, region.right);
        bottom = clamp(bottom, region.top, region.bottom);
        // 窗口过小没有解码意义
        if (right - left < 8 || bottom - top < 8) {
            Log.w(TAG, "mapRectToRegion: mapped rect too small, view=" + viewRect);
            return null;
        }
        return new Rect(left, top, right, bottom);
    }

    /**
     * 手动计算 FILL_CENTER 可视区域（直立坐标系）：ViewPort 不生效时的回退路径。
     * 以 max 缩放铺满视图后居中裁剪。
     */
    public static Rect computeFillCenterRegion(int fullW, int fullH, int viewW, int viewH) {
        float scale = Math.max(fullW / (float) viewW, fullH / (float) viewH);
        int visW = Math.min(fullW, Math.round(viewW * scale));
        int visH = Math.min(fullH, Math.round(viewH * scale));
        int left = (fullW - visW) / 2;
        int top = (fullH - visH) / 2;
        return new Rect(left, top, left + visW, top + visH);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
