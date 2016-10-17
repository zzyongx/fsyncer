package zzyongx.fsyncer.qr;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import zzyongx.fsyncer.R;

class DecodeThread extends Thread {
  private static final String TAG = DecodeThread.class.getSimpleName();
  static final String BARCODE_BITMAP = "barcode_bitmap";

  private CountDownLatch handlerInitLatch;
  private boolean running = true;
  private Handler mainHandler;
  private Handler handler;

  @SuppressLint("HandlerLeak")
  private class DecodeHandler extends Handler {
    @Override
    public void handleMessage(Message message) {
      if (!running) return;
      switch (message.what) {
        case R.id.decode:
          int width = message.arg1;
          int height = message.arg2;

          Rect rect = new Rect(0, 0, width, height);
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

  DecodeThread(Handler mainHandler) {
    handlerInitLatch = new CountDownLatch(1);
    this.mainHandler = mainHandler;
  }

  Handler getHandler() {
    try {
      handlerInitLatch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
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
