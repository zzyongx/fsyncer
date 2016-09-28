package zzyongx.fsyncer.qr;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.qrcode.encoder.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;

import zzyongx.fsyncer.R;

import static android.R.id.message;

public class DecodeThread extends Thread {
  static final String TAG = DecodeThread.class.getSimpleName();

  CountDownLatch handlerInitLatch;
  boolean running = true;
  Handler mainHandler;
  Handler handler;
  Rect    rect;

  class DecodeHandler extends Handler {
    @Override
    public void handleMessage(Message message) {
      if (!running) return;
      switch (message.what) {
        case R.id.decode:
          String c = decode((byte[]) message.obj, message.arg1, message.arg2);
          Message r;
          if (c == null) {
            r = Message.obtain(mainHandler, R.id.decodeFailed);
          } else {
            r = Message.obtain(mainHandler, R.id.decodeSuccess, c);
          }
          r.sendToTarget();
          break;
        case R.id.quit:
          running = false;
          Looper.myLooper().quit();
          break;
      }
    }
  }

  public DecodeThread(Handler mainHandler, Rect finderRect) {
    handlerInitLatch = new CountDownLatch(1);
    this.mainHandler = mainHandler;
    this.rect = finderRect;
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

  private String decode(byte[] data, int width, int height) {
    Log.d(TAG, "decoding");

    BinaryBitmap binaryBitmap = QRCode.buildBinaryBitmap(data, width, height, rect);
    return new QRCode().decode(binaryBitmap);
  }
}
