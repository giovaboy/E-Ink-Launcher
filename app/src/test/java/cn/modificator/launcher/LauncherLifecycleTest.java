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
    // Not a Moaan device here: Wi-Fi and lock only, in the bar and not in the grid.
    assertEquals(2, bar.getChildCount());
    assertEquals(cn.modificator.launcher.modelController.ItemCenter.WIFI_ITEM_ID, bar.getChildAt(0).getTag());
    for (cn.modificator.launcher.modelController.LauncherItemInfo item : launcher.itemCenter.getAllItems()) {
      assertEquals(false, item.type == cn.modificator.launcher.modelController.LauncherItemInfo.TYPE_SPECIAL);
    }

    openSettings(launcher);
    android.view.ViewGroup toggles = launcher.findViewById(R.id.quickItemToggles);
    assertEquals(2, toggles.getChildCount());
    toggles.getChildAt(1).performClick(); // hide lock
    idle();
    assertEquals(1, bar.getChildCount());
    toggles.getChildAt(0).performClick(); // hide Wi-Fi
    idle();
    assertEquals(android.view.View.GONE, launcher.findViewById(R.id.quickBarContainer).getVisibility());
    toggles.getChildAt(0).performClick(); // show them again
    toggles.getChildAt(1).performClick();
    idle();
    assertEquals(2, bar.getChildCount());
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
}
