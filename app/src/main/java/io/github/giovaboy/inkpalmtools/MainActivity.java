package io.github.giovaboy.inkpalmtools;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.io.File;

/** Status (system user? RAM), disable apps, save diagnostics, reboot into recovery. */
public class MainActivity extends Activity {
  private static final int REQUEST_STORAGE = 1;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private TextView status;
  private TextView log;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    status = findViewById(R.id.status);
    log = findViewById(R.id.log);
    findViewById(R.id.apps).setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
    findViewById(R.id.diagnostics).setOnClickListener(v -> saveDiagnostics());
    findViewById(R.id.recovery).setOnClickListener(v -> confirmRecovery());
  }

  @Override
  protected void onResume() {
    super.onResume();
    long[] ram = Diagnostics.ram(this);
    status.setText(getString(R.string.status,
            SystemTools.isSystemUser() ? getString(R.string.status_system_user)
                    : getString(R.string.status_not_system_user, android.os.Process.myUid()),
            ram[1], ram[0]));
  }

  private void saveDiagnostics() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
      return;
    }
    log.setText(R.string.diagnostics_running);
    new Thread(() -> {
      String result;
      try {
        File file = Diagnostics.write(this);
        result = getString(R.string.diagnostics_saved, file.getPath());
      } catch (Exception e) {
        result = String.valueOf(e);
      }
      final String text = result;
      handler.post(() -> log.setText(text));
    }, "diagnostics").start();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == REQUEST_STORAGE && grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      saveDiagnostics();
    }
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
