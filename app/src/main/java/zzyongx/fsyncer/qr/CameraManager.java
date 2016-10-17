package zzyongx.fsyncer.qr;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.util.Log;
import android.view.WindowManager;

import zzyongx.fsyncer.R;

final class CameraManager {
  private static final String TAG = CameraManager.class.getSimpleName();

  private Context context;
  private Camera camera;
  private Camera.CameraInfo cameraInfo;
  private Camera.PreviewCallback callback;
  private Camera.AutoFocusCallback autoFocusCallback;

  CameraManager(Context context) {
    this.context = context;
  }
  
  public synchronized boolean isOpen() {
    return camera != null;
  }

  private void openCamera() {
    camera = null;

    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
      int n = Camera.getNumberOfCameras();
      if (n == 0) {
        Log.e(TAG, "no camera!");
        return;
      }

      for (int i = 0; i < n; ++i) {
        cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(i, cameraInfo);
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
          camera = Camera.open(i);
          break;
        }
      }
    } else {
      camera = Camera.open();
    }

    if (camera == null) {
      Log.e(TAG, "open camera error");
    }
  }

  private int getOrientation8() {
    return 90;
  }

  @TargetApi(9)
  private int getOrientationGt8() {
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    int rotation = wm.getDefaultDisplay().getRotation();
    int degree;
    switch (rotation) {
      case Surface.ROTATION_0:
        degree = 0;
        break;
      case Surface.ROTATION_90:
        degree = 90;
        break;
      case Surface.ROTATION_180:
        degree = 180;
        break;
      case Surface.ROTATION_270:
        degree = 270;
        break;
      default:
        // Have seen this return incorrect values like -90
        if (rotation % 90 == 0) {
          degree = (360 + rotation) % 360;
        } else {
          throw new IllegalArgumentException("Bad rotation: " + rotation);
        }
    }
    return (360 + cameraInfo.orientation - degree) % 360;
  }

  private void setCameraOrientation() {
    if (camera == null) return;

    int orientation;

    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
      orientation = getOrientationGt8();
    } else {
      orientation = getOrientation8();
    }

    camera.setDisplayOrientation(orientation);
    Log.d(TAG, "orientation: " + String.valueOf(orientation));
  }

  void openDriver(SurfaceHolder holder, final Handler decodeHandler) {
    if (camera == null) openCamera();
    if (camera == null) {
      throw new RuntimeException("Camera.open() failed");
    }

    setCameraOrientation();

    try {
      camera.setPreviewDisplay(holder);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    camera.startPreview();

    autoFocusCallback = new Camera.AutoFocusCallback() {
      @Override
      public void onAutoFocus(boolean success, Camera camera) {
        // do nothing;
      }
    };
    camera.autoFocus(autoFocusCallback);


    callback = new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(byte[] data, Camera camera) {
        final Camera.Size size = camera.getParameters().getPreviewSize();
        Log.d(TAG, "produce decode request");
        Message message = decodeHandler.obtainMessage(R.id.decode, size.width, size.height, data);
        message.sendToTarget();
      }
    };
    camera.setOneShotPreviewCallback(callback);
  }

  void oneSnapshot() {
    if (camera != null) {
      camera.autoFocus(autoFocusCallback);
      camera.setOneShotPreviewCallback(callback);
    }
  }

  void closeDriver() {
    if (camera != null) {
      camera.stopPreview();
      camera.setOneShotPreviewCallback(null);
      camera.release();
      camera = null;
    }
  }
}
