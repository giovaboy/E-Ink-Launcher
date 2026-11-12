package io.github.giovaboy.inkpalmtools;

import android.content.ContentResolver;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * The privileged operations. They only work when the app runs as the system user, which needs
 * the platform signature (see AndroidManifest.xml); otherwise they fail and say why.
 */
final class SystemTools {
  static final int ADB_TCP_PORT = 5555;

  private SystemTools() {
  }

  static boolean isSystemUser() {
    return Process.myUid() == Process.SYSTEM_UID;
  }

  static String getProperty(String key) {
    try {
      Class<?> c = Class.forName("android.os.SystemProperties");
      return (String) c.getMethod("get", String.class).invoke(null, key);
    } catch (Exception e) {
      return "?";
    }
  }

  /** Sets a system property; returns null on success or the reason it was refused. */
  static String setProperty(String key, String value) {
    try {
      Class<?> c = Class.forName("android.os.SystemProperties");
      Method set = c.getMethod("set", String.class, String.class);
      set.invoke(null, key, value);
      return value.equals(getProperty(key)) ? null : "not applied";
    } catch (java.lang.reflect.InvocationTargetException e) {
      return String.valueOf(e.getCause());
    } catch (Exception e) {
      return e.toString();
    }
  }

  static int getGlobalInt(Context context, String key) {
    return Settings.Global.getInt(context.getContentResolver(), key, -1);
  }

  /**
   * Restarts adbd the way the "USB debugging" switch does: system_server stops and starts it
   * when adb_enabled changes. adbd reads service.adb.tcp.port when it starts.
   */
  static void restartAdb(Context context) throws InterruptedException {
    ContentResolver resolver = context.getContentResolver();
    Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, 0);
    Thread.sleep(1500);
    Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, 1);
    // The firmware's lock screen resets adb_enabled from this one after unlocking.
    Settings.Global.putInt(resolver, "com.moan.adb.enabled", 1);
  }

  /** Whether adbd accepts TCP connections on the adb port, checked by connecting locally. */
  static boolean isAdbListeningOnTcp() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", ADB_TCP_PORT), 1000);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  static String wifiAddress(Context context) {
    WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    if (wifi == null) {
      return "?";
    }
    int ip = wifi.getConnectionInfo().getIpAddress();
    if (ip == 0) {
      return "?";
    }
    return (ip & 0xff) + "." + (ip >> 8 & 0xff) + "." + (ip >> 16 & 0xff) + "." + (ip >> 24 & 0xff);
  }

  static void rebootToRecovery(Context context) {
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    pm.reboot("recovery");
  }
}
