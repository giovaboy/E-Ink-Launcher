package cn.modificator.launcher.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * The quick bar's row: every item gets the same width, that of the widest one (or an equal
 * share of the row if they don't all fit), and the group is centered. Equal widths keep the
 * icons evenly spaced whatever their labels, and labels stay on one line. It re-measures by
 * itself when a label changes (e.g. the Wi-Fi network name appears).
 */
public class QuickBarRow extends ViewGroup {
  private int cellWidth;

  public QuickBarRow(Context context) {
    super(context);
  }

  public QuickBarRow(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
    int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    int count = 0;
    int widest = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) continue;
      child.measure(unspecified, unspecified);
      widest = Math.max(widest, child.getMeasuredWidth());
      count++;
    }
    cellWidth = count == 0 ? 0 : Math.min(widest, available / count);
    int height = 0;
    int cellSpec = MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY);
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) continue;
      child.measure(cellSpec, unspecified);
      height = Math.max(height, child.getMeasuredHeight());
    }
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int count = 0;
    for (int i = 0; i < getChildCount(); i++) {
      if (getChildAt(i).getVisibility() != GONE) count++;
    }
    int left = (r - l - cellWidth * count) / 2;
    int top = getPaddingTop();
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) continue;
      child.layout(left, top, left + cellWidth, top + child.getMeasuredHeight());
      left += cellWidth;
    }
  }
}
