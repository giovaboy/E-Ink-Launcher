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
 * Renders screens to PNG at the inkPalm 5 resolution (720x1280, xhdpi), to look at layout
 * changes without the device. Only runs with -Dscreenshots=true; images go to
 * app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 27, qualifiers = "it-w360dp-h640dp-xhdpi")
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
    // The whole settings content, unscrolled, to check every row.
    View card = launcher.findViewById(R.id.settingsCard);
    View content = ((android.view.ViewGroup) ((android.view.ViewGroup) card).getChildAt(2)).getChildAt(0);
    save(content, 680, 4000, "settings_full");
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
