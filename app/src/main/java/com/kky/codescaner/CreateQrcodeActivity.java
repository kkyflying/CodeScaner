package com.kky.codescaner;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.zxing.WriterException;
import com.google.zxing.encoding.EncodingHandler;

import java.io.UnsupportedEncodingException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

/**
 * @author :  kky
 * @time : 2020-06-27 Saturday
 */
public class CreateQrcodeActivity extends BaseActivity {

    private static final String TAG = CreateQrcodeActivity.class.getSimpleName();

    @BindView(R.id.input)
    EditText input;
    @BindView(R.id.btnCreate)
    Button btnCreate;
    @BindView(R.id.imgQrcode)
    ImageView imgQrcode;

    private Unbinder unbinder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_qrcode);
        unbinder = ButterKnife.bind(this);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
    }


    @OnClick(R.id.btnCreate)
    public void onViewClicked() {
        String in = input.getText().toString();
        if (!TextUtils.isEmpty(in)){
            try {
                String contents = new String(in.getBytes("UTF-8"), "ISO-8859-1");
                imgQrcode.setImageBitmap(EncodingHandler.createQRCode(contents, 500));
            } catch (WriterException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

}
