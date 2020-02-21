package com.kky.codescaner;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.activity.CaptureActivity;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends BaseActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @BindView(R.id.btnScaner)
    Button btnScaner;
    @BindView(R.id.tvReuslt)
    TextView tvReuslt;
    @BindView(R.id.btnCopy)
    Button btnCopy;
    @BindView(R.id.toolbar)
    Toolbar toolbar;

    private ClipboardManager mClipboardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        init();
    }

    private void init() {
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        toolbar.inflateMenu(R.menu.activity_main_meun);
        setSupportActionBar(toolbar);
        toolbar.setOnMenuItemClickListener(onMenuItemClickListener);
    }


    @OnClick(R.id.btnScaner)
    public void onViewClickedScaner() {
        getPermission();
    }

    @OnClick(R.id.btnCopy)
    public void onViewClickedCopy() {
        ClipData clipData = ClipData.newPlainText("code", tvReuslt.getText());
        mClipboardManager.setPrimaryClip(clipData);
        Toast.makeText(this, "copy ~ ", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == Constant.REQ_QRCODE && data != null) {
            byte[] result = data.getByteArrayExtra(CaptureActivity.KEY_RESULT);
            if (result == null || result.length == 0) {
                tvReuslt.setText("");
                btnCopy.setVisibility(View.GONE);
                return;
            }
            String payCode = new String(result);
            tvReuslt.setText(payCode);
            btnCopy.setVisibility(View.VISIBLE);
        } else {
            tvReuslt.setText("");
            btnCopy.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_meun,menu);
        return true;
    }

    private Toolbar.OnMenuItemClickListener onMenuItemClickListener = new Toolbar.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.about:
                    startActivity(new Intent(MainActivity.this, AboutActivity.class));
                    break;
            }
            return false;
        }
    };

}
