package io.github.giovaboy.inkpalmtools;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Writes a diagnostics file to Download/: RAM, memory of every process, dumpsys meminfo and
 * the system log. As the system user the app may read all of them (DUMP, READ_LOGS); if the
 * firmware still blocks a command, the file says so instead of failing.
 */
final class Diagnostics {
  private Diagnostics() {
  }

  /** RAM in MB: {total, available}. */
  static long[] ram(Context context) {
    ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
    ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
    return new long[]{info.totalMem / (1024 * 1024), info.availMem / (1024 * 1024)};
  }

  static File write(Context context) throws IOException {
    String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "inkpalm-diagnostics-" + stamp + ".txt");
    try (Writer out = new FileWriter(file)) {
      long[] ram = ram(context);
      out.write("InkPalm Tools diagnostics " + stamp + "\n");
      out.write("RAM: " + ram[0] + " MB total, " + ram[1] + " MB available\n\n");

      out.write("\n== Disabled apps\n");
      writeDisabledApps(context, out);

      out.write("\n== Processes by memory (PSS, MB)\n");
      writeProcesses(context, out);

      out.write("\n== dumpsys meminfo\n");
      runCommand(out, "dumpsys", "meminfo");

      // Breakdown (Java heap, native, graphics, code...) of the launcher, to see where it goes.
      out.write("\n== dumpsys meminfo com.moan.moanwm (E-Ink Launcher)\n");
      runCommand(out, "dumpsys", "meminfo", "com.moan.moanwm");

      out.write("\n== logcat (last 3000 lines)\n");
      runCommand(out, "logcat", "-d", "-v", "threadtime", "-t", "3000");
    }
    return file;
  }

  private static void writeDisabledApps(Context context, Writer out) throws IOException {
    int count = 0;
    for (AppControl.App app : AppControl.list(context, false)) {
      if (!app.enabled) {
        out.write(app.packageName + "  (" + app.label + ")\n");
        count++;
      }
    }
    if (count == 0) {
      out.write("(none)\n");
    }
  }

  private static void writeProcesses(Context context, Writer out) throws IOException {
    ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
    if (processes == null) {
      out.write("(not available)\n");
      return;
    }
    int[] pids = new int[processes.size()];
    for (int i = 0; i < pids.length; i++) {
      pids[i] = processes.get(i).pid;
    }
    Debug.MemoryInfo[] memory = am.getProcessMemoryInfo(pids);
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < pids.length; i++) {
      lines.add(String.format(Locale.US, "%7.1f  %s", memory[i].getTotalPss() / 1024f, processes.get(i).processName));
    }
    // Biggest first.
    Collections.sort(lines, Collections.reverseOrder());
    for (String line : lines) {
      out.write(line + "\n");
    }
  }

  private static void runCommand(Writer out, String... command) throws IOException {
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          out.write(line);
          out.write('\n');
        }
      }
      out.write("(exit " + process.waitFor() + ")\n");
    } catch (IOException | InterruptedException e) {
      out.write("(could not run " + command[0] + ": " + e + ")\n");
    }
  }
}
