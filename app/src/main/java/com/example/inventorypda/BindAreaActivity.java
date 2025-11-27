package com.example.inventorypda;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.media.MediaPlayer;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BindAreaActivity extends AppCompatActivity {

    // UI组件
    private EditText etPlate;
    private Button btnScanPlate;
    private Button btnBindArea;
    private Button btnUnbind;
    private Button btnChangePlate;
    private Button btnBatchBind; // 新增批量绑定按钮
    private ListView lvItems;

    // 数据相关
    private List<BindAreaItem> itemList;
    private BindAreaAdapter adapter;
    private String currentPlate = "";
    private ApiClient apiClient;

    // 声音相关
    private MediaPlayer bindSuccessSound;
    private MediaPlayer unbindSuccessSound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bind_area);

        initSounds();
        initViews();
        initData();
        setupListeners();
    }

    private void initSounds() {
        try {
            bindSuccessSound = MediaPlayer.create(this, R.raw.bind_success_sound);
            unbindSuccessSound = MediaPlayer.create(this, R.raw.unbind_success_sound);
        } catch (Exception e) {
            Log.e("SoundError", "无法加载声音文件", e);
        }
    }

    private void playSound(MediaPlayer sound) {
        if (sound != null) {
            try {
                if (sound.isPlaying()) {
                    sound.seekTo(0);
                } else {
                    sound.start();
                }
            } catch (Exception e) {
                Log.e("SoundError", "播放声音失败", e);
            }
        }
    }

    private void initViews() {
        etPlate = findViewById(R.id.etPlate);
        btnScanPlate = findViewById(R.id.btnScanPlate);
        btnBindArea = findViewById(R.id.btnBindArea);
        btnUnbind = findViewById(R.id.btnUnbind);
        btnChangePlate = findViewById(R.id.btnChangePlate);
        btnBatchBind = findViewById(R.id.btnBatchBind); // 初始化批量绑定按钮
        lvItems = findViewById(R.id.lvItems);
    }

    private void initData() {
        apiClient = ApiClient.getInstance(this);
        itemList = new ArrayList<>();
        adapter = new BindAreaAdapter(this, itemList);
        lvItems.setAdapter(adapter);
    }

    private void setupListeners() {
        btnScanPlate.setOnClickListener(v -> loadItemsByPlate());
        btnBindArea.setOnClickListener(v -> showAreaInputDialog());
        btnUnbind.setOnClickListener(v -> showClearRecordsConfirm());
        btnChangePlate.setOnClickListener(v -> showChangePlateDialog());
        btnBatchBind.setOnClickListener(v -> startBatchBindActivity()); // 设置批量绑定点击事件

        lvItems.setOnItemLongClickListener((parent, view, position, id) -> {
            BindAreaItem item = itemList.get(position);
            if (item.板标 == null || item.板标.isEmpty()) {
                Toast.makeText(BindAreaActivity.this, "无法删除板标为空的明细", Toast.LENGTH_SHORT).show();
                return true;
            }
            showSingleDeleteConfirm(item);
            return true;
        });
    }

    // 新增方法：启动批量绑定区域Activity
    private void startBatchBindActivity() {
        Intent intent = new Intent(BindAreaActivity.this, AreaBatchBindActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer(bindSuccessSound);
        releaseMediaPlayer(unbindSuccessSound);
    }

    private void releaseMediaPlayer(MediaPlayer mp) {
        if (mp != null) {
            mp.release();
        }
    }

    private void loadItemsByPlate() {
        String plate = etPlate.getText().toString().trim();
        if (plate.isEmpty()) {
            Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
            return;
        }
        currentPlate = plate;

        apiClient.queryReceiving("plate", plate, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray data = response.getJSONArray("data");
                            itemList.clear();

                            for (int i = 0; i < data.length(); i++) {
                                JSONObject itemJson = data.getJSONObject(i);
                                itemList.add(new BindAreaItem(
                                        itemJson.getInt("id"),
                                        itemJson.getString("商品货号"),
                                        itemJson.getString("板标"),
                                        itemJson.optString("区域", ""),
                                        itemJson.optString("创建时间", "")
                                ));
                            }

                            Collections.sort(itemList, new Comparator<BindAreaItem>() {
                                @Override
                                public int compare(BindAreaItem item1, BindAreaItem item2) {
                                    String area1 = item1.区域.isEmpty() ? "zzzzz" : item1.区域;
                                    String area2 = item2.区域.isEmpty() ? "zzzzz" : item2.区域;
                                    return area2.compareTo(area1);
                                }
                            });

                            adapter.notifyDataSetChanged();
                            Toast.makeText(BindAreaActivity.this,
                                    "加载成功，共" + itemList.size() + "条明细（区域降序）",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(BindAreaActivity.this,
                                    response.getString("message"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(BindAreaActivity.this, "数据重复", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(BindAreaActivity.this,
                        "加载失败：" + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showAreaInputDialog() {
        String plate = etPlate.getText().toString().trim();
        if (plate.isEmpty()) {
            Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText inputArea = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle("输入柜号")
                .setView(inputArea)
                .setPositiveButton("确认", (dialog, which) -> {
                    String area = inputArea.getText().toString().trim();
                    if (area.isEmpty()) {
                        Toast.makeText(BindAreaActivity.this, "柜号不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    bindAreaToPlate(plate, area);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void bindAreaToPlate(String plate, String area) {
        apiClient.bindArea(plate, area, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            playSound(bindSuccessSound);
                            Toast.makeText(BindAreaActivity.this, "绑定成功", Toast.LENGTH_SHORT).show();

                            // 先刷新明细界面
                            loadItemsByPlateWithCallback(() -> {
                                // 刷新完成后清空输入框
                                etPlate.setText("");
                            });
                        } else {
                            Toast.makeText(BindAreaActivity.this,
                                    response.getString("message"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(BindAreaActivity.this, "绑定失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(BindAreaActivity.this,
                        "绑定失败：" + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // 新增方法：带回调的加载明细方法
    private void loadItemsByPlateWithCallback(Runnable callback) {
        String plate = etPlate.getText().toString().trim();
        if (plate.isEmpty()) {
            Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
            return;
        }
        currentPlate = plate;

        apiClient.queryReceiving("plate", plate, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray data = response.getJSONArray("data");
                            itemList.clear();

                            for (int i = 0; i < data.length(); i++) {
                                JSONObject itemJson = data.getJSONObject(i);
                                itemList.add(new BindAreaItem(
                                        itemJson.getInt("id"),
                                        itemJson.getString("商品货号"),
                                        itemJson.getString("板标"),
                                        itemJson.optString("区域", ""),
                                        itemJson.optString("创建时间", "")
                                ));
                            }

                            Collections.sort(itemList, new Comparator<BindAreaItem>() {
                                @Override
                                public int compare(BindAreaItem item1, BindAreaItem item2) {
                                    String area1 = item1.区域.isEmpty() ? "zzzzz" : item1.区域;
                                    String area2 = item2.区域.isEmpty() ? "zzzzz" : item2.区域;
                                    return area2.compareTo(area1);
                                }
                            });

                            adapter.notifyDataSetChanged();

                            // 执行回调
                            if (callback != null) {
                                callback.run();
                            }
                        } else {
                            Toast.makeText(BindAreaActivity.this,
                                    response.getString("message"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(BindAreaActivity.this, "数据重复", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(BindAreaActivity.this,
                        "加载失败：" + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // 新增：显示更换板标对话框
    private void showChangePlateDialog() {
        String currentPlate = etPlate.getText().toString().trim();
        if (currentPlate.isEmpty()) {
            Toast.makeText(this, "请先输入要更换的板标", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建包含两个输入框的布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final EditText etOldPlate = new EditText(this);
        etOldPlate.setHint("旧板标");
        etOldPlate.setText(currentPlate);
        etOldPlate.setEnabled(false); // 旧板标不可编辑
        layout.addView(etOldPlate);

        final EditText etNewPlate = new EditText(this);
        etNewPlate.setHint("请输入新板标");
        layout.addView(etNewPlate);

        new AlertDialog.Builder(this)
                .setTitle("更换板标")
                .setView(layout)
                .setPositiveButton("确认更换", (dialog, which) -> {
                    String newPlate = etNewPlate.getText().toString().trim();
                    if (newPlate.isEmpty()) {
                        Toast.makeText(BindAreaActivity.this, "请输入新板标", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPlate.equals(currentPlate)) {
                        Toast.makeText(BindAreaActivity.this, "新板标不能与旧板标相同", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    changePlate(currentPlate, newPlate);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 新增：更换板标网络请求
    private void changePlate(String oldPlate, String newPlate) {
        apiClient.changePlate(oldPlate, newPlate, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            Toast.makeText(BindAreaActivity.this, "更换板标成功", Toast.LENGTH_SHORT).show();
                            // 刷新界面
                            etPlate.setText(newPlate);
                            loadItemsByPlate();
                        } else {
                            Toast.makeText(BindAreaActivity.this,
                                    response.getString("message"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(BindAreaActivity.this, "更换板标失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(BindAreaActivity.this,
                        "更换板标失败：" + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // 修改：清空记录确认对话框
    private void showClearRecordsConfirm() {
        String plate = etPlate.getText().toString().trim();
        if (plate.isEmpty()) {
            Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("清空记录")
                .setMessage("确认清空板标 " + plate + " 的所有记录？\n（这将清空板标、商品货号、区域、提单号等字段，但保留记录）")
                .setPositiveButton("确认清空", (dialog, which) -> clearPlateFields(plate))
                .setNegativeButton("取消", null)
                .show();
    }

    // 新增：清空板标字段网络请求
    private void clearPlateFields(String plate) {
        apiClient.clearPlateFields(plate, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            Toast.makeText(BindAreaActivity.this, "清空记录成功", Toast.LENGTH_SHORT).show();
                            // 刷新界面
                            loadItemsByPlate();
                        } else {
                            Toast.makeText(BindAreaActivity.this,
                                    response.getString("message"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(BindAreaActivity.this, "清空记录失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(BindAreaActivity.this,
                        "清空记录失败：" + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void batchDeleteNonEmptyPlateItems() {
        if (itemList.isEmpty()) {
            Toast.makeText(this, "无明细可删除", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<BindAreaItem> toDeleteItems = new ArrayList<>();
        for (BindAreaItem item : itemList) {
            // 修改条件：只要板标不为空就可以删除
            if (item.板标 != null && !item.板标.isEmpty()) {
                toDeleteItems.add(item);
            }
        }

        if (toDeleteItems.isEmpty()) {
            Toast.makeText(this, "没有板标不为空的明细", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("批量解除绑定")
                .setMessage("确认删除" + toDeleteItems.size() + "条板标不为空的明细？")
                .setPositiveButton("确认", (dialog, which) -> {
                    final int[] deleteCount = {0};
                    final int total = toDeleteItems.size();

                    for (BindAreaItem item : toDeleteItems) {
                        deleteItem(item, () -> {
                            deleteCount[0]++;
                            if (deleteCount[0] == total) {
                                playSound(unbindSuccessSound);
                                Toast.makeText(BindAreaActivity.this,
                                        "已删除" + deleteCount[0] + "条明细",
                                        Toast.LENGTH_SHORT).show();

                                // 先刷新明细界面
                                loadItemsByPlateWithCallback(() -> {
                                    // 刷新完成后清空输入框
                                    etPlate.setText("");
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showSingleDeleteConfirm(BindAreaItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除明细")
                .setMessage("是否删除商品货号：" + item.商品货号 + "\n板标：" + item.板标)
                .setPositiveButton("确认", (dialog, which) -> {
                    deleteItem(item, () -> {
                        playSound(unbindSuccessSound);
                        Toast.makeText(BindAreaActivity.this, "删除成功", Toast.LENGTH_SHORT).show();

                        // 先刷新明细界面
                        loadItemsByPlateWithCallback(() -> {
                            // 刷新完成后清空输入框
                            etPlate.setText("");
                        });
                    });
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void deleteItem(BindAreaItem item, Runnable onSuccess) {
        apiClient.deleteReceivingItem(String.valueOf(item.id), new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            onSuccess.run();
                        } else {
                            Toast.makeText(BindAreaActivity.this,
                                    "删除失败：" + response.getString("message"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(BindAreaActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(BindAreaActivity.this,
                        "删除失败：" + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    public static class BindAreaItem {
        public int id;
        public String 商品货号;
        public String 板标;
        public String 区域;
        public String 创建时间;

        public BindAreaItem(int id, String 商品货号, String 板标, String 区域, String 创建时间) {
            this.id = id;
            this.商品货号 = 商品货号;
            this.板标 = 板标;
            this.区域 = 区域;
            this.创建时间 = 创建时间;
        }
    }

    public class BindAreaAdapter extends android.widget.BaseAdapter {
        private android.content.Context context;
        private List<BindAreaItem> items;

        private final SimpleDateFormat inputDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH);
        private final SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

        public BindAreaAdapter(android.content.Context context, List<BindAreaItem> items) {
            this.context = context;
            this.items = items;
        }

        class ViewHolder {
            TextView tvProduct;
            TextView tvPlate;
            TextView tvArea;
            TextView tvTime;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = View.inflate(context, R.layout.list_item_location, null);
                holder = new ViewHolder();
                holder.tvProduct = convertView.findViewById(R.id.tvProduct);
                holder.tvPlate = convertView.findViewById(R.id.tvPlate);
                holder.tvArea = convertView.findViewById(R.id.tvArea);
                holder.tvTime = convertView.findViewById(R.id.tvTime);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            BindAreaItem item = items.get(position);
            holder.tvProduct.setText("商品：" + item.商品货号);
            holder.tvPlate.setText("板标：" + item.板标);
            holder.tvArea.setText("柜号：" + (item.区域.isEmpty() ? "未绑定" : item.区域));
            holder.tvTime.setText("日期：" + formatCreateDate(item.创建时间));

            return convertView;
        }

        private String formatCreateDate(String originalDate) {
            if (originalDate == null || originalDate.isEmpty()) {
                return "未记录";
            }

            try {
                Date date = inputDateFormat.parse(originalDate);
                if (date == null) {
                    return originalDate;
                }

                String formattedDate = outputDateFormat.format(date);
                return formattedDate.replaceAll("/0", "/").replaceFirst("^0", "");

            } catch (ParseException e) {
                Log.e("BindAreaDateError", "日期解析失败，原始值：" + originalDate, e);
                return originalDate;
            }
        }
    }
}