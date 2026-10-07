# CodeScaner

## 介绍
-  这是一个基于[zxing](https://github.com/zxing/zxing)修改后集成的库,除去一些没有使用上的文件,使这个库轻量易用.当前依赖 zxing core [3.5.4](https://github.com/zxing/zxing/releases/tag/zxing-3.5.4).
- 相机层基于 CameraX（Preview + ImageAnalysis）,支持扫码与扫描条形码,内置连续对焦、点击对焦、手电筒开关、提示音与震动反馈.
- 支持从相册图片识别二维码/条形码（`DecodingHandler`）.
- 支持生成二维码（基础/自定义边距/带Logo）与一维码（CODE_128）,示例 App 支持逐个保存到相册.
- 界面文案适配中/英/日/韩/繁（台湾、香港）.

## 环境要求
本项目于 2026-10 完成工具链升级，构建环境要求如下：

| 项目 | 要求 |
| --- | --- |
| JDK | 21 |
| Gradle | 9.8.0（项目自带 wrapper，无需单独安装） |
| Android Gradle Plugin | 9.4.1 |
| Android Studio | 需支持 AGP 9.4（Quail 4 / 2026.1.4 及以上） |
| compileSdk / targetSdk | 37 |
| minSdk（最低支持系统） | 23（Android 6.0） |

使用 Gradle wrapper 直接构建：
```
./gradlew assembleDebug   # 构建调试版 APK
./gradlew build           # 完整构建（含单元测试和 Lint）
```

## 下载使用

1. [下载apk](https://github.com/kkyflying/CodeScaner/releases) 

2. 下载编译
```
git clone https://github.com/kkyflying/CodeScaner.git
```
进入到AS , File->New->Import Module ,选择刚clone完成的目录下,导入zxing

## 如何使用
 创建二维码
```
//默认的二维码的空白边距
 image.setImageBitmap(EncodingHandler.createQRCode(url,500));
 //可以控制二维码的空白边距，第三个参数可以调整边距的大小
 image.setImageBitmap(EncodingHandler.createQRCode(url, 500,50));
```
创建有logo的二维码
```
 Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.k);
 image.setImageBitmap(EncodingHandler.createQRCode(url, 500, 500, bitmap));
```
创建一维码（CODE_128,仅支持拉丁字符,不支持中文）
```
 image.setImageBitmap(EncodingHandler.createBarcode("CodeScaner-123", 600, 300));
```
识别图片中的二维码/条形码
```
 //bitmap 需自行按 EXIF 方向摆正；未识别到返回 null
 Result result = DecodingHandler.decodeFromBitmap(bitmap);
```
启动扫码（推荐 Activity Result API）
```
 private final ActivityResultLauncher<Intent> scanLauncher =
         registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
             if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                 byte[] bytes = result.getData().getByteArrayExtra(CaptureActivity.KEY_RESULT);
                 String content = new String(bytes, StandardCharsets.UTF_8);
             }
         });

 //相机权限需自行申请后启动
 scanLauncher.launch(new Intent(this, CaptureActivity.class));
```

## 备注
- 在这个库中,已经添加上权限等之类的注册,无需在原本的项目再次添加.
- 扫码页支持音量键开关手电筒；屏幕区域点击对焦.
- 参考文章 http://www.jianshu.com/p/804f1777955d
