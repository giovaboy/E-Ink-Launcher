package cn.modificator.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;


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
    // Hidden developer tools: device report, adb and recovery attempts (see DevTools).
    version.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        DevTools.show(context);
        return true;
      }
    });
    return root;
  }

  public void show() {
    new AlertDialog.Builder(context)
        .setView(initLayout())
        .setPositiveButton(R.string.dialog_close, null)
        .show();
  }
}
