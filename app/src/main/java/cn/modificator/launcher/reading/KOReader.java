package cn.modificator.launcher.reading;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Reads KOReader's own files to find the book being read. Everything here does file I/O and
 * must run off the main thread, except {@link #findPackage} and {@link #open}.
 */
public final class KOReader {
  private static final String TAG = "KOReader";
  private static final String PACKAGE_PREFIX = "org.koreader.launcher";

  private KOReader() {
  }

  /** The installed KOReader package (GitHub and F-Droid builds differ), or null. */
  @Nullable
  public static String findPackage(PackageManager pm) {
    Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> activities = pm.queryIntentActivities(main, 0);
    for (ResolveInfo info : activities) {
      if (info.activityInfo.packageName.startsWith(PACKAGE_PREFIX)) {
        return info.activityInfo.packageName;
      }
    }
    return null;
  }

  private static File koreaderDir() {
    return new File(Environment.getExternalStorageDirectory(), "koreader");
  }

  /** The last book opened in KOReader, or null if there is none or it no longer exists. */
  @Nullable
  public static ReadingBook readLastBook() {
    Map<String, Object> settings = readLuaFile(new File(koreaderDir(), "settings.reader.lua"));
    String lastFile = settings != null ? asString(settings.get("lastfile")) : null;
    if (lastFile == null) {
      lastFile = mostRecentHistoryFile();
    }
    if (lastFile == null) {
      return null;
    }
    File book = new File(lastFile);
    if (!book.isFile()) {
      return null;
    }

    String metadataFolder = settings != null ? asString(settings.get("document_metadata_folder")) : null;
    Map<String, Object> metadata = readLuaFile(sidecarFile(book, metadataFolder));

    String title = null;
    String authors = null;
    float progress = -1;
    boolean finished = false;
    if (metadata != null) {
      Map<String, Object> props = asTable(metadata.get("doc_props"));
      Map<String, Object> stats = asTable(metadata.get("stats"));
      Map<String, Object> summary = asTable(metadata.get("summary"));
      title = firstNonEmpty(props != null ? asString(props.get("title")) : null,
              stats != null ? asString(stats.get("title")) : null);
      authors = firstNonEmpty(props != null ? asString(props.get("authors")) : null,
              stats != null ? asString(stats.get("authors")) : null);
      Object percent = metadata.get("percent_finished");
      if (percent instanceof Double) {
        progress = Math.max(0f, Math.min(1f, ((Double) percent).floatValue()));
      }
      finished = summary != null && "complete".equals(summary.get("status"));
    }
    if (title == null) {
      String name = book.getName();
      int dot = name.lastIndexOf('.');
      title = dot > 0 ? name.substring(0, dot) : name;
    }
    if (authors != null) {
      // Multiple authors are stored one per line.
      authors = authors.replace("\n", ", ");
    }
    return new ReadingBook(book, title, authors, progress, finished);
  }

  /** Opens the book in KOReader. Returns false if KOReader couldn't be started. */
  public static boolean open(Context context, String packageName, File book) {
    String name = book.getName();
    String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    Intent intent = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.fromFile(book), mimeType)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (context.getPackageManager().resolveActivity(intent, 0) == null) {
      // No matching intent filter (e.g. unknown MIME type): KOReader's main activity reads
      // the file from the intent data anyway.
      Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
      if (launch == null || launch.getComponent() == null) {
        return false;
      }
      intent.setComponent(launch.getComponent());
    }
    // KOReader needs a real path, which a content:// URI doesn't give it. file:// URIs are
    // rejected by StrictMode since Android N, so lift that check just for this call.
    StrictMode.VmPolicy previousPolicy = StrictMode.getVmPolicy();
    StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
    try {
      context.startActivity(intent);
      return true;
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, "Can't open " + book, e);
      return false;
    } finally {
      StrictMode.setVmPolicy(previousPolicy);
    }
  }

  @Nullable
  private static String mostRecentHistoryFile() {
    Map<String, Object> history = readLuaFile(new File(koreaderDir(), "history.lua"));
    if (history == null) {
      return null;
    }
    String bestFile = null;
    double bestTime = -1;
    for (Object entry : history.values()) {
      Map<String, Object> item = asTable(entry);
      if (item == null) continue;
      Object time = item.get("time");
      String file = asString(item.get("file"));
      if (file != null && time instanceof Double && (Double) time > bestTime) {
        bestTime = (Double) time;
        bestFile = file;
      }
    }
    return bestFile;
  }

  /**
   * Where KOReader keeps a book's metadata. "doc" (the default) puts a .sdr folder next to the
   * book, "dir" mirrors the book's path under koreader/docsettings. The "hash" mode is not
   * supported.
   */
  private static File sidecarFile(File book, @Nullable String metadataFolder) {
    String name = book.getName();
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    String extension = dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
    File sdrParent = "dir".equals(metadataFolder)
            ? new File(new File(koreaderDir(), "docsettings"), book.getParent())
            : book.getParentFile();
    return new File(new File(sdrParent, base + ".sdr"), "metadata." + extension + ".lua");
  }

  @Nullable
  private static Map<String, Object> readLuaFile(File file) {
    if (!file.isFile()) {
      return null;
    }
    try (InputStream in = new FileInputStream(file)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      return LuaTableParser.parse(out.toString("UTF-8"));
    } catch (IOException | IllegalArgumentException e) {
      Log.w(TAG, "Can't read " + file, e);
      return null;
    }
  }

  @Nullable
  @SuppressWarnings("unchecked")
  private static Map<String, Object> asTable(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : null;
  }

  @Nullable
  private static String asString(Object value) {
    return value instanceof String ? (String) value : null;
  }

  @Nullable
  private static String firstNonEmpty(@Nullable String a, @Nullable String b) {
    if (a != null && !a.trim().isEmpty()) return a.trim();
    if (b != null && !b.trim().isEmpty()) return b.trim();
    return null;
  }
}
