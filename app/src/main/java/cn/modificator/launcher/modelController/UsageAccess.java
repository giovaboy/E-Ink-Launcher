package cn.modificator.launcher.modelController;

import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * When each app was last in the foreground, from Android's usage stats, for the "recently
 * used" sort. Needs the "usage access" special permission, which the user grants in the
 * system settings; without it every app counts as never used and the sort is alphabetical.
 */
public final class UsageAccess {
  private static final long HISTORY_MS = 2L * 365 * 24 * 60 * 60 * 1000;

  private UsageAccess() {
  }

  public static boolean isSupported() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public static boolean isGranted(Context context) {
    if (!isSupported()) {
      return false;
    }
    AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
    return appOps != null && appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
  }

  /** Opens the system screen where usage access is granted. Returns false if there is none. */
  public static boolean openSettings(Context context) {
    try {
      context.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return true;
    } catch (ActivityNotFoundException e) {
      return false;
    }
  }

  /** Package name to last foreground time (ms); empty when not granted. */
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public static Map<String, Long> lastUsedTimes(Context context) {
    if (!isGranted(context)) {
      return Collections.emptyMap();
    }
    // Context.USAGE_STATS_SERVICE is API 22, the service itself exists since API 21.
    UsageStatsManager usm = (UsageStatsManager) context.getSystemService("usagestats");
    if (usm == null) {
      return Collections.emptyMap();
    }
    long now = System.currentTimeMillis();
    Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(now - HISTORY_MS, now);
    Map<String, Long> result = new HashMap<>();
    for (Map.Entry<String, UsageStats> entry : stats.entrySet()) {
      result.put(entry.getKey(), entry.getValue().getLastTimeUsed());
    }
    return result;
  }
}
