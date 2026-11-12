package cn.modificator.launcher;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.HashMap;

import cn.modificator.launcher.ftpservice.FTPService;
import cn.modificator.launcher.modelController.ItemCenter;
import cn.modificator.launcher.modelController.LauncherItemInfoComparator;
import cn.modificator.launcher.modelController.WifiControl;

/**
 * Launcher settings, shown as a centered modal over the home screen. Every change is sent to
 * the launcher right away (LAUNCHER_ACTION broadcasts), so the grid behind updates live.
 */
public class SettingFragment extends Fragment implements View.OnClickListener {
  private static final int MIN_GRID = 2;
  private static final int MAX_GRID = 10;
  private static final int MIN_FONT_SIZE = 10;
  private static final int MAX_FONT_SIZE = 30;
  private static final int REQUEST_SORT = 10000;
  private static final int REQUEST_LOCATION = 10002;

  private static final Integer[] SORT_FLAGS_IN_ARRAY = new Integer[] {
          LauncherItemInfoComparator.SORT_MODE_ALPHABETICAL | LauncherItemInfoComparator.SORT_ORDER_ASC,
          LauncherItemInfoComparator.SORT_MODE_ALPHABETICAL | LauncherItemInfoComparator.SORT_ORDER_DESC,
          LauncherItemInfoComparator.SORT_MODE_FIRST_APPEAR | LauncherItemInfoComparator.SORT_ORDER_ASC,
          LauncherItemInfoComparator.SORT_MODE_FIRST_APPEAR | LauncherItemInfoComparator.SORT_ORDER_DESC,
          LauncherItemInfoComparator.SORT_MODE_CUSTOM_WITH_FIRST_APPEAR | LauncherItemInfoComparator.SORT_ORDER_ASC,
          LauncherItemInfoComparator.SORT_MODE_CUSTOM_WITH_FIRST_APPEAR | LauncherItemInfoComparator.SORT_ORDER_DESC,
          LauncherItemInfoComparator.SORT_MODE_CUSTOM_WITH_ALPHABETICAL | LauncherItemInfoComparator.SORT_ORDER_ASC,
          LauncherItemInfoComparator.SORT_MODE_CUSTOM_WITH_ALPHABETICAL | LauncherItemInfoComparator.SORT_ORDER_DESC,
  };

  public ItemCenter itemCenter;
  public Config config;

