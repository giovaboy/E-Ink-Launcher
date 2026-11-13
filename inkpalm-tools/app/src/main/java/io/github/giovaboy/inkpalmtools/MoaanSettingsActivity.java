package io.github.giovaboy.inkpalmtools;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Opens the Moaan settings menu (Wi-Fi in standby, app freeze, DPI per app, logo key, refresh
 * mode, sleep...). It lives in the stock launcher, com.moan.launcher, as a non-exported activity,
 * so another home app can't open it; the system user can. This activity has its own icon in the
 * app list, so the menu is one tap away from any launcher.
 */
public class MoaanSettingsActivity extends Activity {
  static final ComponentName MOAAN_SETTINGS =
          new ComponentName("com.moan.launcher", "com.moan.launcher.settings.MySettingsActivity");

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String error = open(this);
    if (error != null) {
      Toast.makeText(this, getString(R.string.moaan_settings_missing, error), Toast.LENGTH_LONG).show();
    }
    finish();
  }

  /** Returns null on success or the error. */
  static String open(Activity activity) {
    try {
      activity.startActivity(new Intent().setComponent(MOAAN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return null;
    } catch (Exception e) {
      return e.getClass().getSimpleName();
    }
  }
}
