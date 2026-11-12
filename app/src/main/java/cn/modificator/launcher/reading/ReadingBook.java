package cn.modificator.launcher.reading;

import androidx.annotation.Nullable;

import java.io.File;

/** The book a reader app has open, as far as the launcher can tell from its files. */
public final class ReadingBook {
  public final File file;
  public final String title;
  @Nullable
  public final String authors;
  /** From 0 to 1, or a negative value when unknown. */
  public final float progress;
  public final boolean finished;

  public ReadingBook(File file, String title, @Nullable String authors, float progress, boolean finished) {
    this.file = file;
    this.title = title;
    this.authors = authors;
    this.progress = progress;
    this.finished = finished;
  }
}
