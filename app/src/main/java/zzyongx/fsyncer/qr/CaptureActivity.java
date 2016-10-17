package zzyongx.fsyncer.qr;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import zzyongx.fsyncer.R;

public class CaptureActivity extends AppCompatActivity implements SurfaceHolder.Callback {
  static final String TAG = CaptureActivity.class.getSimpleName();
  public static final String QRCODE = "QRCODE";

  Rect finderRect;
  ViewFinderView viewFinderView;
  SurfaceView surfaceView;

  CameraManager cameraManager;
  boolean hasSurface = false;

  static class MainHandler extends Handler {
    private CaptureActivity outer;

    MainHandler(CaptureActivity outer) {
      this.outer = outer;
    }

    @Override
    public void handleMessage(Message message) {
      Intent i = outer.getIntent();
      if (i == null) {
        Bundle bundle = message.getData();
        byte[] compressedBitmap = bundle.getByteArray(DecodeThread.BARCODE_BITMAP);
        assert compressedBitmap != null;
        Bitmap barcode = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.length, null);
        // Mutable copy:
        barcode = barcode.copy(Bitmap.Config.ARGB_8888, true);

        ImageView barcodeImageView = (ImageView) outer.findViewById(R.id.barcode_image_view);
        assert barcodeImageView != null;
        barcodeImageView.setImageBitmap(barcode);
      }

      switch (message.what) {
        case R.id.decodeSuccess:
          String text = (String) message.obj;
          Log.d(TAG, "receive decode result: " + text);
          TextView textView = (TextView) outer.findViewById(R.id.capture_barcodeTextView);
          assert textView != null;
          textView.setText(text);
          Message r = Message.obtain(outer.decodeHandler, R.id.quit);
          r.sendToTarget();

          if (i != null) {
            Intent intent = new Intent();
            intent.putExtra(CaptureActivity.QRCODE, text);
            outer.setResult(CaptureActivity.RESULT_OK, intent);
            outer.finish();
          }
          break;
        case R.id.decodeFailed:
          Log.d(TAG, "receve decode result failed");
          outer.cameraManager.oneSnapshot();
          break;
      }
    }
  }

  Handler handler;
  Handler decodeHandler;
  DecodeThread decodeThread;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

    setContentView(R.layout.activity_capture);

    viewFinderView = (ViewFinderView) findViewById(R.id.capture_viewFinderView);
    viewFinderView.setFinderRect(getFinderRect());
    surfaceView = (SurfaceView) findViewById(R.id.capture_preview);

    handler = new MainHandler(this);

    decodeThread = new DecodeThread(handler);
    decodeThread.start();
    decodeHandler = decodeThread.getHandler();

    cameraManager = new CameraManager(this);
    viewFinderView.setVisibility(View.VISIBLE);
  }

  @Override
  protected void onResume() {
    super.onResume();

    SurfaceHolder holder = surfaceView.getHolder();
    holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

    if (hasSurface) {
      initCamera(holder);
    } else {
      holder.addCallback(this);
    }
  }

  @Override
  protected void onPause() {
    if (!hasSurface) {
      surfaceView.getHolder().removeCallback(this);
    }
    cameraManager.closeDriver();
    super.onPause();
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    Log.e(TAG, "surfaceCreated");

    if (!hasSurface) {
      hasSurface = true;
      initCamera(holder);
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    hasSurface = false;
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
  }

  public Rect getFinderRect() {
    if (finderRect != null) return finderRect;

    Point screenResolution = getScreenSize();
    Log.d(TAG, "screen resolution " + screenResolution.toString());

    int width = screenResolution.x / 2;
    int height = screenResolution.y / 3;
    int left = (screenResolution.x - width) / 2;
    int top = (screenResolution.y - height) / 3;

    finderRect = new Rect(left, top, left + width, top + height);
    return finderRect;
  }

  public Point getScreenSize() {
    WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();

    int w = display.getWidth();
    int h = display.getHeight();

    return new Point(w, h);
  }

  void initCamera(SurfaceHolder holder) {
    if (!cameraManager.isOpen()) {
      cameraManager.openDriver(holder, decodeHandler);
    }
  }
}
