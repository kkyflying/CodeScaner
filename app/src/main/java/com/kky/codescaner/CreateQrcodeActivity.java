package com.kky.codescaner;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.zxing.WriterException;
import com.google.zxing.encoding.EncodingHandler;
import com.kky.codescaner.databinding.ActivityCreateQrcodeBinding;

import java.io.UnsupportedEncodingException;

/**
 * @author :  kky
 * @time : 2020-06-27 Saturday
 */
public class CreateQrcodeActivity extends BaseActivity {

    private static final String TAG = CreateQrcodeActivity.class.getSimpleName();

    private ActivityCreateQrcodeBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateQrcodeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //根据输入内容生成二维码
        binding.btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String in = binding.input.getText().toString();
                if (!TextUtils.isEmpty(in)) {
                    try {
                        String contents = new String(in.getBytes("UTF-8"), "ISO-8859-1");
                        binding.imgQrcode.setImageBitmap(EncodingHandler.createQRCode(contents, 500));
                    } catch (WriterException | UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

}
