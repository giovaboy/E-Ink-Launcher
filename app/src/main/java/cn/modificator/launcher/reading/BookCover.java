package cn.modificator.launcher.reading;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Small grayscale cover thumbnails for books, cached in the app cache dir so the book file is
 * only opened once. Only EPUB is supported. Does file I/O: call it off the main thread.
 */
public final class BookCover {
  private static final String TAG = "BookCover";
  private static final int MAX_CACHED_COVERS = 8;

  private BookCover() {
  }

  /** The cached thumbnail for {@code book}, creating it if needed, or null if it has none. */
  @Nullable
  public static File getThumbnail(Context context, File book, int heightPx) {
    File dir = new File(context.getCacheDir(), "covers");
    if (!dir.isDirectory() && !dir.mkdirs()) {
      return null;
    }
    String key = Integer.toHexString((book.getAbsolutePath() + '|' + book.lastModified() + '|' + heightPx).hashCode());
    File thumbnail = new File(dir, key + ".png");
    File noCover = new File(dir, key + ".none");
    if (thumbnail.isFile()) {
      return thumbnail;
    }
    if (noCover.isFile()) {
      return null;
    }

    Bitmap cover = null;
    if (book.getName().toLowerCase().endsWith(".epub")) {
      cover = decodeEpubCover(book, heightPx);
    }
    try {
      if (cover == null) {
        noCover.createNewFile(); // don't retry on every return to the home screen
        return null;
      }
      try (OutputStream out = new FileOutputStream(thumbnail)) {
        cover.compress(Bitmap.CompressFormat.PNG, 100, out);
      }
      return thumbnail;
    } catch (IOException e) {
      Log.w(TAG, "Can't cache cover of " + book, e);
      return null;
    } finally {
      if (cover != null) {
        cover.recycle();
      }
      trimCache(dir);
    }
  }

