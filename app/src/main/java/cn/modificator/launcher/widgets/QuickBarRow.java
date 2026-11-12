package cn.modificator.launcher.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * The quick bar's row: every item gets the same width, an equal share of the row up to
 * MAX_CELL_DP, and the group is centered. The width never depends on the labels: they change
 * (the Wi-Fi network name appears, "Connecting…"), and the icons must not move when they do.
 * Labels longer than the cell are ellipsized by the item layout.
 */
public class QuickBarRow extends ViewGroup {
  private static final int MAX_CELL_DP = 96;

  private int cellWidth;

  public QuickBarRow(Context context) {
    super(context);
  }

  public QuickBarRow(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  private int visibleCount() {
    int count = 0;
    for (int i = 0; i < getChildCount(); i++) {
      if (getChildAt(i).getVisibility() != GONE) count++;
    }
    return count;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
    int count = visibleCount();
    int maxCell = Math.round(MAX_CELL_DP * getResources().getDisplayMetrics().density);
    cellWidth = count == 0 ? 0 : Math.min(maxCell, available / count);
    int height = 0;
    int cellSpec = MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY);
    int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
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
    int left = (r - l - cellWidth * visibleCount()) / 2;
    int top = getPaddingTop();
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) continue;
      child.layout(left, top, left + cellWidth, top + child.getMeasuredHeight());
      left += cellWidth;
    }
  }
}
