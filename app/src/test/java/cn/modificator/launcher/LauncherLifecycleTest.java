package cn.modificator.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentCallbacks2;
import android.os.Looper;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.time.Duration;

/**
 * Smoke tests of the launcher lifecycle paths that crashed on the device: there is no adb
 * there, so these catch startup and recreation crashes before an APK is copied over.
 * Robolectric runs the debug (non-minified) build: R8 issues still need checking on the
 * release APK.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27) // Android 8.1, like the inkPalm 5
public class LauncherLifecycleTest {

  private ActivityController<Launcher> controller;

  @After
  public void tearDown() {
    if (controller != null) {
      LocaleHelper.setLanguage(controller.get(), "");
    }
  }

  private Launcher start() {
    controller = Robolectric.buildActivity(Launcher.class).setup();
    idle();
    return controller.get();
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  private static void openSettings(Launcher launcher) {
    launcher.findViewById(R.id.toSetting).performClick();
    idle();
  }

  @Test
  public void startsAndShowsTheGrid() {
    Launcher launcher = start();
    assertNotNull(launcher.findViewById(R.id.mList));
  }

  @Test
  public void survivesRecreationWithSettingsOpen() {
    // The system restores SettingFragment by itself on configuration changes.
    openSettings(start());
    controller.recreate();
    idle();
    openSettings(controller.get());
  }

  @Test
  public void settingsApplyChangesLiveAndStayOpen() {
    Launcher launcher = start();
    openSettings(launcher);
    int columns = launcher.config.colNum;
    boolean readingCard = launcher.config.showReadingCard;

    launcher.findViewById(columns < 10 ? R.id.colUp : R.id.colDown).performClick();
    launcher.findViewById(R.id.toggleReadingCard).performClick();
    idle();

    assertEquals(columns < 10 ? columns + 1 : columns - 1, launcher.config.colNum);
    assertEquals(!readingCard, launcher.config.showReadingCard);
    assertEquals(!readingCard, ((android.widget.Switch) launcher.findViewById(R.id.switchReadingCard)).isChecked());
    // Still open: the settings card is on screen.
    assertNotNull(launcher.findViewById(R.id.settingsCard));
  }

  @Test
  public void recentlyUsedSortCanBeChosen() {
    Launcher launcher = start();
    launcher.config.setSortFlags(cn.modificator.launcher.modelController.LauncherItemInfoComparator.SORT_MODE_RECENTLY_USED
            | cn.modificator.launcher.modelController.LauncherItemInfoComparator.SORT_ORDER_DESC);
    openSettings(launcher);
    // The ninth option is shown, and sorting without usage access doesn't crash.
    assertEquals(launcher.getResources().getStringArray(R.array.sort_method)[8],
            ((android.widget.TextView) launcher.findViewById(R.id.sortMethodValue)).getText().toString());
    launcher.itemCenter.refresh(cn.modificator.launcher.modelController.ItemCenter.REFRESH_LEVEL_2_VISIBLE_SORT);
  }

  @Test
  public void quickBarHoldsTheSpecialItemsAndHidesThem() {
    Launcher launcher = start();
    android.view.ViewGroup bar = launcher.findViewById(R.id.quickBar);
    // Not a Moaan device here: Wi-Fi, FTP and lock only, in the bar and not in the grid.
    assertEquals(3, bar.getChildCount());
    assertEquals(cn.modificator.launcher.modelController.ItemCenter.WIFI_ITEM_ID, bar.getChildAt(0).getTag());
    assertEquals(cn.modificator.launcher.modelController.ItemCenter.FTP_ITEM_ID, bar.getChildAt(1).getTag());
    for (cn.modificator.launcher.modelController.LauncherItemInfo item : launcher.itemCenter.getAllItems()) {
      assertEquals(false, item.type == cn.modificator.launcher.modelController.LauncherItemInfo.TYPE_SPECIAL);
    }

    openSettings(launcher);
    android.view.ViewGroup toggles = launcher.findViewById(R.id.quickItemToggles);
    assertEquals(3, toggles.getChildCount());
    toggles.getChildAt(2).performClick(); // hide lock
    idle();
    assertEquals(2, bar.getChildCount());
    toggles.getChildAt(0).performClick(); // hide Wi-Fi
    toggles.getChildAt(1).performClick(); // hide FTP
    idle();
    assertEquals(android.view.View.GONE, launcher.findViewById(R.id.quickBarContainer).getVisibility());
    toggles.getChildAt(0).performClick(); // show them again
    toggles.getChildAt(1).performClick();
    toggles.getChildAt(2).performClick();
    idle();
    assertEquals(3, bar.getChildCount());
  }

  @Test
  public void readingCardOptionOnlyWithKOReader() {
    Launcher launcher = start();
    openSettings(launcher);
    assertEquals(android.view.View.GONE, launcher.findViewById(R.id.toggleReadingCard).getVisibility());
    launcher.onBackPressed();
    idle();

    // Install a fake KOReader launcher activity.
    android.content.pm.ResolveInfo koreader = new android.content.pm.ResolveInfo();
    koreader.activityInfo = new android.content.pm.ActivityInfo();
    koreader.activityInfo.packageName = "org.koreader.launcher";
    koreader.activityInfo.name = "org.koreader.launcher.MainActivity";
    shadowOf(launcher.getPackageManager()).addResolveInfoForIntent(
            new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER),
            koreader);
    openSettings(launcher);
    assertEquals(android.view.View.VISIBLE, launcher.findViewById(R.id.toggleReadingCard).getVisibility());
  }

  @Test
  public void survivesLanguageChange() {
    Launcher launcher = start();
    openSettings(launcher);
    LocaleHelper.setLanguage(launcher, "it");
    launcher.applyLanguageChange();
    idle();
    controller.recreate();
    idle();
    TextView clock = controller.get().findViewById(R.id.textClock);
    assertNotNull(clock);
  }

  @Test
  public void releasesMemoryInBackgroundAndComesBack() {
    Launcher launcher = start();
    controller.pause().stop();
    launcher.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6)); // background measurement
    controller.restart().resume();
    idle();
  }

  @Test
  public void deviceProbeWritesAReportWithoutTheVendorNodes() throws Exception {
    java.io.File report = DeviceProbe.run(start());
    String text = new String(java.nio.file.Files.readAllBytes(report.toPath()), "UTF-8");
    assertEquals(true, text.contains("/proc/lm3630a/warm_level: exists=false"));
    assertEquals(true, text.contains("== android.app.ActivityManager"));
  }

  @Test
  public void backKeyClosesTheSettings() {
    Launcher launcher = start();
    openSettings(launcher);
    assertEquals(1, launcher.getSupportFragmentManager().getBackStackEntryCount());
    pressBack(launcher);
    assertEquals(0, launcher.getSupportFragmentManager().getBackStackEntryCount());
    // On the home screen back does nothing and the launcher stays.
    pressBack(launcher);
    assertEquals(false, launcher.isFinishing());
  }

  @Test
  public void aboutOpensInsideTheSettingsAndBackReturns() {
    Launcher launcher = start();
    openSettings(launcher);
    launcher.findViewById(R.id.helpAbout).performClick();
    idle();
    assertEquals(android.view.View.VISIBLE, launcher.findViewById(R.id.aboutContent).getVisibility());
    assertEquals(android.view.View.GONE, launcher.findViewById(R.id.settingsList).getVisibility());
    pressBack(launcher); // back to the settings list, still open
    assertEquals(android.view.View.VISIBLE, launcher.findViewById(R.id.settingsList).getVisibility());
    assertEquals(1, launcher.getSupportFragmentManager().getBackStackEntryCount());
    pressBack(launcher); // closes the settings
    assertEquals(0, launcher.getSupportFragmentManager().getBackStackEntryCount());
  }

  @Test
  public void backgroundMemoryIsRecordedOnStop() {
    Launcher launcher = start();
    controller.pause().stop();
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
    String text = MemoryStats.describe(launcher);
    assertEquals(false, text.contains("–"));
  }

  private static void pressBack(Launcher launcher) {
    launcher.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK));
    launcher.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK));
    idle();
  }

  @Test
  public void devToolsReportVendorMethodsMissing() {
    Launcher launcher = start();
    assertEquals(false, DevTools.setSystemProp(launcher, "sys.usb.config", "mtp,adb"));
  }

  @Test
  public void showsMemoryInAboutDialog() {
    String text = MemoryStats.describe(start());
    assertNotNull(text);
    assertEquals(true, text.contains(BuildConfig.VERSION_NAME));
  }

  @Test
  public void ftpItemNeedsWifiAndShowsItsState() {
    Launcher launcher = start();
    android.view.View ftp = launcher.findViewById(R.id.quickBar).findViewWithTag(cn.modificator.launcher.modelController.ItemCenter.FTP_ITEM_ID);
    assertEquals("FTP", ((TextView) ftp.findViewById(R.id.itemTitle)).getText().toString());
    assertEquals(launcher.getString(R.string.item_ftp_off), ftp.getContentDescription());
    // Robolectric grants storage access but has no Wi-Fi: the server stays off, with a hint.
    shadowOf(launcher.getApplication()).grantPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    ftp.performClick();
    idle();
    assertEquals(false, cn.modificator.launcher.ftpservice.FTPService.isRunning());
    assertEquals(launcher.getString(R.string.toast_need_wifi_connnect), org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    ftp.performLongClick();
    idle();
    assertEquals(launcher.getString(R.string.item_ftp_off), org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
  }

  @Test
  public void quickIconStyleSwitchesTheBarIcons() {
    Launcher launcher = start();
    android.view.View ftp = launcher.findViewById(R.id.quickBar).findViewWithTag(cn.modificator.launcher.modelController.ItemCenter.FTP_ITEM_ID);
    android.widget.ImageView icon = ftp.findViewById(R.id.itemIcon);
    assertEquals(R.drawable.ftp_off, shadowOf(icon.getDrawable()).getCreatedFromResId());
    openSettings(launcher);
    android.view.ViewGroup segments = launcher.findViewById(R.id.quickIconSegments);
    segments.getChildAt(1).performClick(); // Line
    idle();
    assertEquals(cn.modificator.launcher.modelController.QuickIcons.STYLE_LINE, launcher.config.quickIconStyle);
    assertEquals(true, segments.getChildAt(1).isSelected());
    icon = launcher.findViewById(R.id.quickBar).findViewWithTag(cn.modificator.launcher.modelController.ItemCenter.FTP_ITEM_ID).findViewById(R.id.itemIcon);
    assertEquals(R.drawable.ftp_off_line, shadowOf(icon.getDrawable()).getCreatedFromResId());
    // Every item, also those whose icon was already loaded (lock), not only Wi-Fi and FTP.
    android.widget.ImageView lock = launcher.findViewById(R.id.quickBar)
            .findViewWithTag(cn.modificator.launcher.modelController.ItemCenter.ONE_KEY_LOCK_ITEM_ID).findViewById(R.id.itemIcon);
    assertEquals(R.drawable.quick_lock_line, shadowOf(lock.getDrawable()).getCreatedFromResId());
    android.widget.ImageView wifi = launcher.findViewById(R.id.quickBar)
            .findViewWithTag(cn.modificator.launcher.modelController.ItemCenter.WIFI_ITEM_ID).findViewById(R.id.itemIcon);
    int wifiRes = shadowOf(wifi.getDrawable()).getCreatedFromResId();
    assertEquals(true, wifiRes == R.drawable.wifi_on_line || wifiRes == R.drawable.wifi_off_line);
    segments.getChildAt(0).performClick(); // back to Framed
    idle();
    lock = launcher.findViewById(R.id.quickBar)
            .findViewWithTag(cn.modificator.launcher.modelController.ItemCenter.ONE_KEY_LOCK_ITEM_ID).findViewById(R.id.itemIcon);
    assertEquals(R.drawable.quick_lock, shadowOf(lock.getDrawable()).getCreatedFromResId());
    assertEquals(cn.modificator.launcher.modelController.QuickIcons.STYLE_FRAMED, launcher.config.quickIconStyle);
  }

  @Test
  public void systemStatusBarReplacesTheBottomBar() {
    Launcher launcher = start();
    android.view.ViewGroup bar = launcher.findViewById(R.id.quickBar);
    String settingsId = cn.modificator.launcher.modelController.ItemCenter.SETTINGS_ITEM_ID;
    assertEquals(android.view.View.VISIBLE, launcher.findViewById(R.id.bottomBar).getVisibility());
    assertEquals(null, bar.findViewWithTag(settingsId));

    // Turn the system status bar on from the settings, then close them.
    openSettings(launcher);
    launcher.findViewById(R.id.toggleStatusBar).performClick();
    idle();
    pressBack(launcher);
    assertEquals(android.view.View.GONE, launcher.findViewById(R.id.bottomBar).getVisibility());
    assertEquals(android.view.View.GONE, launcher.findViewById(R.id.bottomBarDivider).getVisibility());
    assertEquals(android.view.View.VISIBLE, launcher.findViewById(R.id.readingCardTopDivider).getVisibility());
    android.view.View settings = bar.getChildAt(bar.getChildCount() - 1);
    assertEquals(settingsId, settings.getTag());
    // Not offered among the quick bar switches: it can't be hidden.
    openSettings(launcher);
    android.view.ViewGroup toggles = launcher.findViewById(R.id.quickItemToggles);
    assertEquals(3, toggles.getChildCount());
    pressBack(launcher);

    // The quick bar item opens the settings.
    settings.performClick();
    idle();
    assertEquals(1, launcher.getSupportFragmentManager().getBackStackEntryCount());
    pressBack(launcher);

    // Managing apps brings the bottom bar back for its "Done" button.
    launcher.findViewById(R.id.deleteFinish).setVisibility(android.view.View.VISIBLE);
    launcher.applyBarsLayout();
    assertEquals(android.view.View.VISIBLE, launcher.findViewById(R.id.bottomBar).getVisibility());
    launcher.findViewById(R.id.deleteFinish).performClick();
    idle();
    assertEquals(android.view.View.GONE, launcher.findViewById(R.id.bottomBar).getVisibility());

    // Back to full screen: everything as before.
    openSettings(launcher);
    launcher.findViewById(R.id.toggleStatusBar).performClick();
    idle();
    pressBack(launcher);
    assertEquals(android.view.View.VISIBLE, launcher.findViewById(R.id.bottomBar).getVisibility());
    assertEquals(null, bar.findViewWithTag(settingsId));
  }
}
