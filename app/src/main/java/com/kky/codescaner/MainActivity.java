package com.kky.codescaner;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

import com.google.zxing.activity.CaptureActivity;
import com.kky.codescaner.databinding.ActivityMainBinding;

import java.nio.charset.StandardCharsets;

public class MainActivity extends BaseActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    /** 相机权限请求码 */
    private static final int REQUEST_CAMERA = 1;

    private ActivityMainBinding binding;

    private ClipboardManager mClipboardManager;

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
