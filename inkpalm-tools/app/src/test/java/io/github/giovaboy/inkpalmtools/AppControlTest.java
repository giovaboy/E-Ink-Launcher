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
import android.content.pm.PackageManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class AppControlTest {
  private Application context;

  private void install(String packageName) {
    PackageInfo info = new PackageInfo();
    info.packageName = packageName;
    info.applicationInfo = new ApplicationInfo();
    info.applicationInfo.packageName = packageName;
    info.applicationInfo.enabled = true;
    shadowOf(context.getPackageManager()).installPackage(info);
  }

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    install("com.jd.app.reader");         // suggested
    install("com.moan.moanwm");           // E-Ink Launcher: protected
    install("org.koreader.launcher");     // neither
  }

  private static AppControl.App find(List<AppControl.App> apps, String packageName) {
    for (AppControl.App app : apps) {
      if (app.packageName.equals(packageName)) return app;
    }
    return null;
  }

  @Test
  public void suggestedListOnlyHasVendorApps() {
    List<AppControl.App> apps = AppControl.list(context, true);
    assertNotNull(find(apps, "com.jd.app.reader"));
    assertNull(find(apps, "org.koreader.launcher"));
    assertNull(find(apps, "com.moan.moanwm"));
  }

  @Test
  public void launcherAndThisAppAreProtected() {
    List<AppControl.App> apps = AppControl.list(context, false);
    AppControl.App launcher = find(apps, "com.moan.moanwm");
    assertTrue(launcher.isProtected);
    assertEquals("protected", AppControl.setEnabled(context, launcher, false));
    assertTrue(find(apps, context.getPackageName()).isProtected);
  }

  @Test
  public void disablingAndEnablingChangesTheState() {
    AppControl.App app = find(AppControl.list(context, true), "com.jd.app.reader");
    assertNull(AppControl.setEnabled(context, app, false));
    assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            context.getPackageManager().getApplicationEnabledSetting("com.jd.app.reader"));
    assertFalse(find(AppControl.list(context, true), "com.jd.app.reader").enabled);
    assertNull(AppControl.setEnabled(context, app, true));
    assertTrue(find(AppControl.list(context, true), "com.jd.app.reader").enabled);
  }
}
