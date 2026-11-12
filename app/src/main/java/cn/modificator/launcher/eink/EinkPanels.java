package cn.modificator.launcher.eink;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import cn.modificator.launcher.R;
import cn.modificator.launcher.Utils;

/**
 * The launcher's own front light and contrast panels: centered, with big +/- buttons, which
 * suit e-ink better than sliders. When the firmware doesn't let the launcher change these,
 * the system panels are opened instead.
 */
public final class EinkPanels {
  private static final int DEFAULT_BRIGHTNESS = 6;

  private EinkPanels() {
  }

  public static void showLight(final Context context) {
    if (!(context instanceof Activity) || !EinkControls.isSupported(context)) {
      openSystemLightPanel(context);
      return;
    }
    View view = LayoutInflater.from(context).inflate(R.layout.panel_light, null);
    final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
    final int[] light = EinkControls.getLight(context); // {brightness, temperature}
    final int[] lastOnBrightness = {light[0] > 0 ? light[0] : DEFAULT_BRIGHTNESS};

    final TextView brightnessLabel = view.findViewById(R.id.lightBrightnessLabel);
    final TextView temperatureLabel = view.findViewById(R.id.lightTemperatureLabel);
    final ProgressBar brightnessBar = view.findViewById(R.id.lightBrightnessBar);
    final ProgressBar temperatureBar = view.findViewById(R.id.lightTemperatureBar);
    final TextView toggle = view.findViewById(R.id.lightToggle);

    final Runnable updateViews = new Runnable() {
      @Override
      public void run() {
        brightnessLabel.setText(context.getString(R.string.panel_light_brightness, light[0]));
        temperatureLabel.setText(context.getString(R.string.panel_light_temperature, light[1]));
        brightnessBar.setProgress(light[0]);
        temperatureBar.setProgress(light[1]);
        toggle.setText(light[0] > 0 ? R.string.panel_light_off : R.string.panel_light_on);
      }
    };
    final Runnable apply = new Runnable() {
      @Override
      public void run() {
        if (light[0] > 0) {
          lastOnBrightness[0] = light[0];
        }
        if (EinkControls.setLight(context, light[0], light[1])) {
          updateViews.run();
        } else {
          dialog.dismiss();
          fallBackToSystem(context, true);
        }
      }
    };

    view.findViewById(R.id.lightBrightnessDown).setOnClickListener(new StepListener(light, 0, -1, apply));
    view.findViewById(R.id.lightBrightnessUp).setOnClickListener(new StepListener(light, 0, +1, apply));
    view.findViewById(R.id.lightTemperatureDown).setOnClickListener(new StepListener(light, 1, -1, apply));
    view.findViewById(R.id.lightTemperatureUp).setOnClickListener(new StepListener(light, 1, +1, apply));
    toggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        light[0] = light[0] > 0 ? 0 : lastOnBrightness[0];
        apply.run();
      }
    });
    view.findViewById(R.id.panelDone).setOnClickListener(dismiss(dialog));

    updateViews.run();
    dialog.show();
  }

  public static void showContrast(final Context context) {
    final int current = (context instanceof Activity && EinkControls.isSupported(context))
            ? EinkControls.getContrast(context) : -1;
    if (current < 0) {
      openSystemContrastPanel(context);
      return;
    }
    View view = LayoutInflater.from(context).inflate(R.layout.panel_contrast, null);
    final AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
    final int[] level = {current};
    final LinearLayout segments = view.findViewById(R.id.contrastSegments);

    final Runnable updateViews = new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < segments.getChildCount(); i++) {
          segments.getChildAt(i).setBackgroundResource(
                  i <= level[0] ? R.drawable.panel_segment_on : R.drawable.panel_segment_off);
        }
      }
    };
    final Runnable apply = new Runnable() {
      @Override
      public void run() {
        if (EinkControls.setContrast(context, level[0])) {
          updateViews.run();
        } else {
          dialog.dismiss();
          fallBackToSystem(context, false);
        }
      }
    };

    // One block per level, like the system panel; tapping a block selects it.
    int gap = Utils.dp2Px(context, 4);
    for (int i = 0; i <= EinkControls.CONTRAST_MAX; i++) {
      View segment = new View(context);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
      params.leftMargin = i > 0 ? gap : 0;
      final int segmentLevel = i;
      segment.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          level[0] = segmentLevel;
          apply.run();
        }
      });
      segments.addView(segment, params);
    }
    view.findViewById(R.id.contrastDown).setOnClickListener(new StepListener(level, 0, -1, apply, EinkControls.CONTRAST_MAX));
    view.findViewById(R.id.contrastUp).setOnClickListener(new StepListener(level, 0, +1, apply, EinkControls.CONTRAST_MAX));
    view.findViewById(R.id.contrastRefreshMode).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dialog.dismiss();
        openSystemRefreshModePanel(context);
      }
    });
    view.findViewById(R.id.panelDone).setOnClickListener(dismiss(dialog));

    updateViews.run();
    dialog.show();
  }

  // ---- System panels (fallback) ----

  public static void openSystemLightPanel(Context context) {
    try {
      context.startActivity(systemUiIntent("com.android.systemui.moan.MoanBrightnessDialogActivity"));
    } catch (Exception e) {
      context.sendBroadcast(new Intent("com.mogu.show_brightness_dialog"));
    }
  }

  public static void openSystemContrastPanel(Context context) {
    try {
      context.startActivity(systemUiIntent("com.android.systemui.einksettings.EinkSettingsActivity"));
    } catch (Exception ignored) {
      // Not a Moaan device.
    }
  }

  public static void openSystemRefreshModePanel(Context context) {
    try {
      context.startActivity(systemUiIntent("com.android.systemui.einksettings.RefreshModeSelectDialog"));
    } catch (Exception ignored) {
      // Not a Moaan device.
    }
  }

  private static Intent systemUiIntent(String className) {
    return new Intent()
            .setComponent(new ComponentName("com.android.systemui", className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
  }

  private static void fallBackToSystem(Context context, boolean light) {
    Toast.makeText(context, R.string.panel_not_supported, Toast.LENGTH_LONG).show();
    if (light) {
      openSystemLightPanel(context);
    } else {
      openSystemContrastPanel(context);
    }
  }

  private static View.OnClickListener dismiss(final AlertDialog dialog) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dialog.dismiss();
      }
    };
  }

  /** Moves one value of a small state array by one step, within 0..max, then applies it. */
  private static final class StepListener implements View.OnClickListener {
    private final int[] values;
    private final int index;
    private final int step;
    private final Runnable apply;
    private final int max;

    StepListener(int[] values, int index, int step, Runnable apply) {
      this(values, index, step, apply, EinkControls.LIGHT_MAX);
    }

    StepListener(int[] values, int index, int step, Runnable apply, int max) {
      this.values = values;
      this.index = index;
      this.step = step;
      this.apply = apply;
      this.max = max;
    }

    @Override
    public void onClick(View v) {
      int next = Math.max(0, Math.min(max, values[index] + step));
      if (next != values[index]) {
        values[index] = next;
        apply.run();
      }
    }
  }
}
