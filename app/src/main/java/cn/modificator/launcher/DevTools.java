package cn.modificator.launcher;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Hidden developer tools (long press on the version line in Help & About). On Moaan firmwares
 * ActivityManager.setSystemProp() makes system_server set a system property for any app; these
 * tools use it to try starting adb and rebooting into recovery, since the firmware blocks
 * developer options. Nothing here is permanent: a reboot restores the USB configuration.
 */
public final class DevTools {
  private DevTools() {
  }

  public static void show(final Context context) {
    String[] items = {
            context.getString(R.string.dev_tools_probe),
            context.getString(R.string.dev_tools_adb),
            context.getString(R.string.dev_tools_adb_wifi),
            context.getString(R.string.dev_tools_recovery),
    };
    new AlertDialog.Builder(context)
            .setTitle(R.string.dev_tools_title)
            .setItems(items, (dialog, which) -> {
              if (which == 0) {
                runProbe(context);
              } else if (which == 1) {
                tryEnableAdb(context);
              } else if (which == 2) {
                tryEnableAdbOverWifi(context);
              } else {
                confirmRecovery(context);
              }
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
  }

  private static void runProbe(final Context context) {
    final Handler mainHandler = new Handler(Looper.getMainLooper());
    final Context appContext = context.getApplicationContext();
    new Thread(() -> {
      String message;
      try {
        File report = DeviceProbe.run(appContext);
        message = context.getString(R.string.probe_saved, report.getPath());
      } catch (Exception e) {
        message = context.getString(R.string.probe_failed, e.toString());
      }
      final String text = message;
      mainHandler.post(() -> Toast.makeText(context, text, Toast.LENGTH_LONG).show());
    }, "DeviceProbe").start();
  }

  /** Asks system_server to switch USB to MTP + adb, then reports what happened. */
  private static void tryEnableAdb(final Context context) {
    if (!setSystemProp(context, "sys.usb.config", "mtp,adb")) {
      showResult(context, context.getString(R.string.dev_tools_not_available));
      return;
    }
    // The USB stack takes a moment to switch configuration and start adbd.
    new Handler(Looper.getMainLooper()).postDelayed(() -> showResult(context, context.getString(
            R.string.dev_tools_adb_result,
            getSystemProperty("sys.usb.config"),
            getSystemProperty("sys.usb.state"),
            getSystemProperty("init.svc.adbd"))), 3000);
  }

  /**
   * adbd also listens on TCP when service.adb.tcp.port (or persist.adb.tcp.port) is set when it
   * starts. Set the port through setSystemProp(), then restart adbd by switching the USB
   * configuration off and back to what it was. Reports which properties stuck.
   */
  private static void tryEnableAdbOverWifi(final Context context) {
    if (!setSystemProp(context, "service.adb.tcp.port", "5555")) {
      showResult(context, context.getString(R.string.dev_tools_not_available));
      return;
    }
    if (!"5555".equals(getSystemProperty("service.adb.tcp.port"))) {
      setSystemProp(context, "persist.adb.tcp.port", "5555");
    }
    final String usbConfig = getSystemProperty("sys.usb.config");
    final String restore = "–".equals(usbConfig) || "?".equals(usbConfig) ? "adb" : usbConfig;
    setSystemProp(context, "sys.usb.config", "none");
    final Handler handler = new Handler(Looper.getMainLooper());
    handler.postDelayed(() -> {
      setSystemProp(context, "sys.usb.config", restore);
      handler.postDelayed(() -> showResult(context, context.getString(
              R.string.dev_tools_adb_wifi_result,
              getSystemProperty("service.adb.tcp.port"),
              getSystemProperty("persist.adb.tcp.port"),
              getSystemProperty("init.svc.adbd"),
              wifiAddress(context))), 3000);
    }, 1500);
  }

  private static String wifiAddress(Context context) {
    try {
      android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager)
              context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      int ip = wifi.getConnectionInfo().getIpAddress();
      return (ip & 0xff) + "." + (ip >> 8 & 0xff) + "." + (ip >> 16 & 0xff) + "." + (ip >> 24 & 0xff);
    } catch (Exception e) {
      return "?";
    }
  }

  private static void confirmRecovery(final Context context) {
    new AlertDialog.Builder(context)
            .setTitle(R.string.dev_tools_recovery)
            .setMessage(R.string.dev_tools_recovery_confirm)
            .setPositiveButton(R.string.dialog_confim, (dialog, which) -> {
              if (!setSystemProp(context, "sys.powerctl", "reboot,recovery")) {
                showResult(context, context.getString(R.string.dev_tools_not_available));
                return;
              }
              // If we are still running a few seconds later, the firmware ignored it.
              new Handler(Looper.getMainLooper()).postDelayed(() -> showResult(context,
                      context.getString(R.string.dev_tools_recovery_failed)), 6000);
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
  }

  private static void showResult(Context context, String message) {
    new AlertDialog.Builder(context)
            .setTitle(R.string.dev_tools_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_close, null)
            .show();
  }

  /** The Moaan vendor method; false if missing or refused. */
  static boolean setSystemProp(Context context, String key, String value) {
    try {
      Method method = ActivityManager.class.getMethod("setSystemProp", String.class, String.class);
      method.invoke(context.getSystemService(Context.ACTIVITY_SERVICE), key, value);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  static String getSystemProperty(String key) {
    try {
      Class<?> systemProperties = Class.forName("android.os.SystemProperties");
      String value = (String) systemProperties.getMethod("get", String.class).invoke(null, key);
      return value == null || value.isEmpty() ? "–" : value;
    } catch (Exception e) {
      return "?";
    }
  }
}
