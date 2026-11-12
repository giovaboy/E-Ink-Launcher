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

/** The apps that can be disabled: the suggested vendor apps, or all of them. */
public class AppsActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<AppControl.App> apps = new ArrayList<>();
  private boolean suggestedOnly = true;
  private AppsAdapter adapter;
  private TextView filter;
  private TextView summary;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_apps);
    filter = findViewById(R.id.filter);
    summary = findViewById(R.id.summary);
    adapter = new AppsAdapter();
    ((ListView) findViewById(R.id.list)).setAdapter(adapter);
    filter.setOnClickListener(v -> {
      suggestedOnly = !suggestedOnly;
      load();
    });
    load();
  }

  private void load() {
    filter.setText(suggestedOnly ? R.string.apps_show_all : R.string.apps_show_suggested);
    new Thread(() -> {
      List<AppControl.App> list = AppControl.list(this, suggestedOnly);
      handler.post(() -> {
        apps.clear();
        apps.addAll(list);
        adapter.notifyDataSetChanged();
        updateSummary();
      });
    }, "apps").start();
  }

  private void updateSummary() {
    int disabled = 0;
    for (AppControl.App app : apps) {
      if (!app.enabled) disabled++;
    }
    long[] ram = Diagnostics.ram(this);
    summary.setText(getString(suggestedOnly ? R.string.apps_summary_suggested : R.string.apps_summary_all,
            apps.size(), disabled, ram[1]));
  }

  private void toggle(AppControl.App app) {
    String error = AppControl.setEnabled(this, app, !app.enabled);
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
    public AppControl.App getItem(int position) {
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
      final AppControl.App app = getItem(position);
      ((TextView) view.findViewById(R.id.label)).setText(app.label);
      ((TextView) view.findViewById(R.id.packageName)).setText(app.packageName);
      Switch toggle = view.findViewById(R.id.toggle);
      TextView protectedLabel = view.findViewById(R.id.protectedLabel);
      toggle.setVisibility(app.isProtected ? View.GONE : View.VISIBLE);
      protectedLabel.setVisibility(app.isProtected ? View.VISIBLE : View.GONE);
      toggle.setChecked(app.enabled);
      view.setOnClickListener(app.isProtected ? null : v -> toggle(app));
      return view;
    }
  }
}
