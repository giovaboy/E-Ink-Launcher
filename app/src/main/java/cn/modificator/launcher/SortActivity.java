package cn.modificator.launcher;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import cn.modificator.launcher.modelController.LauncherItemInfo;
import cn.modificator.launcher.modelController.LauncherItemInfoComparator;

public class SortActivity extends Activity {
  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(LocaleHelper.wrap(newBase));
  }

  public static final String ALL_ITEMS_WITH_PRIORITY_KEY = "allItemsWithPriorityKey";
  private ArrayList<LauncherItemInfo> mAllItems;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_sort);

    mAllItems = Config.gson.fromJson(getIntent().getStringExtra(ALL_ITEMS_WITH_PRIORITY_KEY), new TypeToken<ArrayList<LauncherItemInfo>>() {}.getType());

    Collections.sort(mAllItems, new LauncherItemInfoComparator(
            LauncherItemInfoComparator.SORT_MODE_CUSTOM_WITH_FIRST_APPEAR
            | LauncherItemInfoComparator.SORT_ORDER_ASC));

    Iterator<LauncherItemInfo> it = mAllItems.iterator();
    while (it.hasNext()) {
      LauncherItemInfo itemInfo = it.next();
      if (itemInfo.type == LauncherItemInfo.TYPE_SPECIAL) {
        it.remove();
        continue;
      }
    }

    AutoFitRecyclerView recyclerView = findViewById(R.id.sort_recycler_view);
    recyclerView.setAdapter(new SortItemAdapter(mAllItems));
    findViewById(R.id.btnSaveSort).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent resultIntent = new Intent();
        HashMap<String, Integer> priorityMap = new HashMap<>();
        for (LauncherItemInfo itemInfo : mAllItems) {
          if (itemInfo.priority != 0) {
            priorityMap.put(itemInfo.id, itemInfo.priority);
          }
        }
        resultIntent.putExtra(Launcher.PRIORITY_KEY, priorityMap);
        setResult(RESULT_OK, resultIntent);
        finish();
      }
    });
    findViewById(R.id.btnExitSort).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
  }
}

class SortItemAdapter extends RecyclerView.Adapter<SortItemAdapter.SortItemHolder> {
  private List<LauncherItemInfo> mAllItems;
  SortItemAdapter(List<LauncherItemInfo> mAllItems) {
    this.mAllItems = mAllItems;
  }

  @NonNull
  @Override
  public SortItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new SortItemHolder(
            LayoutInflater.from(parent.getContext())
              .inflate(R.layout.sort_item, parent, false),
            null
    );
  }

  @Override
  public void onBindViewHolder(@NonNull final SortItemHolder holder, int position) {
    final LauncherItemInfo itemInfo = mAllItems.get(position);
    holder.itemInfo = itemInfo;
    ImageView icon = holder.itemView.findViewById(R.id.sort_item_icon);
    TextView name = holder.itemView.findViewById(R.id.sort_item_name);
    final EditText priorityInput = holder.itemView.findViewById(R.id.sort_item_priority_input);

    priorityInput.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        try {
          itemInfo.priority = Integer.parseInt(priorityInput.getText().toString());
        } catch (NumberFormatException e) {
          e.printStackTrace();
        }
      }

      @Override
      public void afterTextChanged(Editable s) { }
    });

    // Loaded on demand: only the rows on screen get an icon.
    icon.setImageDrawable(holder.itemInfo.getIcon(icon.getContext(), false, Utils.dp2Px(icon.getContext(), 48)));
    name.setText(holder.itemInfo.title);
    priorityInput.setText(String.valueOf(holder.itemInfo.priority));
  }

  @Override
  public void onViewRecycled(@NonNull SortItemHolder holder) {
    super.onViewRecycled(holder);
    if (holder.itemInfo != null) {
      holder.itemInfo.releaseIcon();
    }
  }

  @Override
  public int getItemCount() {
    return mAllItems.size();
  }

  static class SortItemHolder extends RecyclerView.ViewHolder {
    LauncherItemInfo itemInfo;
    View itemView;
    SortItemHolder(View itemView, LauncherItemInfo itemInfo) {
      super(itemView);
      this.itemView = itemView;
      this.itemInfo = itemInfo;
    }
  }
}
