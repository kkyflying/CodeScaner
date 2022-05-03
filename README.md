# CodeScaner

## 介绍
-  这是一个基于[zxing](https://github.com/zxing/zxing) 3.3.0修改后集成的库,除去一些没有使用上的文件,使这个库轻量易用.
-  最新库的版本号为1.1
- 实现Android的扫码和创建二维码的功能.
- 实现扫描条形码
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
 iamge.setImageBitmap(EncodingHandler.createQRCode(url,500));
 //可以控制二维码的空白边距，第三个参数可以调整边距的大小
 iamge.setImageBitmap(EncodingHandler.createQRCode(url, 500,50));
```
创建有logo的二维码
```
 Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.k);
 iamge.setImageBitmap(EncodingHandler.createQRCode(url, 500, 500, bitmap));
```
启动扫码
```
 startActivityForResult(new Intent(MainActivity.this, CaptureActivity.class), REQ_QRCODE);
```

## 备注
- 在这个库中,已经添加上权限等之类的注册,无需在原本的项目再次添加.
- 参考文章 http://www.jianshu.com/p/804f1777955d



