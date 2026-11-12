package cn.modificator.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Diagnostic report for e-ink firmwares: what the launcher is allowed to read and change for
 * the front light, contrast and refresh mode, and which vendor methods the framework exposes.
 * Nothing is changed: writes only put back the value that is already there.
 * Started with a long press on the memory line in "Help & About"; the report is saved to
 * Download/eink-launcher-probe.txt.
 */
public final class DeviceProbe {
  private static final String[] NODES = {
          "/proc/lm3630a/warm_level",
          "/proc/lm3630a/cold_level",
          "/proc/lm3630a/pwm_level",
          "/proc/lm3630a/leda_brightness",
          "/proc/lm3630a/ledb_brightness",
          "/proc/lm3630a/leda_max_cur",
          "/proc/lm3630a/ledb_max_cur",
          "/sys/kernel/debug/dispdbg/gamma_lvl",
  };
  private static final String[] GLOBAL_KEYS = {
          "mogu_warm_led_value", "mogu_cold_led_value",
          "mogu_warm_led_status", "mogu_cold_led_status",
          "mogu_contrast_value", "mogu_contrast_status",
  };
  private static final String[] PROPERTIES = {
          "persist.sys.mRefreshMode", "persist.sys.canRefresh", "persist.display.gu16_max_limit",
  };
  private static final String[] VENDOR_CLASSES = {
          "android.app.ActivityManager", "android.util.FunApi", "android.os.MoanFunApi",
  };

  private DeviceProbe() {
  }

  /** Runs the probe and returns the report file. Does I/O: call it off the main thread. */
  public static File run(Context context) throws IOException {
    StringBuilder report = new StringBuilder();
    report.append("E-Ink Launcher ").append(BuildConfig.VERSION_NAME).append(" device probe\n");
    report.append("device=").append(Build.DEVICE).append(" model=").append(Build.MODEL)
            .append(" sdk=").append(Build.VERSION.SDK_INT).append('\n')
            .append("fingerprint=").append(Build.FINGERPRINT).append("\n\n");

    report.append("== Nodes (exists, read, write, value, write-back)\n");
    for (String path : NODES) {
      File node = new File(path);
      String value = readFirstLine(node);
      report.append(path).append(": exists=").append(node.exists())
              .append(" read=").append(node.canRead())
              .append(" write=").append(node.canWrite())
              .append(" value=").append(value);
      // Only the two level nodes, and only a plain number: never touch current limits, and
      // don't feed a driver text it might parse as 0.
      boolean levelNode = path.endsWith("/warm_level") || path.endsWith("/cold_level");
      if (levelNode && node.canWrite() && value != null && value.trim().matches("\\d+")) {
        report.append(" writeBack=").append(writeBack(node, value.trim()));
      }
      report.append('\n');
    }

    report.append("\n== Settings.Global (value, write-back of the same value)\n");
    ContentResolver resolver = context.getContentResolver();
    for (String key : GLOBAL_KEYS) {
      String value = Settings.Global.getString(resolver, key);
      report.append(key).append('=').append(value);
      if (value != null) {
        try {
          Settings.Global.putString(resolver, key, value);
          report.append(" writeBack=ok");
        } catch (SecurityException e) {
          report.append(" writeBack=denied");
        }
      }
      report.append('\n');
    }

    report.append("\n== System properties\n");
    for (String property : PROPERTIES) {
      report.append(property).append('=').append(getSystemProperty(property)).append('\n');
    }

    for (String className : VENDOR_CLASSES) {
      report.append("\n== ").append(className).append('\n');
      try {
        Class<?> cls = Class.forName(className);
        Method[] methods = cls.getDeclaredMethods();
        Arrays.sort(methods, (a, b) -> a.getName().compareTo(b.getName()));
        for (Method method : methods) {
          // ActivityManager is huge: only list names that don't exist in stock Android.
          if (className.equals("android.app.ActivityManager") && !isVendorLooking(method.getName())) {
            continue;
          }
          report.append(Modifier.toString(method.getModifiers())).append(' ')
                  .append(method.getReturnType().getSimpleName()).append(' ')
                  .append(method.getName()).append('(');
          Class<?>[] params = method.getParameterTypes();
          for (int i = 0; i < params.length; i++) {
            if (i > 0) report.append(", ");
            report.append(params[i].getSimpleName());
          }
          report.append(")\n");
        }
      } catch (ClassNotFoundException e) {
        report.append("(not present)\n");
      }
    }

    // Vendor and settings apps, with the activities other apps can open: settings screens
    // that have no launcher icon can still be added to the grid by component name.
    report.append("\n== Vendor and settings packages (exported activities)\n");
    PackageManager pm = context.getPackageManager();
    for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
      String name = pkg.packageName.toLowerCase();
      if (!(name.contains("moan") || name.contains("mogu") || name.contains("mgs") || name.contains("settings"))) {
        continue;
      }
      report.append(pkg.packageName).append(" \"").append(pkg.applicationInfo.loadLabel(pm)).append("\"");
      Intent launch = pm.getLaunchIntentForPackage(pkg.packageName);
      if (launch != null && launch.getComponent() != null) {
        report.append(" launcher=").append(launch.getComponent().getClassName());
      }
      report.append('\n');
      if (pkg.activities == null) continue;
      for (ActivityInfo activity : pkg.activities) {
        if (activity.exported) {
          report.append("  ").append(activity.name).append(" \"").append(activity.loadLabel(pm)).append("\"\n");
        }
      }
    }

    File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "eink-launcher-probe.txt");
    try (FileWriter writer = new FileWriter(out)) {
      writer.write(report.toString());
    }
    return out;
  }

  private static boolean isVendorLooking(String name) {
    String lower = name.toLowerCase();
    return lower.contains("mogu") || lower.contains("moan") || lower.contains("systemprop")
            || lower.contains("led") || lower.contains("light") || lower.contains("bold")
            || lower.contains("contrast") || lower.contains("refresh") || lower.contains("eink")
            || lower.contains("node") || lower.contains("gamma");
  }

  private static String readFirstLine(File file) {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      return reader.readLine();
    } catch (IOException | SecurityException e) {
      return null;
    }
  }

  private static String writeBack(File file, String value) {
    try (FileWriter writer = new FileWriter(file)) {
      writer.write(value);
      return "ok";
    } catch (IOException | SecurityException e) {
      return "failed (" + e.getClass().getSimpleName() + ")";
    }
  }

  private static String getSystemProperty(String key) {
    try {
      Class<?> systemProperties = Class.forName("android.os.SystemProperties");
      return (String) systemProperties.getMethod("get", String.class).invoke(null, key);
    } catch (Exception e) {
      return "(error " + e.getClass().getSimpleName() + ")";
    }
  }
}
