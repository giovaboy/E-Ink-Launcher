package cn.modificator.launcher.eink;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;

import cn.modificator.launcher.Utils;

/**
 * Front light and contrast of Moaan e-readers, set the way the firmware's SystemUI does it:
 * through the vendor methods ActivityManager.getSysNode()/setSysNode(), which let
 * system_server write the driver nodes that apps can't open themselves.
 *
 * SystemUI keeps its own copy of the values in Settings.Global, which apps can't write: the
 * launcher remembers what it set, together with the Settings.Global values it saw at the time,
 * so that a later change from the system panel wins over the launcher's values.
 */
public final class EinkControls {
  private static final String TAG = "EinkControls";

  public static final int LIGHT_MAX = 24;
  public static final int CONTRAST_MAX = 5;

  private static final String NODE_PWM = "/proc/lm3630a/pwm_level";
  private static final String NODE_LEDA_MAX_CURRENT = "/proc/lm3630a/leda_max_cur";
  private static final String NODE_LEDA_BRIGHTNESS = "/proc/lm3630a/leda_brightness";
  private static final String NODE_LEDB_MAX_CURRENT = "/proc/lm3630a/ledb_max_cur";
  private static final String NODE_LEDB_BRIGHTNESS = "/proc/lm3630a/ledb_brightness";
  private static final String NODE_GAMMA = "/sys/kernel/debug/dispdbg/gamma_lvl";

  private static final String GLOBAL_BRIGHTNESS = "mogu_warm_led_value";
  private static final String GLOBAL_TEMPERATURE = "mogu_cold_led_value";

  private static final String PREFS_NAME = "einkControls";
  private static final int NOT_READ = Integer.MIN_VALUE;

  private EinkControls() {
  }

  /** Whether this firmware has the vendor methods: otherwise use the system panels. */
  public static boolean isSupported(Context context) {
    return Utils.isMoann && sysNodeMethod("setSysNode") != null
            && sysNodeMethod("getSysNode") != null;
  }

  // ---- Front light ----

  /** Brightness and temperature (0..LIGHT_MAX) the panel should start from. */
  public static int[] getLight(Context context) {
    int globalBrightness = Settings.Global.getInt(context.getContentResolver(), GLOBAL_BRIGHTNESS, 6);
    int globalTemperature = Settings.Global.getInt(context.getContentResolver(), GLOBAL_TEMPERATURE, 12);
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    boolean systemPanelUsedSince = prefs.getInt("seenGlobalBrightness", NOT_READ) != globalBrightness
            || prefs.getInt("seenGlobalTemperature", NOT_READ) != globalTemperature;
    if (!prefs.contains("brightness") || systemPanelUsedSince) {
      return new int[]{clamp(globalBrightness, LIGHT_MAX), clamp(globalTemperature, LIGHT_MAX)};
    }
    return new int[]{prefs.getInt("brightness", globalBrightness), prefs.getInt("temperature", globalTemperature)};
  }

  /**
   * Sets the front light; brightness 0 turns it off. Returns false if the firmware didn't
   * accept the change (checked by reading the PWM node back).
   */
  public static boolean setLight(Context context, int brightness, int temperature) {
    brightness = clamp(brightness, LIGHT_MAX);
    temperature = clamp(temperature, LIGHT_MAX);
    // Same nodes, values and order as SystemUI's FunctionSettingsControl.setLedValue().
    int pwm = LedTables.PWM_VALUE[temperature][brightness];
    boolean ok = setSysNode(context, NODE_PWM, pwm)
            && setSysNode(context, NODE_LEDA_MAX_CURRENT, LedTables.COLD_CURRENT[temperature][brightness])
            && setSysNode(context, NODE_LEDA_BRIGHTNESS, LedTables.COLD_BRIGHTNESS[temperature][brightness])
            && setSysNode(context, NODE_LEDB_MAX_CURRENT, LedTables.WARM_CURRENT[temperature][brightness])
            && setSysNode(context, NODE_LEDB_BRIGHTNESS, LedTables.WARM_BRIGHTNESS[temperature][brightness]);
    if (!ok || !readsBackAs(context, NODE_PWM, pwm)) {
      return false;
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("brightness", brightness)
            .putInt("temperature", temperature)
            .putInt("seenGlobalBrightness", Settings.Global.getInt(context.getContentResolver(), GLOBAL_BRIGHTNESS, NOT_READ))
            .putInt("seenGlobalTemperature", Settings.Global.getInt(context.getContentResolver(), GLOBAL_TEMPERATURE, NOT_READ))
            .apply();
    return true;
  }

  // ---- Contrast ----

  /** Current contrast level, 0..CONTRAST_MAX, or -1 if it can't be read. */
  public static int getContrast(Context context) {
    int value = getSysNode(context, NODE_GAMMA);
    return value == NOT_READ ? -1 : clamp(value, CONTRAST_MAX);
  }

  /** Sets the contrast level (0..CONTRAST_MAX); returns false if the firmware refused it. */
  public static boolean setContrast(Context context, int level) {
    level = clamp(level, CONTRAST_MAX);
    return setSysNode(context, NODE_GAMMA, level) && readsBackAs(context, NODE_GAMMA, level);
  }

  // ---- Vendor methods ----

  /**
   * Whether a node holds the value just written. A node that can't be read back (or reads in
   * a format getSysNode doesn't parse) is given the benefit of the doubt; only a different
   * value means the write was ignored.
   */
  private static boolean readsBackAs(Context context, String path, int expected) {
    int actual = getSysNode(context, path);
    return actual == NOT_READ || actual == expected;
  }

  private static int getSysNode(Context context, String path) {
    Method method = sysNodeMethod("getSysNode");
    if (method == null) {
      return NOT_READ;
    }
    try {
      return (Integer) method.invoke(activityManager(context), path, NOT_READ);
    } catch (Exception e) {
      Log.w(TAG, "getSysNode " + path, e);
      return NOT_READ;
    }
  }

  private static boolean setSysNode(Context context, String path, int value) {
    Method method = sysNodeMethod("setSysNode");
    if (method == null) {
      return false;
    }
    try {
      method.invoke(activityManager(context), path, value);
      return true;
    } catch (Exception e) {
      Log.w(TAG, "setSysNode " + path + " = " + value, e);
      return false;
    }
  }

  @Nullable
  private static Method sysNodeMethod(String name) {
    try {
      return ActivityManager.class.getMethod(name, String.class, int.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static ActivityManager activityManager(Context context) {
    return (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
  }

  private static int clamp(int value, int max) {
    return Math.max(0, Math.min(max, value));
  }
}
