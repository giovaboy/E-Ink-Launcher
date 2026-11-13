package cn.modificator.launcher;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Renders screens to PNG like on the inkPalm 5 (720x1280 at 280 dpi), to look at layout
 * changes without the device. Only runs with -Dscreenshots=true; images go to
 * app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 720x1280 at 280 dpi: the per-app density the firmware gives the launcher on the device
@Config(sdk = 27, qualifiers = "it-w411dp-h731dp-280dpi")
public class ScreenshotTest {

  private static File outDir() {
    File dir = new File("build/screenshots");
    dir.mkdirs();
    return dir;
  }

  private static void save(View view, int width, int height, String name) throws Exception {
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);
    view.draw(canvas);
    try (FileOutputStream out = new FileOutputStream(new File(outDir(), name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  @Test
  public void settings() throws Exception {
    Assume.assumeTrue(Boolean.getBoolean("screenshots"));
    Launcher launcher = Robolectric.buildActivity(Launcher.class).setup().get();
    shadowOf(Looper.getMainLooper()).idle();
    launcher.findViewById(R.id.toSetting).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    View root = launcher.getWindow().getDecorView();
    save(root, 720, 1280, "settings");
    launcher.findViewById(R.id.helpAbout).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    save(root, 720, 1280, "settings_about");
    // The whole settings content, unscrolled, to check every row.
    View card = launcher.findViewById(R.id.settingsCard);
    View content = ((android.view.ViewGroup) ((android.view.ViewGroup) card).getChildAt(2)).getChildAt(0);
    save(content, 680, 4000, "settings_full");
  }

  @Test
  public void bottomBar() throws Exception {
    Assume.assumeTrue(Boolean.getBoolean("screenshots"));
    Launcher launcher = Robolectric.buildActivity(Launcher.class).setup().get();
    shadowOf(Looper.getMainLooper()).idle();
    save(launcher.getWindow().getDecorView(), 720, 1280, "home");

    // Bottom bar in every battery style: normal, charging and low.
    View bar = (View) launcher.findViewById(R.id.toSetting).getParent();
    int[][] states = {{76, 0}, {76, android.os.BatteryManager.BATTERY_PLUGGED_USB}, {9, 0}};
    String[] stateNames = {"normal", "charging", "low"};
    String[] styleNames = {"icon", "ring", "text"};
    for (int style = 0; style < 3; style++) {
      launcher.config.batteryStyle = style;
      launcher.applyBatteryStyle();
      for (int s = 0; s < states.length; s++) {
        android.content.Intent battery = new android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED)
                .putExtra(android.os.BatteryManager.EXTRA_LEVEL, states[s][0])
                .putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                .putExtra(android.os.BatteryManager.EXTRA_PLUGGED, states[s][1])
                .putExtra(android.os.BatteryManager.EXTRA_STATUS, states[s][1] != 0
                        ? android.os.BatteryManager.BATTERY_STATUS_CHARGING : android.os.BatteryManager.BATTERY_STATUS_DISCHARGING);
        launcher.batteryLevelReceiver.onReceive(launcher, battery);
        save(bar, 720, 200, "bar_" + styleNames[style] + "_" + stateNames[s]);
      }
    }
  }

  /** Home screen for the README, with made-up apps and a public domain book (no personal data). */
  @Test
  @Config(qualifiers = "en-w411dp-h731dp-280dpi")
  public void readmeHome() throws Exception {
    Assume.assumeTrue(Boolean.getBoolean("screenshots"));
    android.content.Intent main = new android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER);
    String[] apps = {"KOReader", "Files", "Browser", "Calendar", "Notes", "Dictionary", "Settings", "Music"};
    org.robolectric.shadows.ShadowPackageManager pm = shadowOf(
            org.robolectric.RuntimeEnvironment.getApplication().getPackageManager());
    for (String app : apps) {
      String pkg = "org.example." + app.toLowerCase();
      android.content.pm.ResolveInfo info = new android.content.pm.ResolveInfo();
      info.activityInfo = new android.content.pm.ActivityInfo();
      info.activityInfo.packageName = pkg;
      info.activityInfo.name = pkg + ".Main";
      info.activityInfo.applicationInfo = new android.content.pm.ApplicationInfo();
      info.activityInfo.applicationInfo.packageName = pkg;
      info.nonLocalizedLabel = app;
      pm.addResolveInfoForIntent(main, info);
    }
    Launcher launcher = Robolectric.buildActivity(Launcher.class).setup().get();
    shadowOf(Looper.getMainLooper()).idle();

    // Fill the reading card by hand: the real one reads KOReader's files.
    launcher.findViewById(R.id.readingCard).setVisibility(View.VISIBLE);
    ((android.widget.TextView) launcher.findViewById(R.id.readingTitle)).setText("Pride and Prejudice");
    ((android.widget.TextView) launcher.findViewById(R.id.readingAuthor)).setText("Jane Austen");
    ((android.widget.ProgressBar) launcher.findViewById(R.id.readingProgress)).setProgress(420);
    ((android.widget.TextView) launcher.findViewById(R.id.readingStatus)).setText(
            launcher.getString(R.string.reading_progress, 42));
    android.content.Intent battery = new android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED)
            .putExtra(android.os.BatteryManager.EXTRA_LEVEL, 82)
            .putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            .putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING);
    launcher.batteryLevelReceiver.onReceive(launcher, battery);
    shadowOf(Looper.getMainLooper()).idle();
    save(launcher.getWindow().getDecorView(), 720, 1280, "readme_home");

