package cn.modificator.launcher;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import cn.modificator.launcher.ftpservice.FTPReceiver;
import cn.modificator.launcher.ftpservice.FTPService;
import cn.modificator.launcher.modelController.AdminReceiver;
import cn.modificator.launcher.modelController.ItemCenter;
import cn.modificator.launcher.modelController.HomeEntranceService;
import cn.modificator.launcher.modelController.LauncherItemInfo;
import cn.modificator.launcher.modelController.WifiControl;
import cn.modificator.launcher.reading.ReadingCard;
import cn.modificator.launcher.widgets.BatteryView;
import cn.modificator.launcher.widgets.EInkLauncherView;

/**
 * Created by mod on 16-4-22.
 */
public class Launcher extends FragmentActivity {
  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(LocaleHelper.wrap(newBase));
  }


  public static final String SHORTCUT_ITEMS_KEY = "shortcutItemsKey";
  public static final String SHORTCUT_ITEM_KEY = "shortcutItemKey";
  public static final String ROW_NUM_KEY = "rowNumKey";
  public static final String COL_NUM_KEY = "colNumKey";
  public static final String SORT_FLAGS_KEY = "sortFlagsKey";
  public static final String PRIORITY_KEY = "priorityKey";
  public static final String ITEM_TITLE_LINES_KEY = "itemTitleShowLines";
  public static final String HIDDEN_ITEM_IDS_KEY = "hiddenItemIds";
  public static final String SHOW_STATUS_BAR_KEY = "showStatusBarKey";
  public static final String SHOW_CUSTOM_ICON_KEY = "showCustomIconKey";
  public static final String SHOW_READING_CARD_KEY = "showReadingCardKey";
  public static final String BATTERY_STYLE_KEY = "batteryStyleKey";
  public static final String FONT_SIZE_KEY = "fontSizeKey";
  public static final String HIDE_DIVIDER_KEY = "hideDividerKey";

  public static final String DO_MANAGE_APP_KEY = "doManageAppKey";
  public static final String DO_DELETE_SHORTCUT_KEY = "doDeleteShortcutKey";

  public static final String LAUNCHER_ACTION = BuildConfig.APPLICATION_ID + ".launcherAction";

  // TODO initialize isFirstRun

  EInkLauncherView launcherView;
  ItemCenter itemCenter = null;
  Config config;
  LauncherUpdateReceiver updateReceiver;
  TextView pageStatus;
  BatteryView batteryProgress;
  TextView batteryStatus, batteryPercent, textClock, textTime;
  private int batteryLevel = -1;
  private int batteryStatusValue = -1;
  private int batteryHealth = -1;
  private boolean batteryPlugged;

  BroadcastReceiver timeListener;
  Calendar mCalendar;

  DevicePolicyManager policyManager;
  File iconFile;
  boolean isChina = true;
  FTPReceiver ftpReceiver = new FTPReceiver();
  private final Handler memoryHandler = new Handler(Looper.getMainLooper());
  private boolean isResumed;
  private ReadingCard readingCard;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.launcher_activity);
    config = new Config(this);
    WifiControl.init(this);
    toggleStatusBar();
    if (getExternalCacheDir() != null) {
      iconFile = new File(getExternalCacheDir().getParentFile().getParentFile().getParentFile().getParentFile(), "E-Ink Launcher" + File.separator + "icon");
      if (!iconFile.exists()) {
        iconFile.mkdir();
      }
    }

    isChina = getResources().getConfiguration().locale.getCountry().equals("CN");

    initView();
    readingCard = new ReadingCard(this, findViewById(R.id.readingCard), config.showReadingCard);
    checkLaunchHomeNotification();
  }


  private void initView() {
    policyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
    launcherView = findViewById(R.id.mList);
    pageStatus = findViewById(R.id.pageStatus);
    batteryProgress = findViewById(R.id.batteryProgress);
    batteryStatus = findViewById(R.id.batteryStatus);
    batteryPercent = findViewById(R.id.batteryPercent);
    applyBatteryStyle();
    textClock = findViewById(R.id.textClock);
    textTime = findViewById(R.id.textTime);
    (this.<ImageView>findViewById(R.id.toSetting)).setImageDrawable(Utils.tintDrawable(getResources().getDrawable(R.drawable.navibar_icon_settings_highlight), ColorStateList.valueOf(0xff000000)));

    itemCenter = new ItemCenter(this, config);
    itemCenter.setLauncherView(launcherView);
    itemCenter.setTemporaryHiddenItemIds(config.getHiddenItemIds());
    itemCenter.setTvPageStatus(pageStatus);
    try {
      itemCenter.setIsSystemApp(!isUserApp(getPackageManager().getPackageInfo(getPackageName(), 0)));
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }

    launcherView.setShouldHideDivider(config.getDividerHideStatus());
    launcherView.setFontSize(config.getFontSize());

    // Load the previously saved grid size
    config.getColNum();
    config.getRowNum();

    itemCenter.refresh(ItemCenter.REFRESH_LEVEL_0_ALL, EInkLauncherView.REFRESH_LEVEL_0_LAYOUT);

    findViewById(R.id.previousPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        itemCenter.showPreviousPage();
      }
    });
    findViewById(R.id.nextPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        itemCenter.showNextPage();
      }
    });
    findViewById(R.id.toSetting).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        SettingFragment sf = new SettingFragment();
        sf.config = config;
        sf.itemCenter = itemCenter;
        getSupportFragmentManager().beginTransaction()
                  .replace(android.R.id.content, sf)
                  .addToBackStack(null)
                  .commit();
      }
    });
    findViewById(R.id.deleteFinish).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        itemCenter.setInManagingState(false);
        config.setHiddenItemIds(itemCenter.getTemporaryHiddenItemIds());
        itemCenter.refresh(ItemCenter.REFRESH_LEVEL_0_ALL, EInkLauncherView.REFRESH_LEVEL_1_CURRENT_ITEMS);
        v.setVisibility(View.GONE);
      }
    });

