package cn.modificator.launcher.modelController;

import cn.modificator.launcher.R;

/**
 * The quick bar icons come in two styles, chosen in settings: framed (a rounded square around
 * the symbol) and line (the symbol alone, larger). Code refers to the framed resources; this
 * maps them to the chosen style. Both are generated from the same symbols with the same stroke.
 */
public final class QuickIcons {
  public static final int STYLE_FRAMED = 0;
  public static final int STYLE_LINE = 1;

  private static int style = STYLE_FRAMED;

  private QuickIcons() {
  }

  public static void setStyle(int newStyle) {
    style = newStyle;
  }

  /** The icon in the current style; other resources are returned unchanged. */
  public static int res(int framed) {
    if (style != STYLE_LINE) return framed;
    // R ids are not constants in AGP 8 app modules, so no switch.
    if (framed == R.drawable.wifi_on) return R.drawable.wifi_on_line;
    if (framed == R.drawable.wifi_off) return R.drawable.wifi_off_line;
    if (framed == R.drawable.ftp_on) return R.drawable.ftp_on_line;
    if (framed == R.drawable.ftp_off) return R.drawable.ftp_off_line;
    if (framed == R.drawable.quick_brightness) return R.drawable.quick_brightness_line;
    if (framed == R.drawable.quick_contrast) return R.drawable.quick_contrast_line;
    if (framed == R.drawable.quick_clean) return R.drawable.quick_clean_line;
    if (framed == R.drawable.quick_lock) return R.drawable.quick_lock_line;
    return framed;
  }
}