    // With the system status bar on: no bottom bar, settings in the quick bar, line above the card.
    launcher.config.setStatusBarShowStatus(false);
    launcher.refreshQuickBar();
    launcher.applyBarsLayout();
    shadowOf(Looper.getMainLooper()).idle();
    save(launcher.getWindow().getDecorView(), 720, 1280, "home_system_bar");
  }

  /** Settings and light panel in English, for the README. */
  @Test
  @Config(qualifiers = "en-w411dp-h731dp-280dpi")
  public void readmeSettingsAndPanel() throws Exception {
    Assume.assumeTrue(Boolean.getBoolean("screenshots"));
    Launcher launcher = Robolectric.buildActivity(Launcher.class).setup().get();
    shadowOf(Looper.getMainLooper()).idle();
    launcher.findViewById(R.id.toSetting).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    save(launcher.getWindow().getDecorView(), 720, 1280, "readme_settings");
    View light = LayoutInflater.from(launcher).inflate(R.layout.panel_light, null);
    ((android.widget.TextView) light.findViewById(R.id.lightBrightnessLabel)).setText(launcher.getString(R.string.panel_light_brightness, 6));
    ((android.widget.TextView) light.findViewById(R.id.lightTemperatureLabel)).setText(launcher.getString(R.string.panel_light_temperature, 14));
    ((android.widget.ProgressBar) light.findViewById(R.id.lightBrightnessBar)).setProgress(6);
    ((android.widget.ProgressBar) light.findViewById(R.id.lightTemperatureBar)).setProgress(14);
    ((android.widget.TextView) light.findViewById(R.id.lightToggle)).setText(R.string.panel_light_off);
    save(light, 620, 1280, "readme_light");
  }

  @Test
  public void about() throws Exception {
    Assume.assumeTrue(Boolean.getBoolean("screenshots"));
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    View about = LayoutInflater.from(activity).inflate(R.layout.about_content, null);
    ((android.widget.TextView) about.findViewById(R.id.aboutVersion)).setText(activity.getString(R.string.about_memory, "0.7.1", "19,8", "9,4"));
    save(about, 620, 1280, "about");
  }

  /**
   * The quick bar with the six items of the device and labels of different lengths, in both
   * icon styles, with the Wi-Fi and FTP items on and off.
   */
  @Test
  public void quickBar() throws Exception {
    Assume.assumeTrue(Boolean.getBoolean("screenshots"));
    String[] labels = {"Home-WiFi", "FTP", "Light", "Contrast", "Refresh", "Lock Screen"};
    int[] on = {R.drawable.wifi_on, R.drawable.ftp_on, R.drawable.quick_brightness, R.drawable.quick_contrast,
            R.drawable.quick_clean, R.drawable.quick_lock};
    int[] off = {R.drawable.wifi_off, R.drawable.ftp_off, R.drawable.quick_brightness, R.drawable.quick_contrast,
            R.drawable.quick_clean, R.drawable.quick_lock};
    String[] styles = {"framed", "line"};
    try {
      for (int style = 0; style < styles.length; style++) {
        cn.modificator.launcher.modelController.QuickIcons.setStyle(style);
        for (int state = 0; state < 2; state++) {
          Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
          cn.modificator.launcher.widgets.QuickBarRow bar = new cn.modificator.launcher.widgets.QuickBarRow(activity);
          for (int i = 0; i < labels.length; i++) {
            View item = LayoutInflater.from(activity).inflate(R.layout.quick_item, bar, false);
            int icon = cn.modificator.launcher.modelController.QuickIcons.res(state == 0 ? on[i] : off[i]);
            ((android.widget.ImageView) item.findViewById(R.id.itemIcon)).setImageResource(icon);
            ((android.widget.TextView) item.findViewById(R.id.itemTitle)).setText(labels[i]);
            bar.addView(item);
          }
          // Attached to a window, or detached TextViews draw no text in Robolectric.
          activity.setContentView(bar, new android.view.ViewGroup.LayoutParams(
                  android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
          shadowOf(Looper.getMainLooper()).idle();
          save(bar, 720, 400, "quick_bar_" + styles[style] + (state == 0 ? "_on" : "_off"));
          // Same width for every item: evenly spaced icons.
          for (int i = 1; i < bar.getChildCount(); i++) {
            org.junit.Assert.assertEquals(bar.getChildAt(0).getWidth(), bar.getChildAt(i).getWidth());
            org.junit.Assert.assertEquals(bar.getChildAt(i - 1).getRight(), bar.getChildAt(i).getLeft());
          }
        }
      }
    } finally {
      cn.modificator.launcher.modelController.QuickIcons.setStyle(0);
    }
  }

  @Test
  public void panels() throws Exception {
    Assume.assumeTrue(Boolean.getBoolean("screenshots"));
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    View light = LayoutInflater.from(activity).inflate(R.layout.panel_light, null);
    ((android.widget.TextView) light.findViewById(R.id.lightBrightnessLabel)).setText(activity.getString(R.string.panel_light_brightness, 6));
    ((android.widget.TextView) light.findViewById(R.id.lightTemperatureLabel)).setText(activity.getString(R.string.panel_light_temperature, 14));
    ((android.widget.ProgressBar) light.findViewById(R.id.lightBrightnessBar)).setProgress(6);
    ((android.widget.ProgressBar) light.findViewById(R.id.lightTemperatureBar)).setProgress(14);
    ((android.widget.TextView) light.findViewById(R.id.lightToggle)).setText(R.string.panel_light_off);
    save(light, 620, 1280, "panel_light");
  }
}
