package com.kky.codescaner;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.WriterException;
import com.google.zxing.encoding.EncodingHandler;
import com.kky.codescaner.databinding.ActivityCreateQrcodeBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 创建页：输入内容后一次性生成一维码与三种样式的二维码
 * （基础 / 自定义边距 / 带 Logo，后两者生成方式与 AboutActivity 一致），
 * 每个码左侧展示、右侧提供保存到相册按钮。
 *
 * @author :  kky
 * @time : 2020-06-27 Saturday
 */
public class CreateQrcodeActivity extends BaseActivity {

    private static final String TAG = CreateQrcodeActivity.class.getSimpleName();

    /** 存储权限请求码（仅 API 23-28 保存到相册需要） */
    private static final int REQUEST_STORAGE = 2;

    /** 二维码生成尺寸（px，即保存原图的分辨率，与 AboutActivity 一致） */
    private static final int QR_SIZE = 500;
    /** 自定义边距版二维码的边距（px，与 AboutActivity 一致） */
    private static final int QR_PADDING = 50;
    /** 一维码生成尺寸（px） */
    private static final int BARCODE_WIDTH = 600;
    private static final int BARCODE_HEIGHT = 300;

    /** 保存到相册的子目录名 */
    private static final String GALLERY_DIR = "CodeScaner";

    private ActivityCreateQrcodeBinding binding;

    //权限批准后待保存的图片（仅 API 23-28 权限流使用）
    private Bitmap pendingSaveBitmap;
    private String pendingSaveName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateQrcodeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpToolbar(binding.toolbar, R.string.main_btn_create_qrcode);

        //根据输入内容一次性生成一维码 + 三种二维码
        binding.btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard();
                generateAll(binding.input.getText().toString().trim());
            }
        });

        //四个保存按钮，各自保存对应 ImageView 中的生成图
        binding.btnSaveBarcode.setOnClickListener(v -> saveFromImageView(binding.imgBarcode, "barcode"));
        binding.btnSaveBasic.setOnClickListener(v -> saveFromImageView(binding.imgBasic, "qrcode_basic"));
        binding.btnSavePadding.setOnClickListener(v -> saveFromImageView(binding.imgPadding, "qrcode_padding"));
        binding.btnSaveLogo.setOnClickListener(v -> saveFromImageView(binding.imgLogo, "qrcode_logo"));

        //进入页面自动聚焦输入框并弹出键盘，方便直接输入
        binding.input.post(() -> {
            binding.input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /** 收起软键盘 */
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.btnCreate.getWindowToken(), 0);
        }
    }

    private void generateAll(String content) {
        if (TextUtils.isEmpty(content)) {
            return;
        }
        //1. 一维码（CODE_128 仅支持拉丁字符，中文等内容会失败；失败时该行保持隐藏）
        try {
            binding.imgBarcode.setImageBitmap(
                    EncodingHandler.createBarcode(content, BARCODE_WIDTH, BARCODE_HEIGHT));
            binding.rowBarcode.setVisibility(View.VISIBLE);
        } catch (WriterException | IllegalArgumentException e) {
            binding.rowBarcode.setVisibility(View.GONE);
            Toast.makeText(this, R.string.create_barcode_unsupported, Toast.LENGTH_SHORT).show();
        }
        //2. 基础二维码
        try {
            binding.imgBasic.setImageBitmap(EncodingHandler.createQRCode(content, QR_SIZE));
            binding.rowBasic.setVisibility(View.VISIBLE);
        } catch (WriterException e) {
            Log.w(TAG, "createQRCode basic failed", e);
            binding.rowBasic.setVisibility(View.GONE);
        }
        //3. 自定义边距二维码（生成方式参考 AboutActivity）
        try {
            binding.imgPadding.setImageBitmap(EncodingHandler.createQRCode(content, QR_SIZE, QR_PADDING));
            binding.rowPadding.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.w(TAG, "createQRCode padding failed", e);
            binding.rowPadding.setVisibility(View.GONE);
        }
        //4. 带 Logo 二维码（生成方式参考 AboutActivity：H 级容错保证可扫）
        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.k);
        binding.imgLogo.setImageBitmap(EncodingHandler.createQRCode(content, QR_SIZE, QR_SIZE, logo));
        binding.rowLogo.setVisibility(View.VISIBLE);
    }

    /** 从 ImageView 提取生成图并保存到相册 */
    private void saveFromImageView(ImageView imageView, String namePrefix) {
        Drawable drawable = imageView.getDrawable();
        if (!(drawable instanceof BitmapDrawable)) {
            Toast.makeText(this, R.string.create_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        saveToGallery(((BitmapDrawable) drawable).getBitmap(),
                "CodeScaner_" + namePrefix + "_" + System.currentTimeMillis() + ".png");
    }

    /**
     * 保存到系统相册：API 29+ 走 MediaStore（无需权限），
     * 旧版本写公共目录并需要运行时存储权限。
     */
    private void saveToGallery(Bitmap bitmap, String fileName) {
        if (bitmap == null) {
            Toast.makeText(this, R.string.create_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(bitmap, fileName);
        } else if (hasStoragePermission()) {
            saveViaLegacyFile(bitmap, fileName);
        } else {
            //记住待保存内容，授权成功后继续保存
            pendingSaveBitmap = bitmap;
            pendingSaveName = fileName;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    /** API 29+：MediaStore 插入，IS_PENDING 流程保证相册不会看到半成品 */
    private void saveViaMediaStore(Bitmap bitmap, String fileName) {
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + GALLERY_DIR);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore insert returned null");
            }
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new IOException("bitmap compress failed");
                }
            }
            //写入完成，发布给相册
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            Toast.makeText(this, R.string.create_save_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            //部分定制系统可能抛 SecurityException 等，统一按失败处理并记录日志
            Log.e(TAG, "saveViaMediaStore failed", e);
            if (uri != null) {
                //清理半成品记录
                getContentResolver().delete(uri, null, null);
            }
            Toast.makeText(this, R.string.create_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** API 23-28：写公共图片目录 + 通知相册扫描 */
    private void saveViaLegacyFile(Bitmap bitmap, String fileName) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), GALLERY_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("mkdir failed: " + dir);
            }
            File file = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new IOException("bitmap compress failed");
                }
            }
            //通知相册扫描新文件
            MediaScannerConnection.scanFile(this,
                    new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
            Toast.makeText(this, R.string.create_save_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "saveViaLegacyFile failed", e);
            Toast.makeText(this, R.string.create_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && pendingSaveBitmap != null) {
                saveViaLegacyFile(pendingSaveBitmap, pendingSaveName);
            } else {
                Toast.makeText(this, R.string.create_save_failed, Toast.LENGTH_SHORT).show();
            }
            pendingSaveBitmap = null;
            pendingSaveName = null;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}
