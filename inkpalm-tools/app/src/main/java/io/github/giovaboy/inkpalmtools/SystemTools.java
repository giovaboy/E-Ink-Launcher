package io.github.giovaboy.inkpalmtools;

import android.content.Context;
import android.os.PowerManager;
import android.os.Process;

/**
 * Privileged operations that need the app to run as the system user (platform signature,
 * see AndroidManifest.xml).
 *
 * Not possible even as the system user: adb over Wi-Fi. SELinux only lets adb's shell and root
 * set service.adb.tcp.port / persist.adb.tcp.port ("failed to set system property").
 */
final class SystemTools {
  private SystemTools() {
  }

  static boolean isSystemUser() {
    return Process.myUid() == Process.SYSTEM_UID;
  }

  static void rebootToRecovery(Context context) {
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    pm.reboot("recovery");
  }
}
