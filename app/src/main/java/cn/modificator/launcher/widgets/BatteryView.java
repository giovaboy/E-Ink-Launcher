package cn.modificator.launcher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * Battery indicator of the bottom bar, drawn in pure black and white for e-ink (gray turns into
 * a dither pattern). Three styles: a battery icon, a ring with the percentage inside, or
 * nothing (STYLE_TEXT: the launcher then shows only the percentage text next to it).
 */
public class BatteryView extends View {
  public static final int STYLE_ICON = 0;
  public static final int STYLE_RING = 1;
  public static final int STYLE_TEXT = 2;

  private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint boltOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final Rect textBounds = new Rect();
  private final Path bolt = new Path();

  private int style = STYLE_ICON;
  /** 0..100, or -1 when unknown. */
  private int level = -1;
  private boolean charging;

  public BatteryView(Context context) {
    super(context);
    init();
  }

  public BatteryView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public BatteryView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setColor(0xff000000);
    fillPaint.setStyle(Paint.Style.FILL);
    fillPaint.setColor(0xff000000);
    textPaint.setColor(0xff000000);
    textPaint.setFakeBoldText(true);
    textPaint.setTextAlign(Paint.Align.CENTER);
    boltPaint.setStyle(Paint.Style.FILL);
    boltPaint.setColor(0xff000000);
    // A white outline keeps the bolt visible both on the black fill and on the white background.
    boltOutlinePaint.setStyle(Paint.Style.STROKE);
    boltOutlinePaint.setColor(0xffffffff);
    boltOutlinePaint.setStrokeJoin(Paint.Join.ROUND);
  }

  public void setStyle(int style) {
    if (this.style != style) {
      this.style = style;
      requestLayout();
      invalidate();
    }
  }

  public void setLevel(int level) {
    this.level = level;
    invalidate();
  }

  public void setCharging(boolean charging) {
    this.charging = charging;
    invalidate();
  }

  private float dp(float value) {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width;
    int height;
    switch (style) {
      case STYLE_RING:
        width = height = Math.round(dp(30));
        break;
      case STYLE_TEXT:
        width = height = 0;
        break;
      case STYLE_ICON:
      default:
        width = Math.round(dp(30));
        height = Math.round(dp(16));
        break;
    }
    setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (style == STYLE_RING) {
      drawRing(canvas);
    } else if (style == STYLE_ICON) {
      drawIcon(canvas);
    }
  }

  private void drawIcon(Canvas canvas) {
    float stroke = dp(2);
    float nubWidth = dp(3);
    float w = getWidth();
    float h = getHeight();
    // Body
    strokePaint.setStrokeWidth(stroke);
    rect.set(stroke / 2, stroke / 2, w - nubWidth - stroke / 2, h - stroke / 2);
    canvas.drawRoundRect(rect, dp(3), dp(3), strokePaint);
    // Nub
    rect.set(w - nubWidth, h * 0.3f, w, h * 0.7f);
    canvas.drawRoundRect(rect, dp(1), dp(1), fillPaint);
    // Charge level, inset from the outline
    if (level > 0) {
      float inset = stroke + dp(1.5f);
      float left = inset;
      float right = w - nubWidth - inset;
      rect.set(left, inset, left + (right - left) * Math.min(level, 100) / 100f, h - inset);
      canvas.drawRoundRect(rect, dp(1), dp(1), fillPaint);
    }
    if (charging) {
      drawBolt(canvas, (w - nubWidth) / 2f, h / 2f, h * 0.9f);
    }
  }

  private void drawRing(Canvas canvas) {
    float size = Math.min(getWidth(), getHeight());
    float thick = size / 8f;
    float cx = getWidth() / 2f;
    float cy = getHeight() / 2f;
    float radius = (size - thick) / 2f;
    // Thin black track instead of a gray one, thick black arc for the charge.
    strokePaint.setStrokeWidth(dp(1));
    canvas.drawCircle(cx, cy, radius, strokePaint);
    if (level > 0) {
      strokePaint.setStrokeWidth(thick);
      rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
      canvas.drawArc(rect, -90, Math.min(level, 100) * 3.6f, false, strokePaint);
    }
    if (charging) {
      drawBolt(canvas, cx, cy, size * 0.5f);
    } else {
      String text = level >= 0 ? String.valueOf(Math.min(level, 99)) : "?";
      textPaint.setTextSize(size * 0.38f);
      textPaint.getTextBounds(text, 0, text.length(), textBounds);
      canvas.drawText(text, cx, cy + textBounds.height() / 2f, textPaint);
    }
  }

  /** A lightning bolt of the given height centered on (cx, cy). */
  private void drawBolt(Canvas canvas, float cx, float cy, float height) {
    float u = height / 10f;
    bolt.reset();
    bolt.moveTo(cx + 1 * u, cy - 5 * u);
    bolt.lineTo(cx - 2.5f * u, cy + 0.6f * u);
    bolt.lineTo(cx - 0.2f * u, cy + 0.6f * u);
    bolt.lineTo(cx - 1 * u, cy + 5 * u);
    bolt.lineTo(cx + 2.5f * u, cy - 0.8f * u);
    bolt.lineTo(cx + 0.2f * u, cy - 0.8f * u);
    bolt.close();
    boltOutlinePaint.setStrokeWidth(dp(1.5f));
    canvas.drawPath(bolt, boltOutlinePaint);
    canvas.drawPath(bolt, boltPaint);
  }
}