//        android:format12Hour="yyyy-MM-dd aahh:mm EEEE"
//        android:format24Hour="yyyy-MM-dd aahh:mm EEEE"

    mCalendar = Calendar.getInstance();

    updateTimeShow();

    updateReceiver = new LauncherUpdateReceiver();
    IntentFilter filter = new IntentFilter();
    filter.addAction(LAUNCHER_ACTION);
    registerReceiver(updateReceiver, filter);


    IntentFilter appChangeFilter = new IntentFilter();
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
    appChangeFilter.addDataScheme("package");
    registerReceiver(appChangedReceiver, appChangeFilter);
  }

  private void updateRowNum(int rowNum) {
    config.setRowNum(rowNum);
    itemCenter.refresh(ItemCenter.REFRESH_LEVEL_3_PAGE, EInkLauncherView.REFRESH_LEVEL_0_LAYOUT);
  }

  private void updateColNum(int colNum) {
    config.setColNum(colNum);
    itemCenter.refresh(ItemCenter.REFRESH_LEVEL_3_PAGE, EInkLauncherView.REFRESH_LEVEL_0_LAYOUT);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
      itemCenter.showPreviousPage();
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
      itemCenter.showNextPage();
    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  @Override
  protected void onResume() {
    super.onResume();
    isResumed = true;
    IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    registerReceiver(batteryLevelReceiver, batteryLevelFilter);
    timeListener = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        updateTimeShow();
      }
    };
    updateTimeShow();

    registerReceiver(timeListener, new IntentFilter(Intent.ACTION_TIME_TICK));
    if (itemCenter != null && launcherView != null) {
      itemCenter.refresh(ItemCenter.REFRESH_LEVEL_1_REPLACED_ICONS);
    }
    if (readingCard != null) {
      readingCard.refresh();
    }
    detectUSB();

    IntentFilter ftpIntentFilter = new IntentFilter(FTPService.ACTION_START_FTPSERVER);
    ftpIntentFilter.addAction(FTPService.ACTION_STOP_FTPSERVER);
    registerReceiver(ftpReceiver,ftpIntentFilter);
  }

  /**
   * Refreshes the clock and date shown in the status bar
   */
  private void updateTimeShow() {
    if (textClock != null && mCalendar != null) {
      boolean is24Hour = DateFormat.is24HourFormat(this);
      mCalendar.setTimeInMillis(System.currentTimeMillis());

      Locale locale = getResources().getConfiguration().locale;
      // Date and time are separate views so that only the date gets shortened when space runs out.
      textClock.setText(new SimpleDateFormat(getString(R.string.clock_date_pattern), locale).format(mCalendar.getTime()));

      StringBuilder timeFormatTextBuilder = new StringBuilder();
      if (!is24Hour && isChina) {
        timeFormatTextBuilder.append(Utils.getAMPMCNString(mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.AM_PM)));
      }
      if (is24Hour) {
        timeFormatTextBuilder.append("HH:mm");
      } else {
        timeFormatTextBuilder.append("hh:mm");
      }
      if (!is24Hour && !isChina) {
        timeFormatTextBuilder.append(" a");
      }
      textTime.setText(getString(R.string.clock_time_prefix)
              + new SimpleDateFormat(timeFormatTextBuilder.toString(), locale).format(mCalendar.getTime()));
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    isResumed = false;
    unregisterReceiver(batteryLevelReceiver);
    unregisterReceiver(timeListener);
    unregisterReceiver(usbReceiver);
    unregisterReceiver(ftpReceiver);
  }

  /**
   * When another app (typically a reader) is in front, drop the icons so the launcher, which
   * is kept alive by HomeEntranceService, leaves as much RAM as possible to it. onResume()
   * refreshes the current page, which loads them again.
   */
  @Override
  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (level >= TRIM_MEMORY_UI_HIDDEN && itemCenter != null && launcherView != null) {
      itemCenter.releaseAllIcons();
      launcherView.clearIcons();
      if (readingCard != null) {
        readingCard.releaseMemory();
      }
      memoryHandler.removeCallbacksAndMessages(null);
      memoryHandler.postDelayed(() -> {
        if (!isResumed) {
          MemoryStats.recordBackgroundUsage(Launcher.this);
        }
      }, 5000);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    memoryHandler.removeCallbacksAndMessages(null);
    unregisterReceiver(updateReceiver);
    unregisterReceiver(appChangedReceiver);
    try {
      unregisterReceiver(usbReceiver);
    } catch (Throwable throwable) {
      throwable.printStackTrace();
    }
  }


  class LauncherUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      Bundle bundle = intent.getExtras();
      if (bundle.containsKey(SHORTCUT_ITEM_KEY)) {
        LauncherItemInfo itemInfo = Config.gson.fromJson(bundle.getString(SHORTCUT_ITEM_KEY),
                LauncherItemInfo.class);
        ArrayList<LauncherItemInfo> shortcuts = new ArrayList<>();
        shortcuts.addAll(config.getShortcutItems());
        shortcuts.add(itemInfo);
        config.setShortcutItems(shortcuts);
        itemCenter.refresh(ItemCenter.REFRESH_LEVEL_0_ALL);
      } else if (bundle.containsKey(ROW_NUM_KEY)) {
        updateRowNum(bundle.getInt(ROW_NUM_KEY));
      } else if (bundle.containsKey(COL_NUM_KEY)) {
        updateColNum(bundle.getInt(COL_NUM_KEY));
      } else if (bundle.containsKey(SORT_FLAGS_KEY)) {
        int sortFlags = bundle.getInt(SORT_FLAGS_KEY);
        config.setSortFlags(sortFlags);
        itemCenter.refresh(ItemCenter.REFRESH_LEVEL_2_VISIBLE_SORT);
      } else if (bundle.containsKey(PRIORITY_KEY)) {
        config.setPriorityMap((Map<String, Integer>) bundle.getSerializable(PRIORITY_KEY));
        itemCenter.refresh(ItemCenter.REFRESH_LEVEL_1_PRIORITIES);
      } else if (bundle.containsKey(ITEM_TITLE_LINES_KEY)) {
        int lines = bundle.getInt(ITEM_TITLE_LINES_KEY);
        if (lines == 3) {
          lines = Integer.MAX_VALUE;
        }
        config.setItemTitleLines(lines);
        launcherView.refreshItemTitleLines();
      } else if (bundle.containsKey(HIDDEN_ITEM_IDS_KEY)) {
        itemCenter.refresh(ItemCenter.REFRESH_LEVEL_2_VISIBLE_SORT);
      } else if (bundle.containsKey(SHOW_STATUS_BAR_KEY)) {
        config.setStatusBarShowStatus(bundle.getBoolean(SHOW_STATUS_BAR_KEY));
        toggleStatusBar();
      } else if (bundle.containsKey(BATTERY_STYLE_KEY)) {
        config.setBatteryStyle(bundle.getInt(BATTERY_STYLE_KEY));
        applyBatteryStyle();
      } else if (bundle.containsKey(SHOW_READING_CARD_KEY)) {
        config.setReadingCardShowStatus(bundle.getBoolean(SHOW_READING_CARD_KEY));
        readingCard.setEnabled(config.showReadingCard);
      } else if (bundle.containsKey(SHOW_CUSTOM_ICON_KEY)) {
        config.setCustomIconShowStatus(bundle.getBoolean(SHOW_CUSTOM_ICON_KEY));
        itemCenter.refresh(ItemCenter.REFRESH_LEVEL_1_REPLACED_ICONS);
      } else if (bundle.containsKey(FONT_SIZE_KEY)) {
        launcherView.setFontSize(bundle.getFloat(FONT_SIZE_KEY));
      } else if (bundle.containsKey(HIDE_DIVIDER_KEY)) {
        launcherView.setShouldHideDivider(bundle.getBoolean(HIDE_DIVIDER_KEY));
        config.setDividerHideStatus(bundle.getBoolean(HIDE_DIVIDER_KEY));
        launcherView.refresh(EInkLauncherView.REFRESH_LEVEL_0_LAYOUT);
      } else if (bundle.containsKey(DO_MANAGE_APP_KEY)) {
        itemCenter.setInManagingState(true);
        itemCenter.refresh(ItemCenter.REFRESH_LEVEL_0_ALL);
        findViewById(R.id.deleteFinish).setVisibility(View.VISIBLE);
      } else if (bundle.containsKey(DO_DELETE_SHORTCUT_KEY)) {
        String shortcutItemId = bundle.getString(DO_DELETE_SHORTCUT_KEY);
        ArrayList<LauncherItemInfo> copyOfAllItems = new ArrayList<LauncherItemInfo>();
        copyOfAllItems.addAll(config.getShortcutItems());
        Iterator<LauncherItemInfo> it = copyOfAllItems.iterator();
        while (it.hasNext()) {
          if (it.next().id.equals(shortcutItemId)) {
            it.remove();
          }
        }
        config.setShortcutItems(copyOfAllItems);
        itemCenter.refresh(ItemCenter.REFRESH_LEVEL_0_ALL);
      }
    }
  }

  BroadcastReceiver appChangedReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      itemCenter.refresh(ItemCenter.REFRESH_LEVEL_0_ALL);
    }
  };

  BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
      int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
      batteryLevel = rawLevel >= 0 && scale > 0 ? rawLevel * 100 / scale : -1;
      batteryStatusValue = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
      batteryHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
      batteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
      updateBatteryViews();
    }
  };

  /** Shows the battery in the style chosen in settings (see BatteryView). */
  public void applyBatteryStyle() {
    int style = config.batteryStyle;
    batteryProgress.setStyle(style);
    batteryProgress.setVisibility(style == BatteryView.STYLE_TEXT ? View.GONE : View.VISIBLE);
    // The ring already shows the number inside.
    batteryPercent.setVisibility(style == BatteryView.STYLE_RING ? View.GONE : View.VISIBLE);
    updateBatteryViews();
  }

  private void updateBatteryViews() {
    batteryProgress.setLevel(batteryLevel);
    batteryProgress.setCharging(batteryPlugged);
    batteryPercent.setText(batteryLevel >= 0 ? batteryLevel + "%" : "?");

    // Text only for what the icon can't show: warnings, and charging in the text-only style.
    int statusText = 0;
    if (batteryHealth == BatteryManager.BATTERY_HEALTH_OVERHEAT) {
      statusText = R.string.battery_heat;
    } else if (batteryStatusValue == BatteryManager.BATTERY_STATUS_UNKNOWN) {
      statusText = R.string.battery_unknown;
    } else if (!batteryPlugged && batteryLevel >= 0 && batteryLevel < 15) {
      statusText = R.string.battery_low;
    } else if (batteryPlugged && config.batteryStyle == BatteryView.STYLE_TEXT) {
      statusText = batteryStatusValue == BatteryManager.BATTERY_STATUS_FULL
              ? R.string.battery_full : R.string.battery_charging;
    }
    if (statusText != 0) {
      batteryStatus.setText(statusText);
      batteryStatus.setVisibility(View.VISIBLE);
    } else {
      batteryStatus.setVisibility(View.GONE);
    }
  }

  private void detectUSB() {
    IntentFilter usbFilter = new IntentFilter();
    usbFilter.addAction(Intent.ACTION_UMS_DISCONNECTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
    usbFilter.addDataScheme("file");
    registerReceiver(usbReceiver, usbFilter);
  }

  private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (action.equals(Intent.ACTION_MEDIA_REMOVED)
          || action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
        // Storage removed or unmounted: nothing to do
      } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
        // Storage mounted: reload custom icons
        itemCenter.refresh(ItemCenter.REFRESH_LEVEL_1_REPLACED_ICONS);
      }
    }
  };

  @Override
  public void onBackPressed() {
    if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
      super.onBackPressed();
      config.saveFontSize();
    }
  }

  /**
   * Closes the settings and recreates the launcher so the new language is applied.
   * The settings fragment gets its dependencies injected on creation and must not be
   * restored by the system, so the back stack is cleared before recreating.
   */
  public void applyLanguageChange() {
    if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
      getSupportFragmentManager().popBackStackImmediate();
      config.saveFontSize();
    }
    recreate();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && getFragmentManager().getBackStackEntryCount() == 0) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  public boolean isDeviceAdmin() {
    return policyManager.isAdminActive(new ComponentName(this, AdminReceiver.class));
  }

  public void lockScreen() {
    if (isDeviceAdmin()) {
      policyManager.lockNow();
    } else {
      if (Utils.isMoann){
        ((Context) this).sendBroadcast(new Intent("com.mogu.lock_screen"));
      } else {
        activeManage();
      }
    }
  }

  public void activeManage() {
    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, AdminReceiver.class));
    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getText(R.string.dev_admin_desc));
    startActivityForResult(intent, 10001);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK) {
      switch (requestCode) {
        case 10001:
          policyManager.lockNow();
          break;
      }
    }
  }

  public static boolean isSystemApp(PackageInfo pInfo) {
    return ((pInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
  }

  public static boolean isSystemUpdateApp(PackageInfo pInfo) {
    return ((pInfo.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
  }

  public static boolean isUserApp(PackageInfo pInfo) {
    return (!isSystemApp(pInfo) && !isSystemUpdateApp(pInfo));
  }

  public void toggleStatusBar(){
    int windowFlags= WindowManager.LayoutParams.FLAG_FULLSCREEN;
    if (config.showStatusBar){
      getWindow().setFlags(windowFlags,windowFlags);
    }else{
      getWindow().clearFlags(windowFlags);
    }
  }

  private void checkLaunchHomeNotification(){
    if (!Utils.isMoann) {
      return;
    }
    Intent service = new Intent(this, HomeEntranceService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(service);
    }else{
      startService(service);
    }
  }
}
