package cn.modificator.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Debug;

import java.util.Locale;

/**
 * Measures the launcher's own memory use, so RAM optimizations can be checked on devices
 * without adb. Values are PSS (proportional set size), the same number Android uses to
 * decide which processes to kill.
 */
public final class MemoryStats {
  private static final String PREFS_NAME = "memoryStats";
  private static final String KEY_BACKGROUND_PSS_KB = "backgroundPssKb";

  private MemoryStats() {
  }

  public static long currentPssKb() {
    Debug.MemoryInfo info = new Debug.MemoryInfo();
    Debug.getMemoryInfo(info);
    return info.getTotalPss();
  }

  /** Records the memory used while another app (e.g. a reader) is in the foreground. */
  public static void recordBackgroundUsage(Context context) {
    // The icons were just released: collect them now so the number reflects reality.
    Runtime.getRuntime().gc();
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_BACKGROUND_PSS_KB, currentPssKb())
            .apply();
  }

  public static String describe(Context context) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    long backgroundKb = prefs.getLong(KEY_BACKGROUND_PSS_KB, -1);
    String foreground = toMb(currentPssKb());
    String background = backgroundKb >= 0 ? toMb(backgroundKb) : "–";
    return context.getString(R.string.about_memory, BuildConfig.VERSION_NAME, foreground, background);
  }

  private static String toMb(long kb) {
    return String.format(Locale.getDefault(), "%.1f", kb / 1024f);
  }
}
