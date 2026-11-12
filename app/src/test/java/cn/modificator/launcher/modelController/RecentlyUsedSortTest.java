package cn.modificator.launcher.modelController;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecentlyUsedSortTest {
  private static LauncherItemInfo app(String title, long lastUsed) {
    LauncherItemInfo item = new LauncherItemInfo(LauncherItemInfo.TYPE_LAUNCHER_ACTIVITY, 0);
    item.id = title;
    item.title = title;
    item.lastUsedTime = lastUsed;
    return item;
  }

  @Test
  public void mostRecentFirstThenAlphabetical() {
    List<LauncherItemInfo> items = new ArrayList<>();
    items.add(app("Zeta", 0));
    items.add(app("Notes", 1000));
    items.add(app("Alpha", 0));
    items.add(app("KOReader", 5000));
    Collections.sort(items, new LauncherItemInfoComparator(
            LauncherItemInfoComparator.SORT_MODE_RECENTLY_USED | LauncherItemInfoComparator.SORT_ORDER_DESC));
    StringBuilder order = new StringBuilder();
    for (LauncherItemInfo item : items) order.append(item.title).append(' ');
    assertEquals("KOReader Notes Alpha Zeta ", order.toString());
  }
}
