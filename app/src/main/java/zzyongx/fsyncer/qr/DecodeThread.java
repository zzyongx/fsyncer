package zzyongx.fsyncer.qr;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.encoder.*;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;

import zzyongx.fsyncer.R;

import static android.R.id.accessibilityActionContextClick;
import static android.R.id.message;

public class DecodeThread extends Thread {
  static final String TAG = DecodeThread.class.getSimpleName();
  public static final String BARCODE_BITMAP = "barcode_bitmap";

  CountDownLatch handlerInitLatch;
  boolean running = true;
  Handler mainHandler;
  Handler handler;
  Point   screenSize;
  Rect    screenRect;

  class DecodeHandler extends Handler {
    @Override
    public void handleMessage(Message message) {
      if (!running) return;
      switch (message.what) {
        case R.id.decode:
          int width = message.arg1;
          int height = message.arg2;

          Rect rect = new Rect();
          rect.left = screenRect.left * width / screenSize.x;
          rect.top = screenRect.top * height / screenSize.y;
          rect.right = screenRect.right * width / screenSize.x;
          rect.bottom = screenRect.bottom * height / screenSize.y;

          // rect = new Rect(0, 0, width, height);

          Log.d(TAG, "decoding w:" + String.valueOf(width) + ", h:" + String.valueOf(height) + " rect: " + rect.toString());

          PlanarYUVLuminanceSource source = QRCode.buildPlanarYUVLuminanceSource((byte[]) message.obj, width, height, rect);
          String c = new QRCode().decode(new BinaryBitmap(new HybridBinarizer(source)));
          Message r;
          if (c == null) {
            r = Message.obtain(mainHandler, R.id.decodeFailed);
          } else {
            r = Message.obtain(mainHandler, R.id.decodeSuccess, c);
          }
          r.setData(putQRImageToBundle(source));
          r.sendToTarget();
          break;
        case R.id.quit:
          running = false;
          Looper.myLooper().quit();
          break;
      }
    }
  }

  public DecodeThread(Handler mainHandler, Rect finderRect, Point screenSize) {
    handlerInitLatch = new CountDownLatch(1);
    this.mainHandler = mainHandler;
    this.screenRect = finderRect;
    this.screenSize = screenSize;
  }

  public Handler getHandler() {
    try {
      handlerInitLatch.await();
    } catch (InterruptedException e) {
    }
    return handler;
  }

  @Override
  public void run() {
    Looper.prepare();
    handler = new DecodeHandler();  // Handler must new when thread is run
    handlerInitLatch.countDown();
    Looper.loop();
  }

  private Bundle putQRImageToBundle(PlanarYUVLuminanceSource source) {
    int[] pixels = source.renderThumbnail();
    int width = source.getThumbnailWidth();
    int height = source.getThumbnailHeight();
    Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);

    Bundle bundle = new Bundle();
    bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
    return bundle;
  }
}
