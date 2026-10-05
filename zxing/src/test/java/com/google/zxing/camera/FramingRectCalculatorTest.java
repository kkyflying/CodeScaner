package com.google.zxing.camera;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 取景框计算测试：5/8 边长、[240, 675] 钳制、居中正方形。
 */
@RunWith(RobolectricTestRunner.class)
public class FramingRectCalculatorTest {

    @Test
    public void compute_commonPhoneSize_clampsToMaxAndCenters() {
        // 1080x1920：5/8 → 675/1200，均钳到上限 675，居中正方形
        Rect rect = FramingRectCalculator.compute(1080, 1920);
        assertNotNull(rect);
        assertEquals(675, rect.width());
        assertEquals(675, rect.height());
        assertEquals((1080 - 675) / 2, rect.left);
        assertEquals((1920 - 675) / 2, rect.top);
    }

    @Test
    public void compute_smallScreen_clampsToMin() {
        // 320x480：5/8 → 200/300，宽钳到下限 240；短边 240 决定正方形
        Rect rect = FramingRectCalculator.compute(320, 480);
        assertNotNull(rect);
        assertEquals(240, rect.width());
        assertEquals(240, rect.height());
        assertEquals((320 - 240) / 2, rect.left);
        assertEquals((480 - 240) / 2, rect.top);
    }

    @Test
    public void compute_intermediateSize_keepsFiveEighthsOfShortSide() {
        // 800x600：5/8 → 500/375，短边 375 未触钳制
        Rect rect = FramingRectCalculator.compute(800, 600);
        assertNotNull(rect);
        assertEquals(375, rect.width());
        assertEquals(375, rect.height());
    }

    @Test
    public void compute_invalidSize_returnsNull() {
        assertNull(FramingRectCalculator.compute(0, 100));
        assertNull(FramingRectCalculator.compute(100, 0));
        assertNull(FramingRectCalculator.compute(-5, 100));
    }
}
