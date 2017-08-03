package com.kky.codescaner;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.WriterException;
import com.google.zxing.activity.CaptureActivity;
import com.google.zxing.encoding.EncodingHandler;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    public static final int REQ_QRCODE = 0x1111;


    public static final String url= "https://github.com/kkyflying/CodeScaner";

    @BindView(R.id.btnScaner)
    Button btnScaner;
    @BindView(R.id.tvReuslt)
    TextView tvReuslt;
    @BindView(R.id.iamge1)
    ImageView iamge1;
    @BindView(R.id.iamge2)
    ImageView iamge2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        init();
    }

    private void init() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.k);
        iamge1.setImageBitmap(EncodingHandler.createQRCode(url, 500, 500, bitmap));

        try {
            iamge2.setImageBitmap(EncodingHandler.createQRCode(url,500));
        } catch (WriterException e) {
            e.printStackTrace();
        }

    }


    @OnClick(R.id.btnScaner)
    public void onViewClicked() {
        startActivityForResult(new Intent(MainActivity.this, CaptureActivity.class), REQ_QRCODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == REQ_QRCODE && data != null) {
            byte[] result = data.getByteArrayExtra(CaptureActivity.KEY_RESULT);
            if (result == null || result.length == 0) return;
            String payCode = new String(result);
            tvReuslt.setText(payCode);
        }
    }


}
