package io.github.giovaboy.inkpalmtools;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** The apps the firmware kills 30 seconds after leaving the screen, with a switch to stop it. */
public class BackgroundActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<BackgroundKill.App> apps = new ArrayList<>();
  private AppsAdapter adapter;
  private TextView summary;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_apps);
    ((TextView) findViewById(R.id.title)).setText(R.string.background_title);
    findViewById(R.id.filter).setVisibility(View.GONE);
    summary = findViewById(R.id.summary);
    adapter = new AppsAdapter();
    ((ListView) findViewById(R.id.list)).setAdapter(adapter);
    new Thread(() -> {
      List<BackgroundKill.App> list = BackgroundKill.list(this);
      handler.post(() -> {
        apps.clear();
        apps.addAll(list);
        adapter.notifyDataSetChanged();
        updateSummary();
      });
    }, "apps").start();
  }

  private void updateSummary() {
    int running = 0;
    for (BackgroundKill.App app : apps) {
      if (app.keepsRunning()) running++;
    }
    summary.setText(getString(R.string.background_summary, apps.size(), running));
  }

  private void toggle(BackgroundKill.App app) {
    String error = BackgroundKill.set(this, app, !app.keepsRunning());
    if (error != null) {
      Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }
    adapter.notifyDataSetChanged();
    updateSummary();
  }

  private class AppsAdapter extends BaseAdapter {
    @Override
    public int getCount() {
      return apps.size();
    }

    @Override
    public BackgroundKill.App getItem(int position) {
      return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
      if (view == null) {
        view = LayoutInflater.from(parent.getContext()).inflate(R.layout.app_row, parent, false);
      }
      final BackgroundKill.App app = getItem(position);
      ((TextView) view.findViewById(R.id.label)).setText(app.label);
      ((TextView) view.findViewById(R.id.packageName)).setText(app.packageName);
      Switch toggle = view.findViewById(R.id.toggle);
      // Where the setting can't be read there is nothing to switch.
      boolean known = app.freeze != null;
      toggle.setVisibility(known ? View.VISIBLE : View.GONE);
      TextView note = view.findViewById(R.id.protectedLabel);
      note.setVisibility(known ? View.GONE : View.VISIBLE);
      note.setText(R.string.background_unknown);
      toggle.setChecked(app.keepsRunning());
      view.setOnClickListener(known ? v -> toggle(app) : null);
      return view;
    }
  }
}
