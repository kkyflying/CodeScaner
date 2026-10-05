package com.kky.codescaner;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * @author kky
 * @date 2020-02-21 12:44
 */
public class BaseActivity extends AppCompatActivity {

    /**
     * 子页面统一 Toolbar 初始化：与主页一致的标题样式 + 返回箭头。
     *
     * @param toolbar  布局中的 Toolbar
     * @param titleRes 标题字符串资源
     */
    protected void setUpToolbar(Toolbar toolbar, int titleRes) {
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(titleRes);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_back);
        }
        //点击返回箭头结束当前页
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}
