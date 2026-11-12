package cn.modificator.launcher.widgets;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Observer;

import cn.modificator.launcher.R;
import cn.modificator.launcher.modelController.ItemCenter;
import cn.modificator.launcher.Utils;
import cn.modificator.launcher.modelController.LauncherItemInfo;
import cn.modificator.launcher.modelController.ObservableFloat;
import cn.modificator.launcher.modelController.WifiControl;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

/**
 * Created by mod on 16-4-23.
 */
public class EInkLauncherView extends ViewGroup{

  float dragDistance = 0;
  private List<LauncherItemInfo> itemInfoList = new ArrayList<>();
  float fontSize = 14;
  ObservableFloat observable = new ObservableFloat();
  ItemCenter itemCenter;
  boolean shouldHideDivider = false;

  public static final int REFRESH_LEVEL_0_LAYOUT = 0;
  public static final int REFRESH_LEVEL_1_CURRENT_ITEMS = 10;
  public static final int REFRESH_LEVEL_2_IS_MANAGING_STATE = 20;

  public EInkLauncherView(Context context) {
    super(context);
    init();
  }

  public EInkLauncherView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public EInkLauncherView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  public void setItemCenter(ItemCenter itemCenter) {
    this.itemCenter = itemCenter;
  }

  public void setShouldHideDivider(boolean shouldHideDivider) {
    this.shouldHideDivider = shouldHideDivider;
  }

  private void init() {
    dragDistance = Math.min(getMeasuredWidth(), getMeasuredHeight()) / 6f;
//    gestureDetector = new GestureDetector(getContext(), onGestureListener);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    dragDistance = Math.min(getMeasuredWidth(), getMeasuredHeight()) / 6f;

    for (int i = 0; i < itemCenter.config.rowNum; i++) {
      for (int j = 0; j < itemCenter.config.colNum; j++) {
                /*if (COL_NUM * i + j == dataList.size())
                    break AddView;*/
        int childLeft = j * getPerItemWidth();// + (j * dividerSize);
        int childRight = (j + 1) * getPerItemWidth();// + (j * dividerSize);
        int childTop = i * getPerItemHeight();// + (i * dividerSize);
        int childBottom = (i + 1) * getPerItemHeight();// + (i * dividerSize);
//                view.measure(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        getChildAt(i * itemCenter.config.colNum + j).layout(childLeft, childTop, childRight, childBottom);
      }
    }
    if (getChildCount() > 0){
      if (getChildAt(0).findViewById(R.id.itemTitle).getMeasuredWidth() == 0) {
        refresh(REFRESH_LEVEL_1_CURRENT_ITEMS);
      }
    }
  }

  private void refreshLayout() {
    observable.deleteObservers();
    removeAllViews();
    for (int i = 0; i < itemCenter.config.rowNum * itemCenter.config.colNum; i++) {
      FrameLayout itemView = (FrameLayout) LayoutInflater.from(getContext()).inflate(R.layout.launcher_item, this, false);
      itemView.setClipChildren(false);
      itemView.setClipToPadding(false);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        itemView.setClipBounds(null);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        itemView.setClipToOutline(false);
      }
      observable.addObserver((Observer) itemView.findViewById(R.id.itemTitle));
      TextView tvItemTitle = ((TextView)itemView.findViewById(R.id.itemTitle));
      tvItemTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, itemCenter.config.fontSize);
      tvItemTitle.setMinLines(itemCenter.config.itemTitleLines == 2 ? itemCenter.config.itemTitleLines :0);
      tvItemTitle.setMaxLines(itemCenter.config.itemTitleLines);
      tvItemTitle.setEllipsize(TextUtils.TruncateAt.END);

