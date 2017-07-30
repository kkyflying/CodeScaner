package com.kky.codescaner;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import com.google.zxing.activity.CaptureActivity;
import com.google.zxing.encoding.EncodingHandler;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    public static final int REQ_QRCODE = 0x5152;
    public static final String KEY_RESULT = "result";

    public static final String URL = "http://app.bilibili.com/images/down.html";

    public static final String t = "我可能复习了假书";

    @BindView(R.id.btnScaner)
    Button btnScaner;
    @BindView(R.id.tvReuslt)
    TextView tvReuslt;
    @BindView(R.id.iamge)
    ImageView iamge;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        init();
    }

    private void init(){
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.k);
            iamge.setImageBitmap(EncodingHandler.createQRCode(t,800,800, bitmap));
    }


    @OnClick(R.id.btnScaner)
    public void onViewClicked() {
        startActivityForResult(new Intent(MainActivity.this, CaptureActivity.class), REQ_QRCODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == REQ_QRCODE && data != null) {
            byte[] result = data.getByteArrayExtra(KEY_RESULT);
            if (result == null || result.length == 0) return;
            String payCode = new String(result);
            if (TextUtils.isDigitsOnly(payCode)) {
                Log.i(TAG, "onActivityResult:1 " + payCode);
                tvReuslt.setText(payCode);
            } else {
                Log.i(TAG, "onActivityResult:2 " + payCode);
                tvReuslt.setText(payCode);
            }
        }
    }


}