  private View rootView;
  private TextView colValue, rowValue, fontValue, sortMethodValue, languageValue, ftpStatus;
  private Switch switchDivider, switchStatusBar, switchCustomIcon, switchReadingCard, switchFtp;
  private ViewGroup titleLinesSegments;
  // Local copies: the launcher updates Config asynchronously when it gets the broadcast.
  private int colNum, rowNum, fontSize;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.activity_setting, container, false);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    // When the system recreates the activity (configuration change, language, DPI...) it
    // restores this fragment by itself, without the fields the launcher set when opening it.
    // Launcher.onCreate() has already run at this point, so take them from there.
    Launcher launcher = (Launcher) getActivity();
    if (config == null) {
      config = launcher.config;
    }
    if (itemCenter == null) {
      itemCenter = launcher.itemCenter;
    }
    rootView = getView();

    colValue = rootView.findViewById(R.id.colValue);
    rowValue = rootView.findViewById(R.id.rowValue);
    fontValue = rootView.findViewById(R.id.fontValue);
    sortMethodValue = rootView.findViewById(R.id.sortMethodValue);
    languageValue = rootView.findViewById(R.id.languageValue);
    ftpStatus = rootView.findViewById(R.id.ftp_status);
    switchDivider = rootView.findViewById(R.id.switchDivider);
    switchStatusBar = rootView.findViewById(R.id.switchStatusBar);
    switchCustomIcon = rootView.findViewById(R.id.switchCustomIcon);
    switchReadingCard = rootView.findViewById(R.id.switchReadingCard);
    switchFtp = rootView.findViewById(R.id.switchFtp);
    titleLinesSegments = rootView.findViewById(R.id.titleLinesSegments);

    colNum = config.colNum;
    rowNum = config.rowNum;
    fontSize = Math.round(config.fontSize);

    int[] clickable = {R.id.rootView, R.id.toBack, R.id.colDown, R.id.colUp, R.id.rowDown, R.id.rowUp,
            R.id.fontDown, R.id.fontUp, R.id.sortMethod, R.id.toggleDivider, R.id.toggleStatusBar,
            R.id.toggleCustomIcon, R.id.toggleReadingCard, R.id.menu_ftp, R.id.showWifiName,
            R.id.manageApps, R.id.toSort, R.id.language, R.id.helpAbout};
    for (int id : clickable) {
      rootView.findViewById(id).setOnClickListener(this);
    }
    for (int i = 0; i < titleLinesSegments.getChildCount(); i++) {
      final int position = i;
      titleLinesSegments.getChildAt(i).setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          setTitleLines(position);
        }
      });
    }

    updateViews();
    updateStatus();
  }

  /** Shows the current values. Config uses some inverted flags: see the comments. */
  private void updateViews() {
    colValue.setText(String.valueOf(colNum));
    rowValue.setText(String.valueOf(rowNum));
    fontValue.setText(String.valueOf(fontSize));
    selectTitleLinesSegment(getTitleLinesPosition());
    sortMethodValue.setText(getResources().getStringArray(R.array.sort_method)[getSortMethodPosition()]);
    languageValue.setText(LocaleHelper.getLanguageNames(getActivity())[LocaleHelper.getLanguageIndex(getActivity())]);
    switchDivider.setChecked(!config.hideDivider);
    // config.showStatusBar is true when the launcher runs full screen, i.e. the bar is hidden.
    switchStatusBar.setChecked(!config.showStatusBar);
    switchCustomIcon.setChecked(config.showCustomIcon);
    switchReadingCard.setChecked(config.showReadingCard);
    boolean needsLocation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    rootView.findViewById(R.id.showWifiName).setVisibility(needsLocation ? View.VISIBLE : View.GONE);
  }

  private int getTitleLinesPosition() {
    return Math.min(config.itemTitleLines, 3); // 3 = no limit (Integer.MAX_VALUE)
  }

  private void selectTitleLinesSegment(int position) {
    for (int i = 0; i < titleLinesSegments.getChildCount(); i++) {
      titleLinesSegments.getChildAt(i).setSelected(i == position);
    }
  }

  private int getSortMethodPosition() {
    int result = Arrays.asList(SORT_FLAGS_IN_ARRAY).indexOf(config.getSortFlags());
    if (result < 0) {
      config.setSortFlags(LauncherItemInfoComparator.SORT_MODE_ALPHABETICAL | LauncherItemInfoComparator.SORT_ORDER_ASC);
      result = 0;
    }
    return result;
  }

  private void sendToLauncher(Intent intent) {
    intent.setAction(Launcher.LAUNCHER_ACTION);
    getActivity().sendBroadcast(intent);
  }

  @Override
  public void onClick(View v) {
    // Resource IDs are not compile-time constants since AGP 8, so this can't be a switch.
    int id = v.getId();
    if (id == R.id.toBack || id == R.id.rootView) {
      getActivity().onBackPressed();
    } else if (id == R.id.colDown || id == R.id.colUp) {
      int next = clamp(colNum + (id == R.id.colUp ? 1 : -1), MIN_GRID, MAX_GRID);
      if (next != colNum) {
        colNum = next;
        sendToLauncher(new Intent().putExtra(Launcher.COL_NUM_KEY, colNum));
        updateViews();
      }
    } else if (id == R.id.rowDown || id == R.id.rowUp) {
      int next = clamp(rowNum + (id == R.id.rowUp ? 1 : -1), MIN_GRID, MAX_GRID);
      if (next != rowNum) {
        rowNum = next;
        sendToLauncher(new Intent().putExtra(Launcher.ROW_NUM_KEY, rowNum));
        updateViews();
      }
    } else if (id == R.id.fontDown || id == R.id.fontUp) {
      int next = clamp(fontSize + (id == R.id.fontUp ? 1 : -1), MIN_FONT_SIZE, MAX_FONT_SIZE);
      if (next != fontSize) {
        fontSize = next;
        config.fontSize = fontSize; // saved by the launcher when the settings close
        sendToLauncher(new Intent().putExtra(Launcher.FONT_SIZE_KEY, (float) fontSize));
        updateViews();
      }
    } else if (id == R.id.sortMethod) {
      showSortMethodDialog();
    } else if (id == R.id.toggleDivider) {
      config.hideDivider = !config.hideDivider;
      sendToLauncher(new Intent().putExtra(Launcher.HIDE_DIVIDER_KEY, config.hideDivider));
      updateViews();
    } else if (id == R.id.toggleStatusBar) {
      config.showStatusBar = !config.showStatusBar;
      sendToLauncher(new Intent().putExtra(Launcher.SHOW_STATUS_BAR_KEY, config.showStatusBar));
      updateViews();
    } else if (id == R.id.toggleCustomIcon) {
      Utils.checkStroagePermission(getActivity(), new Runnable() {
        @Override
        public void run() {
          config.showCustomIcon = !config.showCustomIcon;
          sendToLauncher(new Intent().putExtra(Launcher.SHOW_CUSTOM_ICON_KEY, config.showCustomIcon));
          updateViews();
        }
      });
    } else if (id == R.id.toggleReadingCard) {
      config.showReadingCard = !config.showReadingCard;
      sendToLauncher(new Intent().putExtra(Launcher.SHOW_READING_CARD_KEY, config.showReadingCard));
      updateViews();
    } else if (id == R.id.menu_ftp) {
      Utils.checkStroagePermission(getActivity(), new Runnable() {
        @Override
        public void run() {
          if (!FTPService.isRunning()) {
            if (FTPService.isConnectedToWifi(getActivity()))
              startServer();
            else
              Toast.makeText(getActivity(), R.string.toast_need_wifi_connnect, Toast.LENGTH_SHORT).show();
          } else {
            stopServer();
          }
        }
      });
    } else if (id == R.id.showWifiName) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
      }
    } else if (id == R.id.manageApps) {
      sendToLauncher(new Intent().putExtra(Launcher.DO_MANAGE_APP_KEY, true));
      getActivity().onBackPressed();
    } else if (id == R.id.toSort) {
      Intent intent = new Intent(v.getContext(), SortActivity.class);
      intent.putExtra(SortActivity.ALL_ITEMS_WITH_PRIORITY_KEY, Config.gson.toJson(itemCenter.getAllItems()));
      startActivityForResult(intent, REQUEST_SORT);
    } else if (id == R.id.language) {
      showLanguageDialog();
    } else if (id == R.id.helpAbout) {
      AboutDialog.getInstance(getActivity()).show();
    }
  }

  private void setTitleLines(int position) {
    // The launcher maps position 3 to "no limit".
    config.itemTitleLines = position == 3 ? Integer.MAX_VALUE : position;
    sendToLauncher(new Intent().putExtra(Launcher.ITEM_TITLE_LINES_KEY, position));
    selectTitleLinesSegment(position);
  }

  private void showSortMethodDialog() {
    new AlertDialog.Builder(getActivity())
            .setTitle(R.string.sort_method)
            .setSingleChoiceItems(R.array.sort_method, getSortMethodPosition(), (dialog, which) -> {
              dialog.dismiss();
              config.setSortFlags(SORT_FLAGS_IN_ARRAY[which]);
              sendToLauncher(new Intent().putExtra(Launcher.SORT_FLAGS_KEY, SORT_FLAGS_IN_ARRAY[which]));
              updateViews();
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
  }

  private void showLanguageDialog() {
    final Launcher launcher = (Launcher) getActivity();
    final int current = LocaleHelper.getLanguageIndex(launcher);
    new AlertDialog.Builder(launcher)
            .setTitle(R.string.setting_language)
            .setSingleChoiceItems(LocaleHelper.getLanguageNames(launcher), current, (dialog, which) -> {
              dialog.dismiss();
              if (which != current) {
                LocaleHelper.setLanguage(launcher, LocaleHelper.LANGUAGE_TAGS[which]);
                launcher.applyLanguageChange();
              }
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_SORT && resultCode == RESULT_OK) {
      HashMap<String, Integer> priorityMap = (HashMap<String, Integer>) data.getSerializableExtra(Launcher.PRIORITY_KEY);
      sendToLauncher(new Intent().putExtra(Launcher.PRIORITY_KEY, priorityMap));
      getActivity().onBackPressed();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_LOCATION) {
      WifiControl.reloadWifiName();
      updateViews();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    updateStatus();
    IntentFilter wifiFilter = new IntentFilter();
    wifiFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    getActivity().registerReceiver(mWifiReceiver, wifiFilter);
    IntentFilter ftpFilter = new IntentFilter();
    ftpFilter.addAction(FTPService.ACTION_STARTED);
    ftpFilter.addAction(FTPService.ACTION_STOPPED);
    ftpFilter.addAction(FTPService.ACTION_FAILEDTOSTART);
    getActivity().registerReceiver(ftpReceiver, ftpFilter);
  }

  @Override
  public void onPause() {
    super.onPause();
    getActivity().unregisterReceiver(mWifiReceiver);
    getActivity().unregisterReceiver(ftpReceiver);
  }

  /**
   * Sends a broadcast to start ftp server
   */
  private void startServer() {
    getActivity().sendBroadcast(new Intent(FTPService.ACTION_START_FTPSERVER));
  }

  /**
   * Sends a broadcast to stop ftp server
   */
  private void stopServer() {
    getActivity().sendBroadcast(new Intent(FTPService.ACTION_STOP_FTPSERVER));
  }

  /**
   * Update the FTP row based on connection and server status
   */
  private void updateStatus() {
    boolean running = FTPService.isRunning();
    switchFtp.setChecked(running);
    if (!FTPService.isConnectedToWifi(getActivity())) {
      ftpStatus.setText(R.string.settings_ftp_no_wifi);
    } else if (running) {
      ftpStatus.setText(getFTPAddressString());
    } else {
      ftpStatus.setText(R.string.settings_ftp_off);
    }
  }

  /**
   * @return address at which server is running
   */
  private String getFTPAddressString() {
    return "ftp://" + FTPService.getLocalInetAddress(getActivity()).getHostAddress() + ":" + FTPService.getPort();
  }

  private BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo netInfo = conMan.getActiveNetworkInfo();
      if (netInfo == null || netInfo.getType() != ConnectivityManager.TYPE_WIFI) {
        stopServer();
      }
      updateStatus();
    }
  };

  private BroadcastReceiver ftpReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      // Started, stopped or failed to start: the status line reflects all of them.
      updateStatus();
    }
  };
}
