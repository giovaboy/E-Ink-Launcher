package io.github.giovaboy.inkpalmtools;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Disabling and re-enabling apps, as the system user. Disabled apps stay installed but never
 * run (so they use no RAM) and disappear from launchers; re-enabling brings them back as they
 * were. Safer than "pm uninstall --user 0", and reversible from this app.
 */
final class AppControl {
  /**
   * Suggested: the vendor apps an inkPalm 5 owner removed in
   * https://jacob.earth/post/2025/inkpalm-5-mini-ereader-setup/ on the same firmware build.
   * com.android.phone is the telephony service: a persistent process, useless on a device with
   * no cellular modem (Bluetooth headphones don't need it). Only those installed are shown.
   */
  static final List<String> SUGGESTED = Arrays.asList(
          "com.sohu.inputmethod.sogou.oem",
          "com.jd.app.reader",
          "cn.wps.moffice_eng.lite",
          "com.tencent.weread.eink",
          "com.zhangyue.read.iReader.eink",
          "com.duokan.einkreader",
          "com.moan.browser",
          "com.moan.appstore",
          "com.moan.cloudservices",
          "com.mgs.agingtest",
          "com.mgs.factorytest",
          "com.softwinner.dragonfire",
          "com.softwinner.dragonaging",
          "com.softwinner.awmanager",
          "com.android.email",
          "com.android.printspooler",
          "com.android.backupconfirm",
          "com.android.mms.service",
          "com.android.phone",
          // The firmware updater, a system process always running. V11 is the last firmware
          // for the inkPalm 5; disabling it only stops future firmware updates.
          "com.abupdate.fota_demo_iot",
          "com.goodix.rawdata");

  /** Never offered: the device would not work, or not be fixable from here, without them. */
  private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
          "android",
          "com.android.systemui",
          "com.android.settings",
          "com.mgs.settings",
          "com.android.packageinstaller",
          "com.android.shell",
          "com.android.keychain",
          // The stock launcher: it holds the Moaan settings menu and the screens for a forgotten
          // lock password, which the firmware opens.
          "com.moan.launcher",
          "com.moan.moanwm")); // E-Ink Launcher

  static final class App {
    final String packageName;
    final String label;
    boolean enabled;
    final boolean isProtected;

    App(String packageName, String label, boolean enabled, boolean isProtected) {
      this.packageName = packageName;
      this.label = label;
      this.enabled = enabled;
      this.isProtected = isProtected;
    }
  }

  private AppControl() {
  }

  /** Installed apps, disabled ones included: the suggested ones only, or all of them. */
  static List<App> list(Context context, boolean suggestedOnly) {
    PackageManager pm = context.getPackageManager();
    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            ? PackageManager.MATCH_DISABLED_COMPONENTS : PackageManager.GET_DISABLED_COMPONENTS;
    Set<String> protectedNow = protectedPackages(context);
    List<App> apps = new ArrayList<>();
    for (ApplicationInfo info : pm.getInstalledApplications(flags)) {
      if (suggestedOnly && !SUGGESTED.contains(info.packageName)) {
        continue;
      }
      apps.add(new App(info.packageName, String.valueOf(info.loadLabel(pm)), isEnabled(pm, info),
              protectedNow.contains(info.packageName) || info.packageName.startsWith("com.android.providers.")));
    }
    Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    return apps;
  }

  private static boolean isEnabled(PackageManager pm, ApplicationInfo info) {
    int state = pm.getApplicationEnabledSetting(info.packageName);
    if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
      return info.enabled;
    }
    return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
  }

  /** The fixed list plus what is in use right now: the keyboard and the home app. */
  private static Set<String> protectedPackages(Context context) {
    Set<String> result = new HashSet<>(PROTECTED);
    result.add(context.getPackageName());
    String ime = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    if (ime != null && ime.contains("/")) {
      result.add(ime.substring(0, ime.indexOf('/')));
    }
    ResolveInfo home = context.getPackageManager().resolveActivity(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
    if (home != null && home.activityInfo != null) {
      result.add(home.activityInfo.packageName);
    }
    return result;
  }

  /** Disables or re-enables an app; returns null on success or the error. */
  static String setEnabled(Context context, App app, boolean enabled) {
    if (app.isProtected) {
      return "protected";
    }
    try {
      context.getPackageManager().setApplicationEnabledSetting(app.packageName,
              enabled ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                      : PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
              0); // 0: also stops the app if it is running
      app.enabled = enabled;
      return null;
    } catch (Exception e) {
      return e.toString();
    }
  }
}
