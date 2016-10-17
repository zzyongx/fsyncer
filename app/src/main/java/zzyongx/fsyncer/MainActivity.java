package zzyongx.fsyncer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.widget.Toast;

import zzyongx.fsyncer.qr.CaptureActivity;
import zzyongx.fsyncer.qr.QRCode;

public class MainActivity extends AppCompatActivity
        implements HttpServer.Event {

  private static final int QRCODE_REQ = 100;
  private static final int IMAGE_VIDEO_DIR_REQ = 101;

  static final String TAG = MainActivity.class.getSimpleName();
  static final String LISTEN_PORT = "LISTEN_PORT";
  static final String TOKEN_POOL_FILE = "tokenpool.txt";
  static final String IMAGE_VIDEO_DIR_FILE = "imagevideodir.txt";

  TextView  endpointView;
  ImageView qrcodeView;
  Bitmap    qrcodeBitmap;
  Button    mixButton;

  class SessionLock extends CountDownLatch {
    SessionLock() {
      super(1);
      rc = Activity.RESULT_CANCELED;
    }
    int rc;
  }
  Map<String, SessionLock> sessionLocks = new HashMap<>();

  HttpServer http;
  String ip;
  int port = -1;
  Handler handler = new Handler();

  List<String> imageVideoDirs;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if (savedInstanceState != null) {
      port = savedInstanceState.getInt(LISTEN_PORT, -1);
      if (port == -1) Log.d(TAG, "get saved port " + String.valueOf(port));
    }

    endpointView = (TextView) findViewById(R.id.main_endpointView);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    qrcodeView = (ImageView) findViewById(R.id.main_qrcodeView);

    mixButton = (Button) findViewById(R.id.main_mixButton);
    mixButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent i = new Intent(MainActivity.this, CaptureActivity.class);
        startActivity(i);
      }
    });

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {
      LinearLayout layout = (LinearLayout) findViewById(R.id.main_qrcodeLayout);
      layout.setVisibility(LinearLayout.GONE);
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    startWelcomeActivity();
    configHttp();
    if (port > 0) {
      String endpoint = endpointView.getText().toString();
      resetQRImage(endpoint + "?" + http.prefetchToken());
    }

    imageVideoDirs = loadImageVideoDirs();
    if (imageVideoDirs == null) {
      startImageVideoQuestActivity();
    } else if (imageVideoDirs.isEmpty()) {

    }

    List<String> types = new ArrayList<>();
    types.add(Environment.DIRECTORY_DCIM);
    types.add(Environment.DIRECTORY_PICTURES);
    types.add(Environment.DIRECTORY_MUSIC);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      types.add(Environment.DIRECTORY_DOCUMENTS);
    }

    for (String type : types) {
      File file = Environment.getExternalStoragePublicDirectory(type);
      if (file != null) Log.d(TAG, file.getPath());
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (http != null) http.stop();
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);

    if (port != -1) {
      savedInstanceState.putInt(LISTEN_PORT, port);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle item selection
    switch (item.getItemId()) {
      case R.id.menu_scan:
        scan();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == QRCODE_REQ) {
      if (data == null) return;

      String code = data.getStringExtra(CaptureActivity.QRCODE);
      Toast.makeText(this, code, Toast.LENGTH_SHORT).show();
      Log.d(TAG, "QRCODE:" + code);
    }
  }

  @Override
  public HttpServer.UserTokenPool whenStart() {
    HttpServer.UserTokenPool pool = new HttpServer.UserTokenPool();
    try {
      InputStream in = openFileInput(TOKEN_POOL_FILE);
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null) {
          HttpServer.UserToken token = HttpServer.UserToken.fromString(line);
          if (token != null) pool.add(HttpServer.UserToken.fromString(line));
        }
      } finally {
        in.close();
      }
    } catch (FileNotFoundException e) {
      // do nothing
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return pool;
  }

  @Override
  public void whenStop(HttpServer.UserTokenPool pool) {
    try {
      byte[] lf = new byte[] {'\n'};
      OutputStream out = openFileOutput(TOKEN_POOL_FILE, Context.MODE_PRIVATE);
      try {
        for (HttpServer.UserToken u : pool.getAll()) {
          out.write(u.toString().getBytes());
          out.write(lf);
        }
      } finally {
        out.close();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean onNewSession(final String source) {
    final SessionLock lock = new SessionLock();

    handler.post(new Runnable() {
      @Override
      public void run() {
        FragmentManager fm = MainActivity.this.getSupportFragmentManager();
        NewSessionDialogFragment dialog = NewSessionDialogFragment.newInstance(source);
        dialog.show(fm, "NewSession");
      }
    });

    sessionLocks.put(source, lock);

    try {
      lock.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    sessionLocks.remove(source);
    return lock.rc == Activity.RESULT_OK;
  }

  private void onNewSessionResult(String source, int rc) {
    SessionLock lock = sessionLocks.get(source);
    lock.rc = rc;
    lock.countDown();
  }

  private void startWelcomeActivity() {
    PackageManager pm = getPackageManager();
    try {
      PackageInfo info = pm.getPackageInfo(getPackageName(), 0);
      String version = String.valueOf(info.versionCode) + "." + info.versionName;

      SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
      String oldVersion = sharedPref.getString("APP_VERSION", null);

      if (oldVersion != null) Log.d(TAG, "oldVersion " + oldVersion);
      Log.d(TAG, "version  " + version);

      if (oldVersion == null || !oldVersion.equals(version)) {
        Log.d(TAG, "app first run or app upgrade");

        Intent i = new Intent(this, WelcomeActivity.class);
        startActivity(i);

        sharedPref.edit().putString("APP_VERSION", version).commit();
      }
    } catch (PackageManager.NameNotFoundException e) {
      // do nothing
    }
  }

  private List<String> loadImageVideoDirs() {
    try {
      InputStream in = openFileInput(IMAGE_VIDEO_DIR_FILE);
      try {
        List<String> dirs = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null) {
          File file = new File(line);
          if (file.exists() && file.isDirectory()) {
            dirs.add(line);
          }
        }
        return dirs;
      } finally {
        in.close();
      }
    } catch (FileNotFoundException e) {
      return null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void startImageVideoQuestActivity() {
    Intent i = new Intent(this, ImageVideoQuestActivity.class);
    startActivityForResult(i, IMAGE_VIDEO_DIR_REQ);
  }

  static final int LISTEN_PORTS[] = {9127, 7084, 6281, 9091, 8879};

  int getTryPortIndex() {
    if (port == -1) return 0;
    for (int i = 0; i < LISTEN_PORTS.length; ++i) {
      if (port == LISTEN_PORTS[i]) return i;
    }
    return 0;
  }

  void configHttp() {
    ip = getWifiIp();
    if (ip == null) {
      endpointView.setText(R.string.no_wifi_found);
      return;
    }

    int startIndex = getTryPortIndex();
    boolean startOk = false;
    for (int i = 0; !startOk && i < LISTEN_PORTS.length; i++) {
      port = LISTEN_PORTS[(i + startIndex) % LISTEN_PORTS.length];

      try {
        http = new HttpServer(this, ip, port);
        startOk = true;
      } catch (BindException e) {
        Log.d(TAG, "BindException at endpoint " + ip + ":" + String.valueOf(port), e);
      } catch (IOException e) {
        Log.d(TAG, "ioexception", e);
      }
    }

    if (startOk) {
      endpointView.setText("http://" + ip + ":" + String.valueOf(port));
      Log.d(TAG, "start at " + ip + ":" + String.valueOf(port));
    } else {
      port = -1;
    }
  }

  String getLocalIp() {
    try {
      for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
           en.hasMoreElements(); ) {
        NetworkInterface intf = en.nextElement();
        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
             enumIpAddr.hasMoreElements(); ) {
          InetAddress inetAddress = enumIpAddr.nextElement();
          if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
            return inetAddress.getHostAddress();
          }
        }
      }
    } catch (SocketException ex) {
      Log.e(TAG, "Socket exception in getLocalIp() " + ex.toString());
    }

    return null;
  }

  String getWifiIp() {
    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo netInfo = cm.getActiveNetworkInfo();

    boolean isConnected = netInfo.isConnectedOrConnecting();
    if (!isConnected) return null;

    if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
      return getLocalIp();
    } else {
      return null;
    }
  }

  void resetQRImage(String url) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
      Bitmap bitmap = new QRCode().encode(url);
      if (bitmap == null) return;

      if (qrcodeBitmap != null) {
        qrcodeBitmap.recycle();
      }
      qrcodeBitmap = bitmap;

      qrcodeView.setImageBitmap(bitmap);
    }
  }

  @SuppressWarnings("unused")
  void wxScan() {
    Uri uri = Uri.parse("weixin://dl/scan");
    Intent i = new Intent(Intent.ACTION_VIEW, uri);
    startActivityForResult(i, 0);
  }

  void scan() {
    Intent i = new Intent(this, CaptureActivity.class);
    startActivityForResult(i, QRCODE_REQ);
  }

  public static class NewSessionDialogFragment extends DialogFragment {
    private static final String SOURCE = "source";

    static NewSessionDialogFragment newInstance(String source) {
      NewSessionDialogFragment dialog = new NewSessionDialogFragment();

      Bundle args = new Bundle();
      args.putString(SOURCE, source);
      dialog.setArguments(args);

      return dialog;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final String source = getArguments().getString(SOURCE);

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setMessage(getString(R.string.new_fsync_request, source))
              .setPositiveButton(R.string.request_accept, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  ((MainActivity) getActivity()).onNewSessionResult(source, Activity.RESULT_OK);
                }
              })
              .setNegativeButton(R.string.request_deny, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  ((MainActivity) getActivity()).onNewSessionResult(source, Activity.RESULT_CANCELED);
                }
              });

      return builder.create();
    }
  }
}
