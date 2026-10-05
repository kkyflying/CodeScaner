package com.google.zxing.decoding;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * 从图片识别二维码/条形码，与 {@link com.google.zxing.encoding.EncodingHandler} 对应的解码入口。
 * 适用于相册图片等静态 Bitmap 场景。
 */
public final class DecodingHandler {

    /** 支持的格式：二维码 + 常见一维条形码 */
    private static final Collection<BarcodeFormat> FORMATS;

    static {
        EnumSet<BarcodeFormat> set = EnumSet.of(BarcodeFormat.QR_CODE);
        set.add(BarcodeFormat.DATA_MATRIX);
        set.add(BarcodeFormat.AZTEC);
        set.add(BarcodeFormat.PDF_417);
        set.add(BarcodeFormat.EAN_13);
        set.add(BarcodeFormat.EAN_8);
        set.add(BarcodeFormat.UPC_A);
        set.add(BarcodeFormat.UPC_E);
        set.add(BarcodeFormat.CODE_128);
        set.add(BarcodeFormat.CODE_39);
        set.add(BarcodeFormat.CODE_93);
        set.add(BarcodeFormat.ITF);
        set.add(BarcodeFormat.CODABAR);
        FORMATS = Collections.unmodifiableCollection(set);
    }

    private DecodingHandler() {
    }

    /**
     * 识别 Bitmap 中的条码。
     *
     * @param bitmap 已按方向摆正的图片
     * @return 识别结果；未识别到或图片非法返回 null
     */
    public static Result decodeFromBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        MultiFormatReader reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, FORMATS);
        // 静态图识别不追求速度，开启 TRY_HARDER 提高旋转/低质量图片的识别率
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
        try {
            return reader.decodeWithState(
                    new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))));
        } catch (ReaderException e) {
            // NotFoundException 等常规失败：图片中没有可识别的条码
            return null;
        } finally {
            reader.reset();
        }
    }

}
