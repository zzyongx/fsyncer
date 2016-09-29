package zzyongx.fsyncer.qr;

import java.io.IOException;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.util.Log;
import android.view.WindowManager;

import zzyongx.fsyncer.R;

public final class CameraManager {
  static final String TAG = CameraManager.class.getSimpleName();

  Context context;
  Camera camera;
  Camera.CameraInfo cameraInfo;
  Camera.PreviewCallback callback;
  Camera.AutoFocusCallback autoFocusCallback;

  public CameraManager(Context context) {
    this.context = context;
  }
  
  public synchronized boolean isOpen() {
    return camera != null;
  }
  public Camera getCamera() { return camera; }

  Camera openCamera() {
    int n = Camera.getNumberOfCameras();
    if (n == 0) {
      Log.e(TAG, "no camera!");
      return null;
    }

    Camera camera = null;

    for (int i = 0; i < n; ++i) {
      cameraInfo = new Camera.CameraInfo();
      Camera.getCameraInfo(i, cameraInfo);
      if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
        camera = Camera.open(i);
        break;
      }
    }

    if (camera == null) {
      Log.e(TAG, "open camera error");
    }
    
    return camera;
  }

  void setCameraOrientation(Camera camera, Camera.CameraInfo cameraInfo) {
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
      default:
        // Have seen this return incorrect values like -90
        if (rotation % 90 == 0) {
          degree = (360 + rotation) % 360;
        } else {
          throw new IllegalArgumentException("Bad rotation: " + rotation);
        }
    }

    int orientation = (360 + cameraInfo.orientation - degree) % 360;
    camera.setDisplayOrientation(orientation);
  }

  public void openDriver(SurfaceHolder holder, final Handler decodeHandler) {
    if (camera == null) camera = openCamera();
    if (camera == null) {
      throw new RuntimeException("Camera.open() failed");
    }

    setCameraOrientation(camera, cameraInfo);
    camera.startPreview();

    try {
      camera.setPreviewDisplay(holder);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

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

  public void oneSnapshot(final Handler decodeHandler) {
    if (camera != null) {
      camera.autoFocus(autoFocusCallback);
      camera.setOneShotPreviewCallback(callback);
    }
  }

  public void closeDriver() {
    if (camera != null) {
      camera.release();
      camera = null;
    }
  }

  public Point getSnapshotSize() {
    Camera.Size size = camera.getParameters().getPictureSize();
    return new Point(size.width, size.height);
  }


}
