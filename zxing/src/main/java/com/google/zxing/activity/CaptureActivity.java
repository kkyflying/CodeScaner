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

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.TorchState;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LiveData;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.R;
import com.google.zxing.Result;
import com.google.zxing.camera.CameraXManager;
import com.google.zxing.camera.DecodeAnalyzer;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumSet;

/**
 * This activity opens the camera and does the actual scanning on a background thread. It draws a
 * viewfinder to help the user place the barcode correctly, shows feedback as the image processing
 * is happening, and then overlays the results when a scan is successful.
 * <p>
 * 相机层基于 CameraX：预览用 PreviewView，解码由 {@link DecodeAnalyzer} 在专属后台线程完成，
 * 对焦为常态连续对焦 + 点击触发电位对焦，手电筒按钮与音量键共用同一状态源。
 */
public final class CaptureActivity extends AppCompatActivity {

    private static final String TAG = CaptureActivity.class.getSimpleName();

    public static final String KEY_RESULT = "result";

    private CameraXManager cameraManager;
    private ViewfinderView viewfinderView;
    private ImageButton torchButton;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;
    private AmbientLightManager ambientLightManager;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_capturer);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(null);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(null);
        //返回箭头与处理方式同 app 模块 BaseActivity.setUpToolbar 保持一致
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_navigation_left);
        toolbar.setNavigationOnClickListener(v -> finish());
        getSupportActionBar().setElevation(0);

        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        ambientLightManager = new AmbientLightManager(this);

        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        torchButton = (ImageButton) findViewById(R.id.torch_button);
        torchButton.setOnClickListener(v -> toggleTorch());
    }

    @Override
    protected void onResume() {
        super.onResume();

        cameraManager = new CameraXManager(getApplicationContext());

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        beepManager.updatePrefs();
        ambientLightManager.start(cameraManager);
        inactivityTimer.onResume();

        PreviewView previewView = (PreviewView) findViewById(R.id.preview_view);
        // 点击预览触发对焦（连续对焦为常态，点击做一次电位对焦 + 测光）
        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP
                    && cameraManager != null && cameraManager.isCameraReady()) {
                v.performClick();
                viewfinderView.showFocusIndicator((int) event.getX(), (int) event.getY());
                cameraManager.focusAt(previewView.getMeteringPointFactory(),
                        event.getX(), event.getY(),
                        success -> viewfinderView.updateFocusIndicator(success));
            }
            return true;
        });

        // 等 layout 完成后再绑定（ViewPort 构造需要非零尺寸；快速进出时跳过迟到的启动）
        previewView.post(() -> {
            if (cameraManager != null) {
                startCamera(previewView);
            }
        });
    }

    private void startCamera(PreviewView previewView) {
        Collection<BarcodeFormat> decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
        decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
        decodeFormats.addAll(DecodeFormatManager.ONE_D_FORMATS);

        DecodeAnalyzer decodeAnalyzer = new DecodeAnalyzer(decodeFormats,
                viewfinderView::getFramingRect, this::handleDecode);
        decodeAnalyzer.setScanRegion(previewView.getWidth(), previewView.getHeight());

        cameraManager.start(this, previewView, decodeAnalyzer, new CameraXManager.StateCallback() {
            @Override
            public void onCameraReady() {
                setupTorchButton();
            }

            @Override
            public void onCameraError(@NonNull Exception e) {
                displayFrameworkBugMessageAndExit();
            }
        });
    }

    /** 手电筒按钮：有闪光灯硬件才显示，图标由 torchState LiveData（单一事实来源）驱动 */
    private void setupTorchButton() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (!cameraManager.isTorchAvailable()) {
            torchButton.setVisibility(View.GONE);
            return;
        }
        torchButton.setVisibility(View.VISIBLE);
        LiveData<Integer> torchState = cameraManager.getTorchState();
        if (torchState != null) {
            torchState.observe(this, state -> {
                boolean on = state != null && state == TorchState.ON;
                torchButton.setActivated(on);
                torchButton.setContentDescription(
                        getString(on ? R.string.zxing_torch_on : R.string.zxing_torch_off));
            });
        }
    }

    private void toggleTorch() {
        if (cameraManager != null) {
            cameraManager.setTorch(!torchButton.isActivated());
        }
    }

    @Override
    protected void onPause() {
        // 显式解绑：比 bindToLifecycle 默认的 ON_STOP 更早释放相机，与旧版"暂停即关相机"语义一致
        if (cameraManager != null) {
            cameraManager.stop();
            cameraManager = null;
        }
        ambientLightManager.stop();
        beepManager.close();
        inactivityTimer.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                setResult(RESULT_CANCELED);
                finish();
                return true;
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_CAMERA:
                // Handle these events so they don't launch the Camera app
                return true;
            // 音量键开关手电筒（保留旧版入口，按钮图标经 LiveData 自动保持一致）
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (cameraManager != null) {
                    cameraManager.setTorch(false);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (cameraManager != null) {
                    cameraManager.setTorch(true);
                }
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * A valid barcode has been found, so give an indication of success and show the results.
     *
     * @param rawResult The contents of the barcode.
     * @param thumbnail A greyscale bitmap of the camera data which was decoded.
     */
    public void handleDecode(Result rawResult, Bitmap thumbnail) {
        inactivityTimer.onActivity();

        boolean fromLiveScan = thumbnail != null;
        if (fromLiveScan) {
            beepManager.playBeepSoundAndVibrate();
        }

        String resultString = rawResult.getText();
        //FIXME
        if (TextUtils.isEmpty(resultString)) {
            Toast.makeText(this, R.string.zxing_scan_failed, Toast.LENGTH_SHORT).show();
        } else {
            Intent resultIntent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putByteArray(KEY_RESULT, resultString.getBytes(StandardCharsets.UTF_8));
            resultIntent.putExtras(bundle);
            setResult(RESULT_OK, resultIntent);
        }
        finish();
    }

    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.zxing_dialog_title));
        builder.setMessage(getString(R.string.zxing_camera_error));
        builder.setPositiveButton(getString(R.string.zxing_dialog_ok), new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

}
