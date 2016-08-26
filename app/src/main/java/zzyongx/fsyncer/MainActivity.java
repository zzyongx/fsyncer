package zzyongx.fsyncer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

public class MainActivity extends AppCompatActivity {
  static final String TAG = "MainActivity";
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
  }

  @Override
  public void onResume() {
    super.onResume();

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
}
