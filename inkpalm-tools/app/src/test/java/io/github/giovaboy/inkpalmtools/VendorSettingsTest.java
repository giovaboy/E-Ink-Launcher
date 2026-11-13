package io.github.giovaboy.inkpalmtools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.provider.Settings;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class VendorSettingsTest {
  @Test
  public void wifiInStandbyIsOffUntilSetAndWritesTheVendorKey() {
    Application context = RuntimeEnvironment.getApplication();
    assertFalse(VendorSettings.keepsWifiInStandby(context)); // unset: the firmware closes Wi-Fi
    assertNull(VendorSettings.setKeepWifiInStandby(context, true));
    assertEquals(0, Settings.Global.getInt(context.getContentResolver(), "com.moan.closewifi", -1));
    assertTrue(VendorSettings.keepsWifiInStandby(context));
    assertNull(VendorSettings.setKeepWifiInStandby(context, false));
    assertEquals(1, Settings.Global.getInt(context.getContentResolver(), "com.moan.closewifi", -1));
  }

  @Test
  public void switchIsHiddenOutsideMoaanFirmwares() {
    assertFalse(VendorSettings.isMoaan());
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    assertEquals(View.GONE, activity.findViewById(R.id.wifiStandby).getVisibility());
  }

  @Test
  public void moaanSettingsShortcutOpensTheStockMenu() {
    MoaanSettingsActivity activity = Robolectric.buildActivity(MoaanSettingsActivity.class).setup().get();
    android.content.Intent next = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
    assertEquals(MoaanSettingsActivity.MOAAN_SETTINGS, next.getComponent());
    assertTrue(activity.isFinishing());
  }
}
