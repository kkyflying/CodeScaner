package com.google.zxing.camera;

import android.graphics.Rect;

import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * YUV 提取与坐标映射测试：四向旋转公式、rowStride padding、区域映射边界。
 * <p>
 * {@link ImageProxy} 用 JDK 动态代理实现，避免依赖 camerax 内部类型
 * （android.hardware.Image / ExifData 等在 test classpath 不可见）。
 */
@RunWith(RobolectricTestRunner.class)
public class YuvFrameExtractorTest {

    /**
     * 构造 Y 平面内容按 data[y * rowStride + x] 编码的假分析帧。
     */
    private static ImageProxy fakeImage(int width, int height, int rowStride, int rotationDegrees, Rect cropRect) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(rowStride * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < rowStride; x++) {
                // 用可区分的字节值填充（含 padding 区），例如 (2,3) → 24
                buffer.put(y * rowStride + x, (byte) (10 * y + x % 10 + 1));
            }
        }

        ClassLoader loader = YuvFrameExtractorTest.class.getClassLoader();
        Object plane = Proxy.newProxyInstance(loader,
                new Class<?>[]{ImageProxy.PlaneProxy.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRowStride": return rowStride;
                        case "getPixelStride": return 1;
                        case "getBuffer": return buffer;
                        case "toString": return "FakePlane";
                        default: return defaultProxyResult(proxy, method, args);
                    }
                });
        Object imageInfo = Proxy.newProxyInstance(loader,
                new Class<?>[]{ImageInfo.class},
                (proxy, method, args) -> {
                    if ("getRotationDegrees".equals(method.getName())) {
                        return rotationDegrees;
                    }
                    return defaultProxyResult(proxy, method, args);
                });
        return (ImageProxy) Proxy.newProxyInstance(loader,
                new Class<?>[]{ImageProxy.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getWidth": return width;
                        case "getHeight": return height;
                        case "getCropRect": return cropRect;
                        case "getPlanes": return new ImageProxy.PlaneProxy[]{(ImageProxy.PlaneProxy) plane};
                        case "getImageInfo": return imageInfo;
                        case "close": return null;
                        case "toString": return "FakeImageProxy";
                        default: return defaultProxyResult(proxy, method, args);
                    }
                });
    }

    /** 未拦截方法的兜底：equals/hashCode/toString 及产品代码不会调用的方法 */
    private static Object defaultProxyResult(Object proxy, java.lang.reflect.Method method, Object[] args) {
        String name = method.getName();
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return proxy == args[0];
        }
        // 其余（getImage、populateExifData、getTagBundle 等）产品代码不会调用
        return null;
    }

    // 便利方法：data[y * rowStride + x]
    private static byte pixelAt(ImageProxy image, int rowStride, int x, int y) {
        return image.getPlanes()[0].getBuffer().get(y * rowStride + x);
    }

    @Test
    public void extract_rotation0_withRowStridePadding() {
        // 宽 4 高 2、rowStride 8（每行 4 字节 padding），rotation 0 取整幅
        ImageProxy image = fakeImage(4, 2, 8, 0, new Rect(0, 0, 4, 2));
        YuvFrameExtractor.UprightY y = YuvFrameExtractor.extract(image, 0, null, null);
        assertEquals(4, y.width);
        assertEquals(2, y.height);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                assertEquals(pixelAt(image, 8, col, row), y.data[row * 4 + col]);
            }
        }
    }

    @Test
    public void extract_rotation90_mapsUprightCorrectly() {
        // sensor 横向 4x2，竖屏需顺时针 90°：直立尺寸 2x4
        ImageProxy image = fakeImage(4, 2, 4, 90, new Rect(0, 0, 4, 2));
        YuvFrameExtractor.UprightY y = YuvFrameExtractor.extract(image, 90, null, null);
        assertEquals(2, y.width);
        assertEquals(4, y.height);
        // 公式：srcX = crop.left + uprightY, srcY = crop.top + (cropH-1-uprightX)
        for (int uy = 0; uy < 4; uy++) {
            for (int ux = 0; ux < 2; ux++) {
                int srcX = uy;
                int srcY = 2 - 1 - ux;
                assertEquals(pixelAt(image, 4, srcX, srcY), y.data[uy * 2 + ux]);
            }
        }
    }

    @Test
    public void extract_rotation180_mapsUprightCorrectly() {
        ImageProxy image = fakeImage(4, 2, 4, 180, new Rect(0, 0, 4, 2));
        YuvFrameExtractor.UprightY y = YuvFrameExtractor.extract(image, 180, null, null);
        assertEquals(4, y.width);
        assertEquals(2, y.height);
        for (int uy = 0; uy < 2; uy++) {
            for (int ux = 0; ux < 4; ux++) {
                int srcX = 4 - 1 - ux;
                int srcY = 2 - 1 - uy;
                assertEquals(pixelAt(image, 4, srcX, srcY), y.data[uy * 4 + ux]);
            }
        }
    }

    @Test
    public void extract_rotation270_mapsUprightCorrectly() {
        ImageProxy image = fakeImage(4, 2, 4, 270, new Rect(0, 0, 4, 2));
        YuvFrameExtractor.UprightY y = YuvFrameExtractor.extract(image, 270, null, null);
        assertEquals(2, y.width);
        assertEquals(4, y.height);
        // 公式：srcX = crop.left + (cropW-1-uprightY), srcY = crop.top + uprightX
        for (int uy = 0; uy < 4; uy++) {
            for (int ux = 0; ux < 2; ux++) {
                int srcX = 4 - 1 - uy;
                int srcY = ux;
                assertEquals(pixelAt(image, 4, srcX, srcY), y.data[uy * 2 + ux]);
            }
        }
    }

    @Test
    public void extract_withWindow_returnsWindowOnly() {
        // rotation 0 下取 (1,1)-(3,3) 窗口（宽2高2），验证平移正确
        ImageProxy image = fakeImage(4, 4, 4, 0, new Rect(0, 0, 4, 4));
        Rect window = new Rect(1, 1, 3, 3);
        YuvFrameExtractor.UprightY y = YuvFrameExtractor.extract(image, 0, window, null);
        assertEquals(2, y.width);
        assertEquals(2, y.height);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                assertEquals(pixelAt(image, 4, 1 + col, 1 + row), y.data[row * 2 + col]);
            }
        }
    }

    @Test
    public void mapRectToRegion_scalesLinearly() {
        // 视图 1080x1920 映射到直立 720x1280（等比），取景框按 2/3 缩放
        Rect region = new Rect(0, 0, 720, 1280);
        Rect viewRect = new Rect(202, 622, 877, 1297);
        Rect mapped = YuvFrameExtractor.mapRectToRegion(viewRect, 1080, 1920, region);
        assertNotNull(mapped);
        assertEquals(Math.round(202 * 2f / 3), mapped.left);
        assertEquals(Math.round(622 * 2f / 3), mapped.top);
        assertEquals(Math.round(877 * 2f / 3), mapped.right);
        assertEquals(Math.round(1297 * 2f / 3), mapped.bottom);
    }

    @Test
    public void mapRectToRegion_clampsToRegion() {
        Rect region = new Rect(100, 100, 620, 1180);
        // 超出视图边界的取景框应被钳制到区域内
        Rect mapped = YuvFrameExtractor.mapRectToRegion(new Rect(0, 0, 1080, 1920), 1080, 1920, region);
        assertNotNull(mapped);
        assertEquals(100, mapped.left);
        assertEquals(100, mapped.top);
        assertEquals(620, mapped.right);
        assertEquals(1180, mapped.bottom);
    }

    @Test
    public void mapRectToRegion_invalidInput_returnsNull() {
        assertNull(YuvFrameExtractor.mapRectToRegion(null, 100, 100, new Rect(0, 0, 10, 10)));
        assertNull(YuvFrameExtractor.mapRectToRegion(new Rect(0, 0, 1, 1), 0, 100, new Rect(0, 0, 10, 10)));
        assertNull(YuvFrameExtractor.mapRectToRegion(new Rect(0, 0, 1, 1), 100, 100, null));
    }

    @Test
    public void computeFillCenterRegion_maxScaleCentered() {
        // 直立 1600x1200 对视图 800x1600：scale = max(2, 0.75) = 2 → 1600x1200 全幅即居中可视
        Rect region = YuvFrameExtractor.computeFillCenterRegion(1600, 1200, 800, 1600);
        assertEquals(1600, region.width());
        assertEquals(1200, region.height());
        assertEquals(0, region.left);
        assertEquals(0, region.top);
        // 直立 640x480 对视图 400x1200：scale = max(1.6, 0.4) = 1.6 → visH = 1920 > 480 钳到 480
        Rect region2 = YuvFrameExtractor.computeFillCenterRegion(640, 480, 400, 1200);
        assertEquals(640, region2.width());
        assertEquals(480, region2.height());
    }
}