  /** Loads a thumbnail made by {@link #getThumbnail} in a compact pixel format. */
  @Nullable
  public static Bitmap load(File thumbnail) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.RGB_565;
    return BitmapFactory.decodeFile(thumbnail.getPath(), options);
  }

  @Nullable
  private static Bitmap decodeEpubCover(File book, int heightPx) {
    try (ZipFile zip = new ZipFile(book)) {
      String opfPath = findOpfPath(zip);
      if (opfPath == null) {
        return null;
      }
      String coverHref = findCoverHref(zip, opfPath);
      if (coverHref == null) {
        return null;
      }
      String opfDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
      ZipEntry entry = zip.getEntry(resolvePath(opfDir, coverHref));
      if (entry == null) {
        return null;
      }

      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      try (InputStream in = zip.getInputStream(entry)) {
        BitmapFactory.decodeStream(in, null, options);
      }
      if (options.outHeight <= 0 || options.outWidth <= 0) {
        return null;
      }
      int sampleSize = 1;
      while (options.outHeight / (sampleSize * 2) >= heightPx) {
        sampleSize *= 2;
      }
      options.inJustDecodeBounds = false;
      options.inSampleSize = sampleSize;
      Bitmap decoded;
      try (InputStream in = zip.getInputStream(entry)) {
        decoded = BitmapFactory.decodeStream(in, null, options);
      }
      if (decoded == null) {
        return null;
      }
      return toGrayscaleThumbnail(decoded, heightPx);
    } catch (IOException | XmlPullParserException | RuntimeException e) {
      Log.w(TAG, "Can't read cover of " + book, e);
      return null;
    }
  }

  private static Bitmap toGrayscaleThumbnail(Bitmap source, int heightPx) {
    int height = Math.min(heightPx, source.getHeight());
    int width = Math.max(1, Math.round(source.getWidth() * (height / (float) source.getHeight())));
    Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    ColorMatrix grayscale = new ColorMatrix();
    grayscale.setSaturation(0);
    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    paint.setColorFilter(new ColorMatrixColorFilter(grayscale));
    new Canvas(result).drawBitmap(source, null, new Rect(0, 0, width, height), paint);
    source.recycle();
    return result;
  }

  @Nullable
  private static String findOpfPath(ZipFile zip) throws IOException, XmlPullParserException {
    ZipEntry container = zip.getEntry("META-INF/container.xml");
    if (container == null) {
      return null;
    }
    try (InputStream in = zip.getInputStream(container)) {
      XmlPullParser parser = Xml.newPullParser();
      parser.setInput(in, null);
      for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
        if (event == XmlPullParser.START_TAG && "rootfile".equals(localName(parser.getName()))) {
          return parser.getAttributeValue(null, "full-path");
        }
      }
    }
    return null;
  }

  /**
   * The cover image path from the OPF: the EPUB 3 "cover-image" item, else the item named by
   * the EPUB 2 {@code <meta name="cover">}, else an image item whose id or path says "cover".
   */
  @Nullable
  private static String findCoverHref(ZipFile zip, String opfPath) throws IOException, XmlPullParserException {
    ZipEntry opf = zip.getEntry(opfPath);
    if (opf == null) {
      return null;
    }
    String coverId = null;
    String epub3Cover = null;
    String guessedCover = null;
    java.util.Map<String, String> imageHrefById = new java.util.HashMap<>();
    try (InputStream in = zip.getInputStream(opf)) {
      XmlPullParser parser = Xml.newPullParser();
      parser.setInput(in, null);
      for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
        if (event != XmlPullParser.START_TAG) {
          continue;
        }
        String tag = localName(parser.getName());
        if ("meta".equals(tag) && "cover".equals(parser.getAttributeValue(null, "name"))) {
          coverId = parser.getAttributeValue(null, "content");
        } else if ("item".equals(tag)) {
          String id = parser.getAttributeValue(null, "id");
          String href = parser.getAttributeValue(null, "href");
          String mediaType = parser.getAttributeValue(null, "media-type");
          String properties = parser.getAttributeValue(null, "properties");
          if (href == null || mediaType == null || !mediaType.startsWith("image/")) {
            continue;
          }
          if (id != null) {
            imageHrefById.put(id, href);
          }
          if (properties != null && Arrays.asList(properties.split("\\s+")).contains("cover-image")) {
            epub3Cover = href;
          }
          String lowerId = id != null ? id.toLowerCase() : "";
          if (guessedCover == null && (lowerId.contains("cover") || href.toLowerCase().contains("cover"))) {
            guessedCover = href;
          }
        }
      }
    }
    if (epub3Cover != null) return epub3Cover;
    if (coverId != null && imageHrefById.containsKey(coverId)) return imageHrefById.get(coverId);
    return guessedCover;
  }

  private static String localName(String name) {
    int colon = name.indexOf(':');
    return colon >= 0 ? name.substring(colon + 1) : name;
  }

  /** Resolves an OPF-relative, URL-encoded href to a zip entry name. */
  private static String resolvePath(String baseDir, String href) throws IOException {
    String decoded = URLDecoder.decode(href.replace("+", "%2B"), "UTF-8");
    Deque<String> parts = new ArrayDeque<>();
    for (String part : (baseDir + decoded).split("/")) {
      if (part.isEmpty() || part.equals(".")) continue;
      if (part.equals("..")) {
        if (!parts.isEmpty()) parts.removeLast();
      } else {
        parts.addLast(part);
      }
    }
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (sb.length() > 0) sb.append('/');
      sb.append(part);
    }
    return sb.toString();
  }

  private static void trimCache(File dir) {
    File[] files = dir.listFiles();
    if (files == null || files.length <= MAX_CACHED_COVERS) {
      return;
    }
    Arrays.sort(files, new Comparator<File>() {
      @Override
      public int compare(File a, File b) {
        return Long.compare(b.lastModified(), a.lastModified());
      }
    });
    for (int i = MAX_CACHED_COVERS; i < files.length; i++) {
      files[i].delete();
    }
  }
}
