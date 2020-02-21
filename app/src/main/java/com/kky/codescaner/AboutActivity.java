package com.kky.codescaner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.WriterException;
import com.google.zxing.encoding.EncodingHandler;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author kky
 * @date 2020-02-21 12:42
 */
public class AboutActivity extends BaseActivity {

    @BindView(R.id.iamge1)
    ImageView iamge1;
    @BindView(R.id.iamge2)
    ImageView iamge2;
    @BindView(R.id.tvUrl)
    TextView tvUrl;
    @BindView(R.id.btnOpen)
    Button btnOpen;
    @BindView(R.id.btnShare)
    Button btnShare;

    private ClipboardManager mClipboardManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        ButterKnife.bind(this);
        init();
    }

    private void init() {
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.k);
        iamge1.setImageBitmap(EncodingHandler.createQRCode(Constant.URL_HOME, 500, 500, bitmap));
        try {
            iamge2.setImageBitmap(EncodingHandler.createQRCode(Constant.URL_BOLG, 500));
        } catch (WriterException e) {
            e.printStackTrace();
        }
        tvUrl.setText(Constant.URL_HOME);
    }

    @OnClick(R.id.btnOpen)
    public void github() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setAction("android.intent.action.VIEW");
        intent.setData(Uri.parse(Constant.URL_HOME));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        startActivity(Intent.createChooser(intent, "请选择浏览器"));
    }

    @OnClick(R.id.btnShare)
    public void onViewClickedCopy() {
        ClipData clipData = ClipData.newPlainText("code", Constant.URL_HOME);
        mClipboardManager.setPrimaryClip(clipData);
        Toast.makeText(this, "复制到粘贴板~ ", Toast.LENGTH_SHORT).show();
    }

}
