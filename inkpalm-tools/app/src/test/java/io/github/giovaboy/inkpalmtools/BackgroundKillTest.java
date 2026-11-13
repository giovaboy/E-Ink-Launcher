package io.github.giovaboy.inkpalmtools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.widget.ListView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/** Robolectric is stock Android: no freeze setting, which must be reported, not crash. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class BackgroundKillTest {
  private Application context;

  private void install(String packageName, boolean system) {
    PackageInfo info = new PackageInfo();
    info.packageName = packageName;
    info.applicationInfo = new ApplicationInfo();
    info.applicationInfo.packageName = packageName;
    info.applicationInfo.enabled = true;
    info.applicationInfo.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
    shadowOf(context.getPackageManager()).installPackage(info);
  }

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    install("com.moan.moanwm", false);
    install("com.android.settings", true);
  }

  private static BackgroundKill.App find(List<BackgroundKill.App> apps, String packageName) {
    for (BackgroundKill.App app : apps) {
      if (app.packageName.equals(packageName)) return app;
    }
    return null;
  }

  @Test
  public void listsOnlyNonSystemApps() {
    List<BackgroundKill.App> apps = BackgroundKill.list(context);
    assertNotNull(find(apps, "com.moan.moanwm"));
    assertNull(find(apps, "com.android.settings"));
    assertNull(find(apps, context.getPackageName()));
  }

  @Test
  public void withoutTheVendorSettingNothingChanges() {
    BackgroundKill.App app = find(BackgroundKill.list(context), "com.moan.moanwm");
    assertNull(app.freeze);
    assertFalse(app.keepsRunning());
    assertEquals(context.getString(R.string.background_unsupported), BackgroundKill.set(context, app, true));
    assertEquals(0, BackgroundKill.notFrozenValue());
    assertTrue(BackgroundKill.describeApi().contains("not a Moaan firmware"));
  }

  @Test
  public void screenOpens() {
    BackgroundActivity activity = Robolectric.buildActivity(BackgroundActivity.class).setup().get();
    assertNotNull(((ListView) activity.findViewById(R.id.list)).getAdapter());
  }
}
