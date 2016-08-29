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
import java.util.concurrent.ThreadLocalRandom;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

public class MainActivity extends AppCompatActivity {
  static final String TAG = "MainActivity";
  static final String LISTEN_PORT = "LISTEN_PORT";

  TextView endpointView;
  
  HttpServer http;
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
  }

  @Override
  public void onResume() {
    super.onResume();

    startWelcomeActivity();
    configHttp();

    List<String> types = new ArrayList<>();
    types.add(Environment.DIRECTORY_DCIM);
    types.add(Environment.DIRECTORY_PICTURES);
    types.add(Environment.DIRECTORY_MUSIC);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      types.add(Environment.DIRECTORY_DOCUMENTS);
    };

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

  void configHttp() {
    String ip = getWifiIp();
    if (ip == null) {
      endpointView.setText("this app only work in wifi network, check it");
      return;
    }

    boolean startOk = false;

    for (int i = 0; !startOk && i < 10 ; ++i) {
      if (port == -1 || i != 0) {
        port = ThreadLocalRandom.current().nextInt(6000, 9999);
      }
      try {
        http = new HttpServer(ip, port);
        http.start();
        startOk = true;
      } catch (BindException e) {
        /* when this Exception arise, start OK, I don't know why */
        startOk = true;
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
}

