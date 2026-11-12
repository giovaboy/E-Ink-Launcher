package cn.modificator.launcher.reading;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;

import cn.modificator.launcher.R;
import cn.modificator.launcher.Utils;

/**
 * The "continue reading" card above the app grid: shows the last book opened in KOReader
 * and opens it again on tap. Files are read on a short-lived background thread.
 */
public final class ReadingCard {
  private final Activity activity;
  private final View root;
  private final ImageView cover;
  private final TextView title;
  private final TextView author;
  private final ProgressBar progress;
  private final TextView status;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private boolean enabled = true;
  @Nullable
  private String koreaderPackage;
  @Nullable
  private ReadingBook book;
  /** What is on screen, to skip redraws (which flash on e-ink) when nothing changed. */
  @Nullable
  private String shownKey;
  private boolean coverShown;
  private int requestId;

  public ReadingCard(Activity activity, View root, boolean enabled) {
    this.activity = activity;
    this.root = root;
    this.enabled = enabled;
    cover = root.findViewById(R.id.readingCover);
    title = root.findViewById(R.id.readingTitle);
    author = root.findViewById(R.id.readingAuthor);
    progress = root.findViewById(R.id.readingProgress);
    status = root.findViewById(R.id.readingStatus);
    root.findViewById(R.id.readingCardContent).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onCardClicked();
      }
    });
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    refresh();
  }

  /** Re-reads KOReader's files; call it whenever the home screen is shown again. */
  public void refresh() {
    final int id = ++requestId;
    koreaderPackage = enabled ? KOReader.findPackage(activity.getPackageManager()) : null;
    if (koreaderPackage == null) {
      show(null);
      return;
    }
    if (!hasStoragePermission()) {
      showMessage(activity.getString(R.string.reading_permission));
      return;
    }
    final int coverHeightPx = activity.getResources().getDimensionPixelSize(R.dimen.reading_cover_height);
    final android.content.Context appContext = activity.getApplicationContext();
    new Thread(new Runnable() {
      @Override
      public void run() {
        final ReadingBook lastBook = KOReader.readLastBook();
        final File thumbnail = lastBook != null ? BookCover.getThumbnail(appContext, lastBook.file, coverHeightPx) : null;
        mainHandler.post(new Runnable() {
          @Override
          public void run() {
            if (id == requestId) {
              show(lastBook);
              if (thumbnail != null && !coverShown) {
                loadCover(thumbnail);
              }
            }
          }
        });
      }
    }, "ReadingCard").start();
  }

  /** Drops the cover bitmap, e.g. while another app is in the foreground. */
  public void releaseMemory() {
    requestId++;
    cover.setImageResource(R.drawable.reading_cover_placeholder);
    coverShown = false;
  }

  private void show(@Nullable ReadingBook newBook) {
    book = newBook;
    if (koreaderPackage == null) {
      root.setVisibility(View.GONE);
      shownKey = null;
      return;
    }
    String key = newBook == null ? "none"
            : newBook.file.getPath() + '|' + newBook.title + '|' + newBook.authors + '|' + newBook.progress + '|' + newBook.finished;
    if (key.equals(shownKey) && root.getVisibility() == View.VISIBLE) {
      return;
    }
    if (shownKey == null || !shownKey.startsWith(newBook != null ? newBook.file.getPath() + '|' : "none")) {
      // A different book: its cover has to be loaded again.
      cover.setImageResource(R.drawable.reading_cover_placeholder);
      coverShown = false;
    }
    shownKey = key;
    root.setVisibility(View.VISIBLE);

    if (newBook == null) {
      title.setText(R.string.reading_open_koreader);
      author.setVisibility(View.GONE);
      progress.setVisibility(View.GONE);
      status.setVisibility(View.GONE);
      return;
    }
    title.setText(newBook.title);
    author.setText(newBook.authors);
    author.setVisibility(newBook.authors != null ? View.VISIBLE : View.GONE);
    status.setVisibility(View.VISIBLE);
    if (newBook.finished) {
      progress.setVisibility(View.VISIBLE);
      progress.setProgress(progress.getMax());
      status.setText(R.string.reading_finished);
    } else if (newBook.progress >= 0) {
      progress.setVisibility(View.VISIBLE);
      progress.setProgress(Math.round(newBook.progress * progress.getMax()));
      status.setText(activity.getString(R.string.reading_progress, Math.round(newBook.progress * 100)));
    } else {
      progress.setVisibility(View.GONE);
      status.setText(R.string.reading_continue);
    }
  }

  private void showMessage(String message) {
    book = null;
    shownKey = null;
    root.setVisibility(View.VISIBLE);
    cover.setImageResource(R.drawable.reading_cover_placeholder);
    coverShown = false;
    title.setText(message);
    author.setVisibility(View.GONE);
    progress.setVisibility(View.GONE);
    status.setVisibility(View.GONE);
  }

  private void loadCover(final File thumbnail) {
    final int id = requestId;
    new Thread(new Runnable() {
      @Override
      public void run() {
        final Bitmap bitmap = BookCover.load(thumbnail);
        mainHandler.post(new Runnable() {
          @Override
          public void run() {
            if (id == requestId && bitmap != null) {
              cover.setImageBitmap(bitmap);
              coverShown = true;
            }
          }
        });
      }
    }, "ReadingCardCover").start();
  }

  private void onCardClicked() {
    if (koreaderPackage == null) {
      return;
    }
    if (!hasStoragePermission()) {
      Utils.checkStroagePermission(activity, null);
      return;
    }
    if (book != null && KOReader.open(activity, koreaderPackage, book.file)) {
      return;
    }
    Intent launch = activity.getPackageManager().getLaunchIntentForPackage(koreaderPackage);
    if (launch != null) {
      activity.startActivity(launch);
    }
  }

  private boolean hasStoragePermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
  }
}
