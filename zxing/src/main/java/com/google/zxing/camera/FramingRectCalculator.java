package com.google.zxing.camera;

import android.graphics.Rect;

/**
 * 取景框计算纯函数。
 * <p>
 * 算法与旧 {@code CameraManager.getFramingRect()} 等价：目标为视图短边的 5/8，
 * 钳制在 [240, 675] px 区间，居中放置的正方形。
 */
public final class FramingRectCalculator {

    private static final int MIN_FRAME_WIDTH = 240;
    private static final int MAX_FRAME_WIDTH = 675;

    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_HEIGHT = 675;

    private FramingRectCalculator() {
    }

    /**
     * 根据视图尺寸计算居中正方形取景框。
     *
     * @param viewWidth  视图宽度（px）
     * @param viewHeight 视图高度（px）
     * @return 取景框；尺寸非法时返回 null
     */
    public static Rect compute(int viewWidth, int viewHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return null;
        }
        int width = findDesiredDimensionInRange(viewWidth, MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
        int height = findDesiredDimensionInRange(viewHeight, MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);
        // 取两者较小值保证正方形
        int min = Math.min(width, height);
        int leftOffset = (viewWidth - min) / 2;
        int topOffset = (viewHeight - min) / 2;
        return new Rect(leftOffset, topOffset, leftOffset + min, topOffset + min);
    }

    private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
        // 目标取各维度的 5/8
        int dim = 5 * resolution / 8;
        if (dim < hardMin) {
            return hardMin;
        }
        if (dim > hardMax) {
            return hardMax;
        }
        return dim;
    }
}
