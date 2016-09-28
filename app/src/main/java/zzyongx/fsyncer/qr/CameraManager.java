package zzyongx.fsyncer.qr;

import java.io.IOException;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import android.util.Log;

import zzyongx.fsyncer.R;

public final class CameraManager {
  static final String TAG = CameraManager.class.getSimpleName();

  Context context;
  Camera camera;
  Camera.PreviewCallback callback;

  public CameraManager(Context context) {
    this.context = context;
  }
  
  public synchronized boolean isOpen() {
    return camera != null;
  }
  public Camera getCamera() { return camera; }

  static Camera openCamera() {
    int n = Camera.getNumberOfCameras();
    if (n == 0) {
      Log.e(TAG, "no camera!");
      return null;
    }

    Camera camera = null;

    for (int i = 0; i < n; ++i) {
      Camera.CameraInfo info = new Camera.CameraInfo();
      Camera.getCameraInfo(i, info);
      if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
        camera = Camera.open(i);
        break;
      }
    }

    if (camera == null) {
      Log.e(TAG, "open camera error");
    }
    
    return camera;
  }

  public void openDriver(SurfaceHolder holder, final Handler decodeHandler) {
    if (camera == null) camera = openCamera();
    if (camera == null) {
      throw new RuntimeException("Camera.open() failed");
    }
    camera.setDisplayOrientation(90);
    camera.startPreview();
    try {
      camera.setPreviewDisplay(holder);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    final Camera.Size size = camera.getParameters().getPictureSize();

    callback = new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(byte[] data, Camera camera) {
        Log.d(TAG, "produce decode request");
        Message message = decodeHandler.obtainMessage(R.id.decode, size.width, size.height, data);
        message.sendToTarget();
      }
    };
    camera.setOneShotPreviewCallback(callback);
  }

  public void oneSnapshot(final Handler decodeHandler) {
    camera.setOneShotPreviewCallback(callback);
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
