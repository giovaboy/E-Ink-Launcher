package cn.modificator.launcher;

import android.view.View;
import android.widget.TextView;

/**
 * Help & About, shown inside the settings card (see SettingFragment): version and memory use,
 * what the launcher does, the project page, credits and license. A long press on the version
 * line opens the developer tools (see DevTools).
 */
final class AboutPanel {
  private AboutPanel() {
  }

  /** Fills in the parts of about_content.xml that change: version and memory. */
  static void bind(View aboutContent) {
    TextView version = aboutContent.findViewById(R.id.aboutVersion);
    version.setText(MemoryStats.describe(aboutContent.getContext()));
    version.setOnLongClickListener(v -> {
      DevTools.show(v.getContext());
      return true;
    });
  }
}