      if (shouldHideDivider) {
        itemView.setBackgroundResource(R.drawable.app_item_final);
      } else if (i == itemCenter.config.rowNum * itemCenter.config.colNum - 1) {
        itemView.setBackgroundResource(R.drawable.app_item_final);
      } else if (i % itemCenter.config.colNum == itemCenter.config.colNum - 1){
        itemView.setBackgroundResource(R.drawable.app_item_right);
      } else if (i > (itemCenter.config.rowNum - 1) * itemCenter.config.colNum - 1){
        itemView.setBackgroundResource(R.drawable.app_item_bottom);
      } else {
        itemView.setBackgroundResource(R.drawable.app_item_normal);
      }
//      if (COL_NUM * i + j < dataList.size() + 2) {
//        if (hideDivider) {
//          view.setBackgroundResource(R.drawable.app_item_final);
//        } else if (j == COL_NUM - 1 && i == ROW_NUM - 1) {
//          view.setBackgroundResource(R.drawable.app_item_final);
//        } else if (j == COL_NUM - 1)
//          view.setBackgroundResource(R.drawable.app_item_right);
//        else if (i == ROW_NUM - 1)
//          view.setBackgroundResource(R.drawable.app_item_bottom);
//        else if (!hideDivider)
//          view.setBackgroundResource(R.drawable.app_item_normal);
//      }
      addView(itemView);
    }
  }

  private void refreshCurrentItems() {
    List<LauncherItemInfo> newItems = itemCenter.getCurrentPageItemInfoList();
    // Only the icons on screen are kept in memory.
    for (LauncherItemInfo oldItem : itemInfoList) {
      if (!newItems.contains(oldItem)) {
        oldItem.releaseIcon();
      }
    }
    itemInfoList.clear();
    itemInfoList.addAll(newItems);
    int iconSizePx = getIconSizePx();

    View itemView;
    for (int i = 0; i < itemCenter.config.rowNum; i++) {
      for (int j = 0; j < itemCenter.config.colNum; j++) {
        final int position = i * itemCenter.config.colNum + j;
        itemView = getChildAt(position);
        if (itemView == null) {
          return;
        }
        if (position < itemInfoList.size() && position < getChildCount()) {
          final LauncherItemInfo itemInfo = itemInfoList.get(itemCenter.config.colNum * i + j);
          CharSequence itemTitle = itemInfo.title;

          itemInfo.setIconImageForView((ImageView) itemView.findViewById(R.id.itemIcon), itemCenter.config, iconSizePx);
          if (itemInfo.id.equals(ItemCenter.WIFI_ITEM_ID)) {
            WifiControl.bind(itemView);
          } else {
            ((TextView) itemView.findViewById(R.id.itemTitle)).setText(itemInfo.title);
          }

          itemView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
              if (position < itemInfoList.size()) {
                itemCenter.executeItemOnClick(v.getContext(), itemInfo);
              }
            }
          });
          itemView.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
              if (position < itemInfoList.size()) {
                itemCenter.executeItemOnLongClick(v.getContext(), itemInfo);
              }
              return true;
            }
          });
          itemView.findViewById(R.id.menu_delete).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
              if (position < itemInfoList.size()) {
                itemCenter.deleteItem(v.getContext(), itemInfo);
              }
            }
          });
          itemView.findViewById(R.id.menu_hide).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
              if (itemCenter.isHidden(itemInfo.id)) {
                itemCenter.removeHidden(itemInfo.id);
                v.setSelected(false);
              } else {
                itemCenter.addHidden(itemInfo.id);
                v.setSelected(true);
              }
            }
          });
          itemView.setVisibility(VISIBLE);
          itemView.setAlpha(1);
        } else if (position >= getChildCount()) {
          Log.e("launcherView", "position >= getChildCount() detected!");
        } else if (position >= itemInfoList.size()) {
          ((TextView) itemView.findViewById(R.id.itemTitle)).setText("");
          ((ImageView) itemView.findViewById(R.id.itemIcon)).setImageDrawable(null);
          //          itemView.setLongClickable(false);
          //          itemView.setClickable(false);
          //          itemView.setVisibility(GONE);
          itemView.setAlpha(0);
        }
      }
    }
  }

  private void refreshWithUpdatedManagingState() {
    PackageManager pm = getContext().getPackageManager();
    for (int i = 0; i < getChildCount(); i++) {
      if (i < itemInfoList.size()){
        if (!itemCenter.isInManagingState()){
          ((ViewGroup) getChildAt(i)).findViewById(R.id.item_menu).setVisibility(GONE);
        } else {
          ((ViewGroup) getChildAt(i)).findViewById(R.id.item_menu).setVisibility(VISIBLE);

          LauncherItemInfo itemInfo = itemInfoList.get(i);
          boolean shouldShowDelete = itemInfo.shouldShowDelete(pm);
          getChildAt(i).findViewById(R.id.menu_delete).setVisibility(shouldShowDelete ? VISIBLE : GONE);
          getChildAt(i).findViewById(R.id.menu_hide).setSelected(itemCenter.isHidden(itemInfo.id));
        }
      }
    }
  }

  public void refresh(int level) {
    if (level <= REFRESH_LEVEL_0_LAYOUT) {
      refreshLayout();
    }
    if (level <= REFRESH_LEVEL_1_CURRENT_ITEMS) {
      refreshCurrentItems();
    }
    if (level <= REFRESH_LEVEL_2_IS_MANAGING_STATE) {
      refreshWithUpdatedManagingState();
    }
  }

  public void refreshItemTitleLines() {
    for (int i = 0; i < itemCenter.config.rowNum * itemCenter.config.colNum; i++) {
      TextView tvAppName = getChildAt(i).findViewById(R.id.itemTitle);
      tvAppName.setMinLines(itemCenter.config.itemTitleLines == 2 ? itemCenter.config.itemTitleLines : 0);
      tvAppName.setMaxLines(itemCenter.config.itemTitleLines);
    }
  }

  /**
   * Size the icons are rasterized at: the icon box in launcher_item.xml is 80% of the cell
   * width and 80% of the top 70% of the cell height. Falls back to 64dp before layout.
   */
  private int getIconSizePx() {
    int width = getPerItemWidth();
    int height = getPerItemHeight();
    if (width <= 0 || height <= 0) {
      return Utils.dp2Px(getContext(), 64);
    }
    return (int) Math.min(width * 0.8f, height * 0.7f * 0.8f);
  }

  /** Clears the icons from the views so their bitmaps can be freed while in the background. */
  public void clearIcons() {
    for (int i = 0; i < getChildCount(); i++) {
      ((ImageView) getChildAt(i).findViewById(R.id.itemIcon)).setImageDrawable(null);
    }
  }

  private int getPerItemHeight() {
//        return (getAdjustedHeight() - (ROW_NUM - 2) * dividerSize) / ROW_NUM;
    return getInnerHeight() / itemCenter.config.rowNum;
  }


  private int getPerItemWidth() {
//        return (getAdjustedWidth() - (COL_NUM - 2) * dividerSize) / COL_NUM;
    return getInnerWidth() / itemCenter.config.colNum;
  }

  private int getInnerHeight() {
    return getInnerHeight(this);
  }

  private static int getInnerHeight(View v) {
    return v.getHeight() - v.getPaddingBottom() - v.getPaddingTop();
  }

  private int getInnerWidth() {
    return getInnerWidth(this);
  }

  private static int getInnerWidth(View v) {
    return v.getWidth() - v.getPaddingLeft() - v.getPaddingRight();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int itemWidthMeasureSpec = makeMeasureSpec(getPerItemWidth(), EXACTLY);
    int itemHeightMeasureSpec = makeMeasureSpec(getPerItemHeight(), EXACTLY);
    for (int i = 0; i < getChildCount(); i++) {
      getChildAt(i).measure(itemWidthMeasureSpec,itemHeightMeasureSpec);
    }
  }


  private Point touchDown = null;
  private @Nullable Boolean handleTouchDragAndDrop(MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        touchDown = new Point((int) event.getX(), (int) event.getY());
        break;
      case MotionEvent.ACTION_UP:
        if ((event.getX() > touchDown.x && dragDistance < Math.abs(event.getX() - touchDown.x)) ||
                (event.getY() > touchDown.y && dragDistance < Math.abs(event.getY() - touchDown.y))) {
          itemCenter.showPreviousPage();
          return true;
        }
        if ((event.getX() < touchDown.x && dragDistance < Math.abs(event.getX() - touchDown.x)) ||
                (event.getY() < touchDown.y && dragDistance < Math.abs(event.getY() - touchDown.y))) {
          itemCenter.showNextPage();
          return true;
        }
        return false;
    }
    return null;
  }

  //region Description
 /*   SimpleOnGestureListener onGestureListener = new SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;//super.onDown(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
//            Log.e("onScroll", Math.abs(e1.getX() - e2.getX()) + "   " + distanceX + "    " + distanceY);

            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

            Log.e("onFling", (e1.getX() - e2.getX()) + "    " + (e1.getY() - e2.getY()) + "   " + velocityX + "    " + velocityY + "    " + dragDistance);
           *//* if (((e2.getX() - e1.getX() > dragDistance || e2.getY() - e1.getY() > dragDistance))
                    ||
                    (velocityX > 500 || velocityY > 500)) {
                if (touchListener != null)
                    touchListener.toNext();
                return true;
            } else if ((e1.getX() - e2.getX() < -dragDistance || e1.getY() - e2.getY() < -dragDistance) ||
                    (velocityX < -500 || velocityY < -500)) {
                if (touchListener != null)
                    touchListener.toLast();
                return true;
            }*//*
            if (e1.getX() - e2.getX() > 100 && Math.abs(velocityX) > 50) {
                if (touchListener != null)
                    touchListener.toNext();
                return true;
            } else if (e2.getX() - e1.getX() > 100 && Math.abs(velocityX) > 50) {
                if (touchListener != null)
                    touchListener.toLast();
                return true;
            }
            return false;//super.onFling(e1, e2, velocityX, velocityY);
        }
    };
*/

//  GestureDetector.OnGestureListener onGestureListener = new GestureDetector.SimpleOnGestureListener(){
//    @Override
//    public boolean onSingleTapConfirmed(MotionEvent e) {
//      Log.e("====================","onSingleTapConfirmed");
//      return super.onSingleTapConfirmed(e);
//    }
//
//    @Override
//    public void onLongPress(MotionEvent e) {
//      Log.e("====================","onLongPress");
//      super.onLongPress(e);
//    }
//
//    @Override
//    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//      Log.e("====================","onFling");
//      return super.onFling(e1, e2, velocityX, velocityY);
//    }
//  };

  //endregion

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    Boolean handleResult = handleTouchDragAndDrop(event);
    if (handleResult != null) {
      return handleResult;
    }
    return super.onInterceptTouchEvent(event);
  }

  public void setFontSize(float fontSize) {
    this.fontSize = fontSize;
    observable.set(fontSize);
  }

  public float getFontSize() {
    return fontSize;
  }
}
