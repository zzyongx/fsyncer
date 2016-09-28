package zzyongx.fsyncer.qr;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.EnumMap;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class QRCode {
  private static final int WHITE = 0xFFFFFFFF;
  private static final int BLACK = 0xFF000000;
  
  public static class Param {
    public int width  = 120;
    public int height = 120;
    public int margin = 1;
    public ErrorCorrectionLevel ecLevel = ErrorCorrectionLevel.L;
    public Charset outputEncoding = Charset.forName("UTF-8");
  }

  private Param p;

  public QRCode() {
    this(new Param());
  }

  public QRCode(Param param) {
    this.p = param;
  }

  public Bitmap encode(String text) {
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.MARGIN, p.margin);
    if (!Charset.forName("ISO_8859_1").equals(p.outputEncoding)) {
      // Only set if not QR code default
      hints.put(EncodeHintType.CHARACTER_SET, p.outputEncoding.name());
    }
    hints.put(EncodeHintType.ERROR_CORRECTION, p.ecLevel);
    
    BitMatrix matrix;
    try {
      matrix = new QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, p.width, p.height, hints);
    } catch (WriterException we) {
      return null;
    }

    int[] pixels = new int[p.width * p.height];
    for (int y = 0; y < p.height; y++) {
      int offset = y * p.width;
      for (int x = 0; x < p.width; x++) {
        pixels[offset + x] = matrix.get(x, y) ? BLACK : WHITE;
      }
    }

    Bitmap bitmap = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, p.width, 0, 0, p.width, p.height);
    return bitmap;
  }

  public static BinaryBitmap buildBinaryBitmap(byte[] data, int width, int height, Rect rect) {
    PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
            data, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
    return source == null ? null : new BinaryBitmap(new HybridBinarizer(source));
  }

  public String decode(BinaryBitmap bitmap) {
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.CHARACTER_SET, p.outputEncoding.name());

    try {
      Result result = new QRCodeReader().decode(bitmap, hints);
      return result == null ? null : result.getText();
    } catch (Exception e) {
      return null;
    }
  }
}
