package cn.modificator.launcher.widgets;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import cn.modificator.launcher.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27, qualifiers = "w411dp-h731dp-280dpi")
// Native graphics: real text widths, which is what used to move the icons.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class QuickBarRowTest {

  private static int[] lefts(ViewGroup bar) {
    int[] result = new int[bar.getChildCount()];
    for (int i = 0; i < result.length; i++) {
      result[i] = bar.getChildAt(i).getLeft();
    }
    return result;
  }

  private static void relayout() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  /** Measures and lays out the bar at the device width, as a frame would. */
  private static void layout(View bar) {
    bar.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    bar.layout(0, 0, bar.getMeasuredWidth(), bar.getMeasuredHeight());
  }

  @Test
  public void iconsDontMoveWhenALabelChanges() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    QuickBarRow bar = new QuickBarRow(activity);
    // Four items (some hidden): cells have room, which is where label widths used to show.
    String[] labels = {"Wi-Fi", "FTP", "Luce", "Blocca"};
    for (String label : labels) {
      View item = LayoutInflater.from(activity).inflate(R.layout.quick_item, bar, false);
      ((TextView) item.findViewById(R.id.itemTitle)).setText(label);
      bar.addView(item);
    }
    activity.setContentView(bar, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    relayout();
    layout(bar);
    int[] before = lefts(bar);

    // The Wi-Fi item while connecting, then with a long network name, then short again.
    TextView wifi = bar.getChildAt(0).findViewById(R.id.itemTitle);
    for (String label : new String[]{"Connessione in corso…", "A-very-long-network-name", "On"}) {
      wifi.setText(label);
      layout(bar);
      int[] after = lefts(bar);
      for (int i = 0; i < before.length; i++) {
        assertEquals("item " + i + " with label " + label, before[i], after[i]);
      }
    }
    // Equal cells, side by side.
    for (int i = 1; i < bar.getChildCount(); i++) {
      assertEquals(bar.getChildAt(0).getWidth(), bar.getChildAt(i).getWidth());
      assertEquals(bar.getChildAt(i - 1).getRight(), bar.getChildAt(i).getLeft());
    }
  }

  @Test
  public void fewItemsStayCloseTogether() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    QuickBarRow bar = new QuickBarRow(activity);
    for (int i = 0; i < 2; i++) {
      bar.addView(LayoutInflater.from(activity).inflate(R.layout.quick_item, bar, false));
    }
    activity.setContentView(bar, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    relayout();
    // Capped at 96dp each and centered, not spread over the whole row.
    int cell = Math.round(96 * activity.getResources().getDisplayMetrics().density);
    assertEquals(cell, bar.getChildAt(0).getWidth());
    assertEquals(bar.getWidth() / 2, bar.getChildAt(1).getLeft(), 1);
  }
}
