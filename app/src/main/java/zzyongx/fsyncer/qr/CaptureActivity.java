package zzyongx.fsyncer.qr;

import java.io.OutputStream;

import android.content.Context;
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

  Rect           finderRect;
  ViewFinderView viewFinderView;
  SurfaceView    surfaceView;

  CameraManager cameraManager;
  boolean hasSurface = false;

  Handler handler = new Handler() {
    @Override
    public void handleMessage(Message message) {
      Bundle bundle = message.getData();
      byte[] compressedBitmap = bundle.getByteArray(DecodeThread.BARCODE_BITMAP);
      Bitmap barcode = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.length, null);
      // Mutable copy:
      barcode = barcode.copy(Bitmap.Config.ARGB_8888, true);

      ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
      barcodeImageView.setImageBitmap(barcode);

      switch (message.what) {
        case R.id.decodeSuccess:
          String text = (String) message.obj;
          Log.d(TAG, "receive decode result: " + text);
          TextView textView = (TextView) findViewById(R.id.capture_barcodeTextView);
          textView.setText(text);
          break;
        case R.id.decodeFailed:
          Log.d(TAG, "receve decode result failed");
          cameraManager.oneSnapshot(decodeHandler);
          break;
      }
    }
  };

  Handler      decodeHandler;
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
  }

  @Override
  protected void onResume() {
    super.onResume();

    decodeThread = new DecodeThread(handler, finderRect, getScreenSize());
    decodeThread.start();
    decodeHandler = decodeThread.getHandler();

    cameraManager = new CameraManager(this);
    viewFinderView.setVisibility(View.VISIBLE);
    
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
    cameraManager.closeDriver();
    if (!hasSurface) {
      surfaceView.getHolder().removeCallback(this);
    }
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

    Point screenResolution = new Point();
    display.getSize(screenResolution);

    return screenResolution;
  }

  void initCamera(SurfaceHolder holder) {
    if (!cameraManager.isOpen()) {
      cameraManager.openDriver(holder, decodeHandler);
    }
  }
}
