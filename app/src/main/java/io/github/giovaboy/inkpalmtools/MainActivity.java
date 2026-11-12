package io.github.giovaboy.inkpalmtools;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/**
 * One screen: status (system user? adb? listening on Wi-Fi?), adb over Wi-Fi on/off, reboot
 * into recovery, and a log of what each action did, including why something was refused.
 */
public class MainActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private TextView status;
  private TextView log;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    status = findViewById(R.id.status);
    log = findViewById(R.id.log);
    findViewById(R.id.adbWifiOn).setOnClickListener(v -> setAdbOverWifi(true));
    findViewById(R.id.adbWifiOff).setOnClickListener(v -> setAdbOverWifi(false));
    findViewById(R.id.recovery).setOnClickListener(v -> confirmRecovery());
  }

  @Override
  protected void onResume() {
    super.onResume();
    refreshStatus();
  }

  private void refreshStatus() {
    new Thread(() -> {
      boolean listening = SystemTools.isAdbListeningOnTcp();
      String text = getString(R.string.status,
              SystemTools.isSystemUser() ? getString(R.string.status_system_user)
                      : getString(R.string.status_not_system_user, android.os.Process.myUid()),
              SystemTools.getGlobalInt(this, "adb_enabled"),
              SystemTools.getProperty("init.svc.adbd"),
              SystemTools.getProperty("service.adb.tcp.port"),
              SystemTools.getProperty("persist.adb.tcp.port"),
              listening ? getString(R.string.status_listening, SystemTools.wifiAddress(this), SystemTools.ADB_TCP_PORT)
                      : getString(R.string.status_not_listening));
      handler.post(() -> status.setText(text));
    }, "status").start();
  }

  private void addLog(String line) {
    handler.post(() -> log.append(line + "\n"));
  }

  private void setAdbOverWifi(boolean on) {
    log.setText("");
    if (!SystemTools.isSystemUser()) {
      addLog(getString(R.string.log_not_system));
      return;
    }
    String port = on ? String.valueOf(SystemTools.ADB_TCP_PORT) : "0";
    new Thread(() -> {
      String result = SystemTools.setProperty("service.adb.tcp.port", port);
      addLog("service.adb.tcp.port = " + port + ": " + (result == null ? "ok" : result));
      // Persistent too, so adb keeps listening on Wi-Fi after a reboot.
      result = SystemTools.setProperty("persist.adb.tcp.port", port);
      addLog("persist.adb.tcp.port = " + port + ": " + (result == null ? "ok" : result));
      try {
        addLog(getString(R.string.log_restarting_adb));
        SystemTools.restartAdb(this);
        Thread.sleep(3000);
        addLog(getString(R.string.log_done));
      } catch (Exception e) {
        addLog(String.valueOf(e));
      }
      refreshStatus();
    }, "adb").start();
  }

  private void confirmRecovery() {
    new AlertDialog.Builder(this)
            .setTitle(R.string.recovery)
            .setMessage(R.string.recovery_confirm)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
              try {
                SystemTools.rebootToRecovery(this);
              } catch (Exception e) {
                log.setText(String.valueOf(e));
              }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
  }
}
