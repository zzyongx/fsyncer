package zzyongx.fsyncer;

import java.io.File;
import java.net.BindException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Enumeration;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
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
import android.widget.TextView;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import zzyongx.fsyncer.qr.CaptureActivity;

public class MainActivity extends AppCompatActivity
  implements HttpServer.Event {

  static final String TAG = "MainActivity";
  static final String LISTEN_PORT = "LISTEN_PORT";

  TextView endpointView;
  ImageView qrcodeView;
  Button mixButton;
  
  HttpServer http;
  String ip;
  int port = -1;
  Handler handler = new Handler();
  
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
  }

  @Override
  public void onResume() {
    super.onResume();

    startWelcomeActivity();
    configHttp();
    resetQRImage();

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
    IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    if (scanResult != null) {
      Log.d(TAG, "QRcode  " + scanResult.getContents());
    }
  }

  public HttpServer.UserTokenPool whenStart() {
    HttpServer.UserTokenPool pool = new HttpServer.UserTokenPool();
    return pool;
  }

  public void whenStop(HttpServer.UserTokenPool pool) {
    for (HttpServer.UserToken u : pool.getAll()) {
    }
  }

  public class Lock {
    public int rc;
  }

  public boolean onNewSession() {
    final Lock lock = new Lock();
    
    handler.post(new Runnable() {
        @Override
        public void run() {
          FragmentManager fm = MainActivity.this.getSupportFragmentManager();
          NewSessionDialogFragment dialog = new NewSessionDialogFragment(lock);
          dialog.show(fm, "NewSession");
        }
      });

    synchronized(lock) {
      try {
        lock.wait();
      } catch (Exception e) {
      }
      return lock.rc == Activity.RESULT_OK;
    }
  }

  void startWelcomeActivity() {
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
    }    
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
      endpointView.setText("this app only work in wifi network, check it");
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
      } catch (Exception e) {
        Log.d(TAG, "Internal error", e);
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
           en.hasMoreElements();) {
        NetworkInterface intf = en.nextElement();
        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
             enumIpAddr.hasMoreElements();) {
          InetAddress inetAddress = enumIpAddr.nextElement();
          if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
            return inetAddress.getHostAddress().toString();
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

  void resetQRImage() {
    /*
    Bitmap bitmap = new QRCode().encode("text");
    if (bitmap == null) return;

    qrcodeView.setImageBitmap(bitmap);
    */
  }

  void wxScan() {
    Uri uri = Uri.parse("weixin://dl/scan");
    Intent i = new Intent(Intent.ACTION_VIEW, uri);
    startActivityForResult(i, 0);
  }

  void scan() {
//    IntentIntegrator integrator = new IntentIntegrator(this);
//    integrator.initiateScan();
  }      

  class NewSessionDialogFragment extends DialogFragment {
    final Lock lock;
    public NewSessionDialogFragment(final Lock lock) {
      this.lock = lock;
    }
    
    void sendResult(int rc) {
      synchronized (lock) {
        lock.rc = rc;
        lock.notify();
      }
    }
  
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
      builder.setMessage("新的同步请求？")
        .setPositiveButton("接受", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              sendResult(Activity.RESULT_OK);
            }
          })
        .setNegativeButton("拒绝", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              sendResult(Activity.RESULT_CANCELED);
            }
          });
        
      return builder.create();
    }
  }
}
