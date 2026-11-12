package cn.modificator.launcher.eink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import cn.modificator.launcher.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class EinkPanelsTest {

  @Test
  public void ledTablesAreCompleteAndOffAtBrightnessZero() {
    int[][][] tables = {LedTables.PWM_VALUE, LedTables.COLD_CURRENT, LedTables.COLD_BRIGHTNESS,
            LedTables.WARM_CURRENT, LedTables.WARM_BRIGHTNESS};
    for (int[][] table : tables) {
      assertEquals(EinkControls.LIGHT_MAX + 1, table.length);
      for (int[] row : table) {
        assertEquals(EinkControls.LIGHT_MAX + 1, row.length);
      }
    }
    // Brightness 0 (first column) must drive no current through either LED string.
    for (int temperature = 0; temperature <= EinkControls.LIGHT_MAX; temperature++) {
      assertEquals(0, LedTables.COLD_CURRENT[temperature][0]);
      assertEquals(0, LedTables.WARM_CURRENT[temperature][0]);
      assertEquals(0, LedTables.COLD_BRIGHTNESS[temperature][0]);
      assertEquals(0, LedTables.WARM_BRIGHTNESS[temperature][0]);
    }
  }

  @Test
  public void panelLayoutsInflate() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    assertNotNull(LayoutInflater.from(activity).inflate(R.layout.panel_light, null));
    assertNotNull(LayoutInflater.from(activity).inflate(R.layout.panel_contrast, null));
  }

  @Test
  public void withoutVendorMethodsTheSystemPanelsAreOpened() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    assertFalse(EinkControls.isSupported(activity));

    EinkPanels.showLight(activity);
    Intent light = shadowOf(activity).getNextStartedActivity();
    assertEquals("com.android.systemui.moan.MoanBrightnessDialogActivity", light.getComponent().getClassName());

    EinkPanels.showContrast(activity);
    Intent contrast = shadowOf(activity).getNextStartedActivity();
    assertEquals("com.android.systemui.einksettings.EinkSettingsActivity", contrast.getComponent().getClassName());
  }
}
