package cn.modificator.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;

import java.util.Locale;

/**
 * Lets the user pick the launcher language independently of the system language.
 * Activities and the Application wrap their base context with {@link #wrap(Context)}.
 */
public final class LocaleHelper {
  private static final String PREFS_NAME = "launcherPropertyFile";
  private static final String KEY_LANGUAGE = "appLanguage";

  /** Language tags offered in settings; the empty tag means "follow the system". */
  public static final String[] LANGUAGE_TAGS = {"", "it", "en", "zh", "pl", "ko"};
  /** Native names for {@link #LANGUAGE_TAGS}, index 0 is replaced by a translated "System". */
  private static final String[] LANGUAGE_NAMES = {null, "Italiano", "English", "中文", "Polski", "한국어"};

  private LocaleHelper() {
  }

  public static String getLanguage(Context context) {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "");
  }

  public static void setLanguage(Context context, String tag) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, tag).commit();
  }

  public static String[] getLanguageNames(Context context) {
    String[] names = LANGUAGE_NAMES.clone();
    names[0] = context.getString(R.string.language_system);
    return names;
  }

  public static int getLanguageIndex(Context context) {
    String current = getLanguage(context);
    for (int i = 0; i < LANGUAGE_TAGS.length; i++) {
      if (LANGUAGE_TAGS[i].equals(current)) {
        return i;
      }
    }
    return 0;
  }

  public static Context wrap(Context base) {
    String tag = getLanguage(base);
    if (TextUtils.isEmpty(tag)) {
      // Undo a previous override, so date formatting follows the system again.
      Locale.setDefault(Resources.getSystem().getConfiguration().locale);
      return base;
    }
    Locale locale = new Locale(tag);
    Locale.setDefault(locale);
    Configuration config;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      // Only the locale as an override delta: density, font scale and any per-app
      // display tweaks applied by e-ink firmwares stay those of the base context.
      config = new Configuration();
    } else {
      // Before N the empty constructor sets defaults (e.g. font scale 1) instead of
      // leaving fields undefined, so start from the current configuration.
      config = new Configuration(base.getResources().getConfiguration());
    }
    config.setLocale(locale);
    return base.createConfigurationContext(config);
  }
}
