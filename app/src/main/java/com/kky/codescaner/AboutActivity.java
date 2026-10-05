package com.kky.codescaner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.zxing.WriterException;
import com.google.zxing.encoding.EncodingHandler;
import com.kky.codescaner.databinding.ActivityAboutBinding;

/**
 * @author kky
 * @date 2020-02-21 12:42
 */
public class AboutActivity extends BaseActivity {

    private ActivityAboutBinding binding;

    private ClipboardManager mClipboardManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        init();
    }

    private void init() {
        setUpToolbar(binding.toolbar, R.string.menu_about);
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.k);
        binding.iamge1.setImageBitmap(EncodingHandler.createQRCode(Constant.URL_HOME, 500, 500, bitmap));
        try {
            /**
             *修改二维码的空白距离
             */
            binding.iamge2.setImageBitmap(EncodingHandler.createQRCode(Constant.URL_BOLG, 500, 50));
        } catch (WriterException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        binding.tvUrl.setText(Constant.URL_HOME);
        binding.tvVersion.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));

        //打开项目主页
        binding.btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                github();
            }
        });

        //复制项目主页地址
        binding.btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipData clipData = ClipData.newPlainText("code", Constant.URL_HOME);
                mClipboardManager.setPrimaryClip(clipData);
                Toast.makeText(AboutActivity.this, R.string.about_copy_success, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void github() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setAction("android.intent.action.VIEW");
        intent.setData(Uri.parse(Constant.URL_HOME));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        startActivity(Intent.createChooser(intent, getString(R.string.about_choose_browser)));
    }

}
