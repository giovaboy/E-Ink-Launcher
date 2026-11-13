package io.github.giovaboy.inkpalmtools;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Moaan firmware's background kill. Its ActivityManagerService force-stops a non-system app
 * 30 seconds after it leaves the screen ("BEGIN_MOGU_KILL_APP" in the log) when the app's
 * "freeze" setting is 1. The setting is per package and per user, saved as freeze="1" in
 * /data/system/users/0/package-restrictions.xml, and changed with the vendor method
 * IPackageManager.setApplicationFreezeSetting(packageName, state, flags, userId, callingPackage),
 * which skips its permission check for the system user. Turning it off lets an app (a launcher
 * with an FTP server, a keyboard, a music player) keep running in the background like on stock
 * Android; it survives reboots.
 *
 * The methods are not in the SDK, so they are called by reflection: on other firmwares they
 * don't exist and every call reports that instead of failing.
 */
final class BackgroundKill {
  /** The value that gets an app killed in the background (checked as "== 1" by the firmware). */
  static final int FROZEN = 1;

  static final class App {
    final String packageName;
    final String label;
    /** The freeze setting, or null if it can't be read. */
    Integer freeze;

    App(String packageName, String label, Integer freeze) {
      this.packageName = packageName;
      this.label = label;
      this.freeze = freeze;
    }

    boolean keepsRunning() {
      return freeze != null && freeze != FROZEN;
    }
  }

  private BackgroundKill() {
  }

  /** Apps the firmware may kill: the non-system ones, sorted by name. */
  static List<App> list(Context context) {
    PackageManager pm = context.getPackageManager();
    List<App> apps = new ArrayList<>();
    for (ApplicationInfo info : pm.getInstalledApplications(0)) {
      if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || info.packageName.equals(context.getPackageName())) {
        continue;
      }
      apps.add(new App(info.packageName, String.valueOf(info.loadLabel(pm)), get(context, info.packageName)));
    }
    Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    return apps;
  }

  /** The app's freeze setting, or null where the firmware has no such setting. */
  static Integer get(Context context, String packageName) {
    try {
      PackageManager pm = context.getPackageManager();
      Method method = pm.getClass().getMethod("getApplicationFreezeSetting", String.class);
      return (Integer) method.invoke(pm, packageName);
    } catch (Throwable e) {
      return null;
    }
  }

  /** Lets the app keep running in the background, or not; returns null on success or the error. */
  static String set(Context context, App app, boolean keepRunning) {
    int state = keepRunning ? notFrozenValue() : FROZEN;
    try {
      Object service = Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null);
      Method method = service.getClass().getMethod("setApplicationFreezeSetting",
              String.class, int.class, int.class, int.class, String.class);
      method.invoke(service, app.packageName, state, 0, userId(), context.getPackageName());
    } catch (java.lang.reflect.InvocationTargetException e) {
      return String.valueOf(e.getCause());
    } catch (Throwable e) {
      return context.getString(R.string.background_unsupported);
    }
    app.freeze = get(context, app.packageName);
    if (app.freeze == null || app.freeze != state) {
      return context.getString(R.string.background_not_changed, String.valueOf(app.freeze));
    }
    return null;
  }

  /**
   * The firmware's own "not frozen" constant (PackageManager.APP_FREEZE_DISABLE) when it has
   * one, else 0, the value an app has when the setting was never written.
   */
  static int notFrozenValue() {
    try {
      int value = PackageManager.class.getField("APP_FREEZE_DISABLE").getInt(null);
      if (value != FROZEN) {
        return value;
      }
    } catch (Throwable ignored) {
      // Not this firmware.
    }
    return 0;
  }

  private static int userId() {
    // UserHandle.PER_USER_RANGE: the user id is the uid divided by 100000.
    return android.os.Process.myUid() / 100000;
  }

  /** For the diagnostics: the vendor freeze methods and constants this firmware has. */
  static String describeApi() {
    StringBuilder out = new StringBuilder();
    describe(out, PackageManager.class);
    try {
      describe(out, Class.forName("android.content.pm.IPackageManager"));
    } catch (Throwable e) {
      out.append("android.content.pm.IPackageManager: ").append(e).append('\n');
    }
    if (out.length() == 0) {
      out.append("(no freeze methods: not a Moaan firmware)\n");
    }
    out.append(String.format(Locale.US, "value used for \"keep running\": %d\n", notFrozenValue()));
    return out.toString();
  }

  private static void describe(StringBuilder out, Class<?> type) {
    for (Method method : type.getMethods()) {
      if (method.getName().toLowerCase(Locale.US).contains("freeze")) {
        out.append(type.getName()).append('.').append(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
          out.append(i == 0 ? "" : ", ").append(params[i].getSimpleName());
        }
        out.append(")\n");
      }
    }
    for (Field field : type.getFields()) {
      if (field.getName().contains("FREEZE") && Modifier.isStatic(field.getModifiers())) {
        try {
          out.append(type.getName()).append('.').append(field.getName()).append(" = ").append(field.get(null)).append('\n');
        } catch (Throwable ignored) {
          // Unreadable: skip it.
        }
      }
    }
  }
}
