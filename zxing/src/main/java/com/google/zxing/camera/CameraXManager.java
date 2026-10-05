package com.google.zxing.camera;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CameraX 门面：负责相机的绑定/解绑、手电筒与点击对焦。
 * <p>
 * Preview 与 ImageAnalysis 通过 UseCaseGroup + ViewPort（FILL_CENTER，与 PreviewView
 * 默认 scaleType 一致）绑定，保证分析帧的 cropRect 即预览可视内容，
 * 屏幕取景框只需线性缩放即可映射到分析帧坐标。
 */
public final class CameraXManager {

    private static final String TAG = CameraXManager.class.getSimpleName();

    /** 相机绑定结果回调（主线程） */
    public interface StateCallback {
        void onCameraReady();

        void onCameraError(@NonNull Exception e);
    }

    /** 对焦结果回调（主线程） */
    public interface FocusCallback {
        void onFocusFinished(boolean success);
    }

    private final Context appContext;
    private final Executor mainExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ExecutorService cameraExecutor;
    /** stop() 之后忽略迟到的异步回调 */
    private boolean stopped;

    public CameraXManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        mainExecutor = ContextCompat.getMainExecutor(appContext);
    }

    /**
     * 绑定相机并开始预览与解码。
     * 必须在主线程调用，且 previewView 已完成 layout（宽高 > 0，ViewPort 构造需要非零尺寸）。
     */
    public void start(@NonNull LifecycleOwner lifecycleOwner, @NonNull PreviewView previewView,
                      @NonNull DecodeAnalyzer analyzer, @NonNull StateCallback callback) {
        stop();
        stopped = false;
        // 解码专用单线程：保证 analyzer 串行执行（MultiFormatReader 非线程安全）
        cameraExecutor = Executors.newSingleThreadExecutor();

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(appContext);
        // 一律 addListener，禁止在主线程阻塞 get()
        future.addListener(() -> {
            if (stopped) {
                return;
            }
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = buildImageAnalysis();
                imageAnalysis.setAnalyzer(cameraExecutor, analyzer);

                // ViewPort 尺寸与 PreviewView 一致、同为 FILL_CENTER，分析帧与预览对齐
                ViewPort viewPort = new ViewPort.Builder(
                        new Rational(previewView.getWidth(), previewView.getHeight()),
                        Surface.ROTATION_0)
                        .setScaleType(ViewPort.FILL_CENTER)
                        .build();

                camera = cameraProvider.bindToLifecycle(lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        new UseCaseGroup.Builder()
                                .addUseCase(preview)
                                .addUseCase(imageAnalysis)
                                .setViewPort(viewPort)
                                .build());
                Log.i(TAG, "Camera bound, analysis resolution pending first frame");
                callback.onCameraReady();
            } catch (ExecutionException | InterruptedException | RuntimeException e) {
                Log.e(TAG, "Failed to bind camera", e);
                shutdownExecutor();
                callback.onCameraError(e);
            }
        }, mainExecutor);
    }

    /**
     * 分析用例构建：KEEP_ONLY_LATEST 串行背压 + YUV_420_888 输出 + 720p 目标分辨率。
     * <p>
     * 注：CameraX 1.5 起 per-use-case 的 Camera2Interop.Extender 已随 Builder 重构移除，
     * 剩余的 Camera2CaptureRequestConfigurator 属 CameraXConfig 全局配置，作为库不宜侵入宿主。
     * CameraX 对预览场景默认启用连续对焦，点击对焦由 CameraControl 保证，无需额外设置。
     */
    private ImageAnalysis buildImageAnalysis() {
        ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(
                        new Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build();
        return new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setResolutionSelector(resolutionSelector)
                .build();
    }

    /**
     * 解绑相机并释放线程（主线程调用，幂等）。
     * 比 bindToLifecycle 默认的 ON_STOP 更早释放，与旧版"暂停即关相机"语义一致。
     */
    public void stop() {
        stopped = true;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        camera = null;
        shutdownExecutor();
    }

    private void shutdownExecutor() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
    }

    public boolean isCameraReady() {
        return camera != null;
    }

    /** 开关手电筒。所有入口（按钮/音量键/光感）统一走这里，UI 图标由 {@link #getTorchState()} 驱动 */
    public void setTorch(boolean on) {
        Camera camera = this.camera;
        if (camera != null) {
            camera.getCameraControl().enableTorch(on);
        }
    }

    /** 是否有闪光灯硬件 */
    public boolean isTorchAvailable() {
        Camera camera = this.camera;
        return camera != null && camera.getCameraInfo().hasFlashUnit();
    }

    /** 手电筒状态（UI 的单一事实来源），相机未就绪时返回 null */
    @Nullable
    public LiveData<Integer> getTorchState() {
        Camera camera = this.camera;
        return camera != null ? camera.getCameraInfo().getTorchState() : null;
    }

    /**
     * 点击对焦：AF + AE 同时触发；使用默认 auto-cancel（约 5s 后自动取消并恢复连续对焦，
     * 不 disableAutoCancel，避免永久对焦锁重新引入"拉风箱"）。连续点击会自动替换上一次动作。
     *
     * @param factory PreviewView.getMeteringPointFactory()，内部已处理裁剪与旋转换算
     */
    public void focusAt(@NonNull MeteringPointFactory factory, float x, float y,
                        @Nullable FocusCallback callback) {
        Camera camera = this.camera;
        if (camera == null || callback == null) {
            return;
        }
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(
                point, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .build();
        ListenableFuture<FocusMeteringResult> future =
                camera.getCameraControl().startFocusAndMetering(action);
        future.addListener(() -> {
            boolean success = false;
            try {
                // 监听器触发时 future 已完成，get() 立即返回
                FocusMeteringResult result = future.get();
                if (result != null) {
                    success = result.isFocusSuccessful();
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.w(TAG, "startFocusAndMetering failed", e);
            }
            callback.onFocusFinished(success);
        }, mainExecutor);
    }
}
