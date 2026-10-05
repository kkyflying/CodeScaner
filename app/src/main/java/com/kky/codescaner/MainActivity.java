package com.kky.codescaner;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import com.google.zxing.Result;
import com.google.zxing.activity.CaptureActivity;
import com.google.zxing.decoding.DecodingHandler;
import com.kky.codescaner.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    /** 相机权限请求码 */
    private static final int REQUEST_CAMERA = 1;

    /** 相册识别降采样的目标最长边（px） */
    private static final int MAX_DECODE_EDGE = 1600;

    private ActivityMainBinding binding;

    private ClipboardManager mClipboardManager;

    //相册识别专用后台线程（图片解码与识别不能在主线程执行）
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();

    //系统相册选图（GetContent + image/*，Android 13+ 由系统 Photo Picker 呈现）
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    recognizeFromGallery(uri);
                }
            });

    //扫码结果回调（Activity Result API，替代已废弃的 startActivityForResult/onActivityResult）
    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    byte[] resultBytes = data.getByteArrayExtra(CaptureActivity.KEY_RESULT);
                    if (resultBytes != null && resultBytes.length > 0) {
                        binding.tvResult.setText(new String(resultBytes, StandardCharsets.UTF_8));
                        binding.btnCopy.setVisibility(View.VISIBLE);
                        return;
                    }
                }
                binding.tvResult.setText("");
                binding.btnCopy.setVisibility(View.GONE);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        init();
    }

    private void init() {
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        //菜单统一由 onCreateOptionsMenu() inflate，这里不再重复 inflateMenu
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setOnMenuItemClickListener(onMenuItemClickListener);

        //点击"scaner"按钮，先申请相机权限
        binding.btnScaner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPermission();
            }
        });

        binding.btnCreateQrcode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CreateQrcodeActivity.class));
            }
        });

        //从相册选图识别
        binding.btnGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                galleryLauncher.launch("image/*");
            }
        });

        //复制扫描结果
        binding.btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipData clipData = ClipData.newPlainText("code", binding.tvResult.getText());
                mClipboardManager.setPrimaryClip(clipData);
                Toast.makeText(MainActivity.this, R.string.main_copy_success, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //权限还没有授予，需要在这里写申请权限的代码
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            //权限已经被授予，在这里直接写要执行的相应方法即可
            scanLauncher.launch(new Intent(MainActivity.this, CaptureActivity.class));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanLauncher.launch(new Intent(MainActivity.this, CaptureActivity.class));
            } else {
                // Permission Denied
                Toast.makeText(MainActivity.this, R.string.main_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //相册图片识别：后台加载（降采样 + EXIF 摆正）→ 解码 → 主线程展示结果
    private void recognizeFromGallery(Uri uri) {
        decodeExecutor.execute(() -> {
            Bitmap bitmap = loadUprightBitmap(uri, MAX_DECODE_EDGE);
            String text = null;
            if (bitmap != null) {
                Result result = DecodingHandler.decodeFromBitmap(bitmap);
                bitmap.recycle();
                if (result != null) {
                    text = result.getText();
                }
            }
            String decoded = text;
            runOnUiThread(() -> {
                if (!TextUtils.isEmpty(decoded)) {
                    binding.tvResult.setText(decoded);
                    binding.btnCopy.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(MainActivity.this, R.string.main_recognize_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /** 加载图片并按 EXIF 方向摆正，最长边降采样到 maxEdge 附近 */
    private Bitmap loadUprightBitmap(Uri uri, int maxEdge) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, opts);
            opts.inSampleSize = computeInSampleSize(opts.outWidth, opts.outHeight, maxEdge);
            opts.inJustDecodeBounds = false;
            Bitmap bitmap;
            try (InputStream decodeIn = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(decodeIn, null, opts);
            }
            if (bitmap == null) {
                return null;
            }
            int degrees = readExifRotation(uri);
            if (degrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                bitmap.recycle();
                bitmap = rotated;
            }
            return bitmap;
        } catch (IOException | OutOfMemoryError e) {
            return null;
        }
    }

    /** 读取图片 EXIF 旋转角（相册竖拍照片通常带 90°） */
    private int readExifRotation(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return 0;
            }
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }

    /** inSampleSize 算法：降采样后最长边落在 maxEdge/2 与 maxEdge*2 之间 */
    private static int computeInSampleSize(int width, int height, int maxEdge) {
        int inSampleSize = 1;
        if (width > maxEdge || height > maxEdge) {
            final int halfWidth = width / 2;
            final int halfHeight = height / 2;
            while ((halfWidth / inSampleSize) >= maxEdge / 2 && (halfHeight / inSampleSize) >= maxEdge / 2) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @Override
    protected void onDestroy() {
        decodeExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_meun, menu);
        return true;
    }

    private final Toolbar.OnMenuItemClickListener onMenuItemClickListener = new Toolbar.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            //AGP 9 起 R 字段不再是编译期常量，需使用 if-else 替代 switch-case
            int itemId = item.getItemId();
            if (itemId == R.id.about) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            }
            return false;
        }
    };

}
