package io.github.giovaboy.inkpalmtools;

import android.content.Context;
import android.provider.Settings;

/**
 * Moaan firmware settings that the vendor UI doesn't offer. They live in Settings.Global, which
 * normal apps can't write; the system user can.
 */
final class VendorSettings {
  /**
   * Read by PowerManagerService.wifiOnOff() when the reader goes to sleep: 1 (also when unset)
   * turns Wi-Fi off, and it comes back on at wake-up if it was on. 0 leaves Wi-Fi alone, so it
   * is still connected at wake-up, at the cost of some battery in standby.
   */
  static final String CLOSE_WIFI_ON_SLEEP = "com.moan.closewifi";

  private VendorSettings() {
  }

  /** True on Moaan firmwares (they have the vendor class android.util.FunApi). */
  static boolean isMoaan() {
    try {
      Class.forName("android.util.FunApi");
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  static boolean keepsWifiInStandby(Context context) {
    return Settings.Global.getInt(context.getContentResolver(), CLOSE_WIFI_ON_SLEEP, 1) == 0;
  }

  /** Returns null on success or the error. */
  static String setKeepWifiInStandby(Context context, boolean keep) {
    try {
      Settings.Global.putInt(context.getContentResolver(), CLOSE_WIFI_ON_SLEEP, keep ? 0 : 1);
      return null;
    } catch (Exception e) {
      return e.toString();
    }
  }

  /** For the diagnostics. */
  static String describe(Context context) {
    return CLOSE_WIFI_ON_SLEEP + "=" + Settings.Global.getString(context.getContentResolver(), CLOSE_WIFI_ON_SLEEP)
            + " (unset means 1: Wi-Fi off in standby)\n";
  }
}
