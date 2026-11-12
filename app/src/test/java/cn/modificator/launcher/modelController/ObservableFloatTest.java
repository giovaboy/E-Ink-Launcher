package cn.modificator.launcher.modelController;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ObservableFloatTest {
  @Test
  public void notifiesObserversOnTheFirstChange() {
    ObservableFloat observable = new ObservableFloat();
    final float[] seen = {-1};
    observable.addObserver((o, arg) -> seen[0] = (Float) arg);
    observable.set(16f);
    assertEquals(16f, seen[0], 0f);
    observable.set(18f);
    assertEquals(18f, seen[0], 0f);
  }
}
