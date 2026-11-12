package cn.modificator.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Help & About: version and memory use, what the launcher does, the project page, credits and
 * license. A long press on the version line writes the device report (see DeviceProbe).
 */
public class AboutDialog {

  private final Context context;

  private AboutDialog(Context context) {
    this.context = context;
  }

  public static AboutDialog getInstance(Context context) {
    return new AboutDialog(context);
  }

  private View initLayout() {
    View root = LayoutInflater.from(context).inflate(R.layout.about_dialog, null);
    TextView version = root.findViewById(R.id.aboutVersion);
    version.setText(MemoryStats.describe(context));
    // Hidden diagnostic: a long press writes a device probe report (see DeviceProbe).
    version.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        runDeviceProbe();
        return true;
      }
    });
    return root;
  }

  private void runDeviceProbe() {
    final Handler mainHandler = new Handler(Looper.getMainLooper());
    final Context appContext = context.getApplicationContext();
    new Thread(new Runnable() {
      @Override
      public void run() {
        String message;
        try {
          File report = DeviceProbe.run(appContext);
          message = context.getString(R.string.probe_saved, report.getPath());
        } catch (Exception e) {
          message = context.getString(R.string.probe_failed, e.toString());
        }
        final String text = message;
        mainHandler.post(new Runnable() {
          @Override
          public void run() {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show();
          }
        });
      }
    }, "DeviceProbe").start();
  }

  public void show() {
    new AlertDialog.Builder(context)
        .setView(initLayout())
        .setPositiveButton(R.string.dialog_close, null)
        .show();
  }
}
