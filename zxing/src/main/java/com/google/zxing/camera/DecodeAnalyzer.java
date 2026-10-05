package com.google.zxing.camera;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 扫码分析器：在 CameraX 的专属后台线程上直接解码。
 * <p>
 * 配合 {@code STRATEGY_KEEP_ONLY_LATEST}：上一帧 analyze() 返回（image.close()）
 * 后才会投递下一帧，天然串行背压，等价于旧版"解完一帧再请求下一帧"的循环。
 * {@code MultiFormatReader} 仅在此线程构造与访问，跨帧复用（zxing 非线程安全）。
 */
public final class DecodeAnalyzer implements ImageAnalysis.Analyzer {

    private static final String TAG = DecodeAnalyzer.class.getSimpleName();

    /** 解码成功回调（主线程） */
    public interface Listener {
        void onDecoded(@NonNull Result result, @NonNull Bitmap thumbnail);
    }

    /** 取景框提供者（屏幕坐标系），避免 camera 包反向依赖 activity 包 */
    public interface FramingRectProvider {
        Rect getFramingRect();
    }

    /** ViewPort 宽高比校验容差 */
    private static final double ASPECT_TOLERANCE = 0.02;

    private final MultiFormatReader reader = new MultiFormatReader();
    private final FramingRectProvider framingRectProvider;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** 已成功标志：finish 前只排空帧、不重复回调 */
    private final AtomicBoolean decoded = new AtomicBoolean(false);
    /** 帧数据复用缓冲（尺寸由 extractor 按需扩容） */
    private byte[] scratch;

    /** 预览视图尺寸（与 ViewfinderView 同为 match_parent） */
    private int viewWidth;
    private int viewHeight;

    public DecodeAnalyzer(@NonNull Collection<BarcodeFormat> decodeFormats,
                          @NonNull FramingRectProvider framingRectProvider,
                          @NonNull Listener listener) {
        this.framingRectProvider = framingRectProvider;
        this.listener = listener;
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
        reader.setHints(hints);
    }

    /** 设置预览视图尺寸（屏幕取景框→分析帧映射的基准） */
    public void setScanRegion(int viewWidth, int viewHeight) {
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (decoded.get()) {
                return;
            }
            Rect framingRect = framingRectProvider.getFramingRect();
            if (framingRect == null || viewWidth <= 0 || viewHeight <= 0) {
                return;
            }

            int rotation = image.getImageInfo().getRotationDegrees();
            Rect sensorCrop = image.getCropRect();
            // 旋转后直立的整幅尺寸
            boolean swap = rotation == 90 || rotation == 270;
            int fullW = swap ? sensorCrop.height() : sensorCrop.width();
            int fullH = swap ? sensorCrop.width() : sensorCrop.height();

            // ViewPort 生效时直立整幅即预览可视内容；宽高比偏差过大视为个别 HAL
            // 未应用 ViewPort（cropRect 返回全幅），回退手动 FILL_CENTER 数学
            Rect visibleRegion;
            if (isViewportTrusted(fullW, fullH)) {
                visibleRegion = new Rect(0, 0, fullW, fullH);
            } else {
                visibleRegion = YuvFrameExtractor.computeFillCenterRegion(fullW, fullH, viewWidth, viewHeight);
            }

            // 屏幕取景框 → 直立图像坐标的解码窗口
            Rect scanRect = YuvFrameExtractor.mapRectToRegion(framingRect, viewWidth, viewHeight, visibleRegion);
            if (scanRect == null) {
                return;
            }

            YuvFrameExtractor.UprightY y = YuvFrameExtractor.extract(image, rotation, scanRect, scratch);
            if (y == null) {
                return;
            }
            scratch = y.data;

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    y.data, y.width, y.height, 0, 0, y.width, y.height, false);
            try {
                Result result = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
                if (decoded.compareAndSet(false, true)) {
                    Bitmap thumbnail = renderThumbnail(source);
                    main.post(() -> listener.onDecoded(result, thumbnail));
                }
            } catch (ReaderException e) {
                // 常规解码失败：静默等待下一帧
            } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                // 个别机型的畸形帧数据：按失败处理，避免崩溃
                Log.w(TAG, "Malformed frame data", e);
            } finally {
                reader.reset();
            }
        } finally {
            // KEEP_ONLY_LATEST 下漏 close 会导致帧流停摆，必须兜底
            image.close();
        }
    }

    /** ViewPort 是否可信：直立整幅与视图宽高比偏差在容差内 */
    private boolean isViewportTrusted(int fullW, int fullH) {
        double imageAspect = fullW / (double) fullH;
        double viewAspect = viewWidth / (double) viewHeight;
        boolean trusted = Math.abs(imageAspect / viewAspect - 1.0) <= ASPECT_TOLERANCE;
        if (!trusted) {
            Log.w(TAG, "ViewPort aspect mismatch, fallback to manual FILL_CENTER"
                    + ", image=" + fullW + "x" + fullH + ", view=" + viewWidth + "x" + viewHeight);
        }
        return trusted;
    }

    /** 亮度缩略图（复用旧版 thumbnail 语义，供 beep 判断 live scan 等使用） */
    private static Bitmap renderThumbnail(PlanarYUVLuminanceSource source) {
        int[] pixels = source.renderThumbnail();
        int width = source.getThumbnailWidth();
        int height = source.getThumbnailHeight();
        return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
    }
}
