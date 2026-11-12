package cn.modificator.launcher.modelController;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

import cn.modificator.launcher.Config;
import cn.modificator.launcher.R;
import cn.modificator.launcher.Utils;

public class LauncherItemInfo implements Cloneable {
  public static final int TYPE_LAUNCHER_ACTIVITY = 0;
  public static final int TYPE_SHORTCUT = 1;
  public static final int TYPE_SPECIAL = 2;

  /** For app launcher activites: component name */
  /** For shortcut: shortcut#UUID */
  @NonNull
  public String id;

  public int type;

  public int priority;

  @Nullable
  public Intent intent;

  /** For app launcher activities and Android Nougat+ shortcuts only */
  @Nullable
  public String packageName;

  /** For app launcher activities and Android Nougat+ shortcuts only */
  @Nullable
  public ComponentName componentName;

  /** For app launcher activities and Android Nougat- shortcuts only */
  @Nullable
  public String shortcutId;

  /** Loaded on demand by {@link #getIcon}, null while the item is not on screen. */
  @Nullable
  public Drawable drawable;

  /** Size and source of {@link #drawable}, to reload it when either changes. */
  private int iconSizePx;
  private boolean iconIsCustom;

  /** Last time the app was in the foreground (usage stats), for the "recently used" sort. */
  public long lastUsedTime;

  /** For special items: the icon resource */
  public int iconRes;

  /** For shortcuts only */
  @Nullable
  public Bitmap bitmap;

  @NonNull
  public CharSequence title;
  @Nullable
  public CharSequence description;

  /** Value is assigned at runtime */
  @Nullable
  public Uri replacementIconUri;

  @NonNull
  public long firstAppearTime;

  public LauncherItemInfo(int type, long firstAppearTime) {
    id = "";
    title = "";
    this.type = type;
    this.firstAppearTime = firstAppearTime;
    priority = 0;
  }

  public String getNameForIconReplacementMatch() {
    switch (type) {
      case TYPE_LAUNCHER_ACTIVITY:
        return packageName;
      case TYPE_SHORTCUT:
      case TYPE_SPECIAL:
        return id;
      default:
        throw new IllegalStateException();
    }
  }

  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  public boolean shouldShowDelete(PackageManager pm) {
    boolean showDelete = false;
    switch (type) {
      case TYPE_LAUNCHER_ACTIVITY:
        try {
          showDelete = (pm.getPackageInfo(packageName, 0).applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (PackageManager.NameNotFoundException e) {
          e.printStackTrace();
        }
        break;
      case TYPE_SHORTCUT:
        showDelete = true;
      default:
        break;
    }
    return showDelete;
  }

  public void setIconImageForView(ImageView imageView, Config config, int sizePx) {
    imageView.setImageDrawable(getIcon(imageView.getContext(), config.showCustomIcon, sizePx));
  }

  /**
   * Returns the icon, loading it on demand. App icons and custom icons are rasterized at
   * {@code sizePx} instead of being kept at full resolution, and only the icons of the items
   * on screen are kept in memory: see {@link #releaseIcon()}.
   */
  @Nullable
  public Drawable getIcon(Context context, boolean showCustomIcon, int sizePx) {
    boolean useCustomIcon = showCustomIcon && replacementIconUri != null;
    if (drawable != null && iconSizePx == sizePx && iconIsCustom == useCustomIcon) {
      return drawable;
    }
    drawable = null;
    if (useCustomIcon) {
      Bitmap custom = Utils.decodeSampledBitmap(replacementIconUri.getPath(), sizePx);
      if (custom != null) {
        drawable = new BitmapDrawable(context.getResources(), custom);
      }
    }
    if (drawable == null) {
      drawable = loadDefaultIcon(context, sizePx);
      useCustomIcon = false;
    }
    iconSizePx = sizePx;
    iconIsCustom = useCustomIcon;
    return drawable;
  }

  /** The icon if it is already loaded, otherwise loads it at the size used by the grid. */
  @Nullable
  public Drawable getIcon(Context context, Config config) {
    return drawable != null ? drawable : getIcon(context, config.showCustomIcon, defaultIconSizePx(context));
  }

  /** Drops the icon so it can be garbage collected; it is reloaded on the next {@link #getIcon}. */
  public void releaseIcon() {
    drawable = null;
  }

  @Nullable
  private Drawable loadDefaultIcon(Context context, int sizePx) {
    switch (type) {
      case TYPE_LAUNCHER_ACTIVITY:
        Drawable appIcon;
        try {
          appIcon = context.getPackageManager().getActivityIcon(componentName);
        } catch (PackageManager.NameNotFoundException e) {
          appIcon = context.getPackageManager().getDefaultActivityIcon();
        }
        // Don't upscale icons smaller than the cell, it only costs memory.
        int intrinsic = Math.max(appIcon.getIntrinsicWidth(), appIcon.getIntrinsicHeight());
        int size = intrinsic > 0 ? Math.min(sizePx, intrinsic) : sizePx;
        return new BitmapDrawable(context.getResources(), Utils.createBitmapFromDrawable(appIcon, size, context));
      case TYPE_SHORTCUT:
        return bitmap != null ? new BitmapDrawable(context.getResources(), bitmap) : null;
      case TYPE_SPECIAL:
        return iconRes != 0 ? context.getResources().getDrawable(QuickIcons.res(iconRes)) : null;
      default:
        return null;
    }
  }

  private static int defaultIconSizePx(Context context) {
    return Utils.dp2Px(context, 64);
  }

  public void executeShortcut(Context context) {
    if (type != TYPE_SHORTCUT) { return; }

    if (intent != null) {
      context.startActivity(intent);
    } else {
      if (shortcutId != null) {
        if (Build.VERSION.SDK_INT < 26) {
          Toast.makeText(context, R.string.shortcut_need_o, Toast.LENGTH_SHORT).show();
          return;
        }

        if (packageName == null || componentName == null) {
          Toast.makeText(context, R.string.shortcut_data_loss, Toast.LENGTH_SHORT).show();
          return;
        }

        LauncherApps la = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);

        // TODO: Support for multi-user/work profile
        la.startShortcut(packageName, shortcutId, null, null, android.os.Process.myUserHandle());
      }
    }
  }

  public ShortcutInfo getShortcutInfo(Context context) {
    if (shortcutId != null) {
      if (Build.VERSION.SDK_INT < 26) {
        Toast.makeText(context, R.string.shortcut_need_o, Toast.LENGTH_SHORT).show();
        return null;
      }

      if (packageName == null || componentName == null) {
        Toast.makeText(context, R.string.shortcut_data_loss, Toast.LENGTH_SHORT).show();
        return null;
      }

      LauncherApps la = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
      LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
      query.setPackage(packageName);
      query.setActivity(componentName);
      query.setShortcutIds(Arrays.asList(new String[]{shortcutId}));
      query.setQueryFlags(
              LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                      | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                      | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
      );
      List<ShortcutInfo> sis = la.getShortcuts(query, android.os.Process.myUserHandle()); // TODO: Support for multi-user/work profile
      if (sis.isEmpty()) {
        Toast.makeText(context, R.string.shortcut_not_found, Toast.LENGTH_SHORT).show();
        return null;
      }
      ShortcutInfo si = sis.get(0);
      return si;
    }
    return null;
  }
}
