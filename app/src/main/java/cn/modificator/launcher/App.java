package cn.modificator.launcher;

import android.content.Context;
import android.app.Application;

public class App extends Application {
  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(LocaleHelper.wrap(newBase));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    CrashCapture.getInstance().init(this, 1, Launcher.class);
  }
}
