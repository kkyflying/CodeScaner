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

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.activity.CaptureActivity;
import com.kky.codescaner.databinding.ActivityMainBinding;

public class MainActivity extends BaseActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private ActivityMainBinding binding;

    private ClipboardManager mClipboardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        init();
    }

    private void init() {
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        binding.toolbar.inflateMenu(R.menu.activity_main_meun);
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
                ClipData clipData = ClipData.newPlainText("code", binding.tvReuslt.getText());
                mClipboardManager.setPrimaryClip(clipData);
                Toast.makeText(MainActivity.this, R.string.main_copy_success, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getPermission() {
        //第二个参数是需要申请的权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //权限还没有授予，需要在这里写申请权限的代码
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 2);
        } else {
            //权限已经被授予，在这里直接写要执行的相应方法即可
            startActivityForResult(new Intent(MainActivity.this, CaptureActivity.class), Constant.REQ_QRCODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 2) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(new Intent(MainActivity.this, CaptureActivity.class), Constant.REQ_QRCODE);
            } else {
                // Permission Denied
                Toast.makeText(MainActivity.this, R.string.main_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == Constant.REQ_QRCODE && data != null) {
            byte[] result = data.getByteArrayExtra(CaptureActivity.KEY_RESULT);
            if (result == null || result.length == 0) {
                binding.tvReuslt.setText("");
                binding.btnCopy.setVisibility(View.GONE);
                return;
            }
            String payCode = new String(result);
            binding.tvReuslt.setText(payCode);
            binding.btnCopy.setVisibility(View.VISIBLE);
        } else {
            binding.tvReuslt.setText("");
            binding.btnCopy.setVisibility(View.GONE);
        }
        super.onActivityResult(requestCode, resultCode, data);
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
