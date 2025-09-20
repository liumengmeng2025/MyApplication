package com.example.inventorypda;

import android.content.Intent; // 添加这行导入
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.util.Log;
import android.media.MediaPlayer;
import java.util.ArrayList;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QueryLocationActivity extends AppCompatActivity {

    // UI组件声明
    private EditText etSearch;
    private Button btnByProduct, btnByPlate, btnUnbind, btnCabinetQuery; // 添加btnCabinetQuery
    private ListView lvItems;

    // 数据列表和适配器
    private List<LocationItem> itemList;
    private LocationAdapter adapter;

    // 声音播放器
    private MediaPlayer unbindSuccessSound;

    // 当前查询类型和值
    private String currentSearchType = "";
    private String currentSearchValue = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_query_location);

        // 初始化声音资源
        initSounds();

        // 初始化UI组件
        initViews();
        // 初始化数据和适配器
        initData();
        // 设置按钮点击事件
        setupButtonListeners();
        // 修复：添加列表事件绑定（之前遗漏调用）
        setupListListeners();
    }

    // 初始化声音资源
    private void initSounds() {
        try {
            unbindSuccessSound = MediaPlayer.create(this, R.raw.unbind_success_sound);
        } catch (Exception e) {
            Log.e("SoundError", "无法加载声音文件", e);
        }
    }

    // 播放声音的通用方法
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

    // 初始化UI组件
    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        btnByProduct = findViewById(R.id.btnByProduct);
        btnByPlate = findViewById(R.id.btnByPlate);
        btnUnbind = findViewById(R.id.btnUnbind);
        btnCabinetQuery = findViewById(R.id.btnCabinetQuery); // 初始化新按钮
        lvItems = findViewById(R.id.lvItems);
    }

    // 初始化数据
    private void initData() {
        itemList = new ArrayList<>();
        adapter = new LocationAdapter(this, itemList);
        lvItems.setAdapter(adapter);
    }

    // 按钮点击事件（查询、批量解绑）
    private void setupButtonListeners() {
        // 按商品货号查询
        btnByProduct.setOnClickListener(v -> searchByProduct());
        // 按板标查询
        btnByPlate.setOnClickListener(v -> searchByPlate());
        // 批量解除绑定
        btnUnbind.setOnClickListener(v -> deleteAllItems());
        // 装柜数据查询
        btnCabinetQuery.setOnClickListener(v -> {
            Intent intent = new Intent(QueryLocationActivity.this, CabinetWithdrawActivity.class);
            startActivity(intent);
        });
    }

    // 修复：仅保留1个列表事件绑定方法（含长按弹窗逻辑）
    private void setupListListeners() {
        // 列表项点击（选中项高亮）
        lvItems.setOnItemClickListener((parent, view, position, id) -> {
            adapter.setSelectedPosition(position);
        });

        // 列表项长按（弹出确认对话框）
        lvItems.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                LocationItem selectedItem = itemList.get(position);
                showDeleteConfirmDialog(selectedItem); // 弹确认框
                return true; // 消费事件，不触发点击
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放MediaPlayer资源
        if (unbindSuccessSound != null) {
            unbindSuccessSound.release();
        }
    }

    // 按商品货号查询
    private void searchByProduct() {
        String product = etSearch.getText().toString().trim();
        if (product.isEmpty()) {
            Toast.makeText(this, "请输入商品货号", Toast.LENGTH_SHORT).show();
            return;
        }
        currentSearchType = "product";
        currentSearchValue = product;
        search("product", product);
    }

    // 按板标查询
    private void searchByPlate() {
        String plate = etSearch.getText().toString().trim();
        if (plate.isEmpty()) {
            Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
            return;
        }
        currentSearchType = "plate";
        currentSearchValue = plate;
        search("plate", plate);
    }

    // 执行查询
    private void search(String type, String value) {
        ApiClient.getInstance(this).getRequest("/receiving/query?type=" + type + "&value=" + value,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            JSONArray data = response.getJSONArray("data");
                            itemList.clear();

                            // 解析数据
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject item = data.getJSONObject(i);
                                itemList.add(new LocationItem(
                                        item.getInt("id"),
                                        item.getString("商品货号"),
                                        item.getString("板标"),
                                        item.optString("区域", "未绑定"),
                                        item.optString("创建时间", ""),
                                        item.optString("更新时间", "")
                                ));
                            }

                            adapter.notifyDataSetChanged();

                            // 提示查询结果
                            if (itemList.isEmpty()) {
                                Toast.makeText(QueryLocationActivity.this, "没有找到匹配的记录", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(QueryLocationActivity.this, "找到" + itemList.size() + "条记录", Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(QueryLocationActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        String errorMessage = (error != null) ? error : "发生未知错误";
                        Toast.makeText(QueryLocationActivity.this, "查询失败: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 批量删除（自动存deletelog表）
    private void deleteAllItems() {
        if (itemList.isEmpty()) {
            Toast.makeText(this, "没有记录可删除", Toast.LENGTH_SHORT).show();
            return;
        }

        final int[] deleteCount = {0};
        final int total = itemList.size();
        for (LocationItem item : itemList) {
            Map<String, String> params = new HashMap<>();
            params.put("id", String.valueOf(item.id));

            ApiClient.getInstance(this).postRequest("/receiving/delete", params,
                    new ApiClient.ApiResponseListener() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            deleteCount[0]++;
                            if (deleteCount[0] == total) {
                                itemList.clear();
                                adapter.notifyDataSetChanged();
                                // 播放解除绑定语音
                                playSound(unbindSuccessSound);
                                Toast.makeText(QueryLocationActivity.this, "所有记录已删除", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(QueryLocationActivity.this, "部分记录删除失败: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    // 长按删除确认对话框
    private void showDeleteConfirmDialog(LocationItem item) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage(String.format(
                        "是否删除以下记录？\n商品货号：%s\n板标：%s\n区域：%s",
                        item.商品货号, item.板标, item.区域
                ))
                .setPositiveButton("是", (dialog, which) -> {
                    deleteSingleItem(item); // 执行删除
                    dialog.dismiss();
                })
                .setNegativeButton("否", (dialog, which) -> {
                    dialog.dismiss(); // 仅关闭对话框
                })
                .setCancelable(false) // 禁止外部点击关闭
                .show();
    }

    // 单条删除（自动存deletelog表）
    private void deleteSingleItem(LocationItem item) {
        Map<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(item.id));

        ApiClient.getInstance(this).postRequest("/receiving/delete", params,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        itemList.remove(item);
                        adapter.notifyDataSetChanged();

                        // 播放解除绑定语音
                        playSound(unbindSuccessSound);

                        // 提示结果
                        if (itemList.isEmpty()) {
                            Toast.makeText(QueryLocationActivity.this, "记录已删除，列表为空", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(QueryLocationActivity.this, "记录已删除", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(QueryLocationActivity.this, "删除失败: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 数据模型：库位查询项
    public static class LocationItem {
        public int id;
        public String 商品货号;
        public String 板标;
        public String 区域;
        public String 创建时间;
        public String 更新时间;

        public LocationItem(int id, String 商品货号, String 板标, String 区域, String 创建时间, String 更新时间) {
            this.id = id;
            this.商品货号 = 商品货号;
            this.板标 = 板标;
            this.区域 = 区域;
            this.创建时间 = 创建时间;
            this.更新时间 = 更新时间;
        }
    }

    // 自定义适配器：明细带前缀显示 + 日期格式优化
    public class LocationAdapter extends android.widget.BaseAdapter {
        private android.content.Context context;
        private List<LocationItem> items;
        private int selectedPosition = -1; // 选中项高亮

        // 日期解析器（处理 RFC 2822 格式，如 Fri,29 Aug 2025）
        private final SimpleDateFormat inputDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH);
        // 日期格式化器（目标格式：2025/MM/dd）
        private final SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

        public LocationAdapter(android.content.Context context, List<LocationItem> items) {
            this.context = context;
            this.items = items;
        }

        // 设置选中项（用于列表点击选择）
        public void setSelectedPosition(int position) {
            selectedPosition = position;
            notifyDataSetChanged();
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

        // ViewHolder：优化布局复用
        class ViewHolder {
            TextView tvLine1; // 第一行：商品+板标
            TextView tvLine2; // 第二行：区域+创建时间
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            // 复用布局
            if (convertView == null) {
                convertView = View.inflate(context, android.R.layout.simple_list_item_2, null);
                holder = new ViewHolder();
                holder.tvLine1 = convertView.findViewById(android.R.id.text1);
                holder.tvLine2 = convertView.findViewById(android.R.id.text2);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            // 填充数据（带前缀 + 日期优化）
            LocationItem item = items.get(position);
            // 第一行：商品+板标（带前缀，不变）
            holder.tvLine1.setText(String.format("商品：%s | 板标：%s", item.商品货号, item.板标));
            // 第二行：区域+格式化后的日期
            holder.tvLine2.setText(String.format(
                    "区域：%s | 创建时间：%s",
                    item.区域,
                    formatCreateDate(item.创建时间) // 调用日期格式化方法
            ));

            // 选中项高亮（不变）
            if (position == selectedPosition) {
                convertView.setBackgroundColor(context.getResources().getColor(android.R.color.darker_gray));
            } else {
                convertView.setBackgroundColor(context.getResources().getColor(android.R.color.white));
            }

            return convertView;
        }

        // 核心：将 Fri,29 Aug 2025 转为 2025/8/29（自动去前导零）
        private String formatCreateDate(String originalDate) {
            // 1. 处理空值
            if (originalDate == null || originalDate.isEmpty()) {
                return "未记录";
            }

            try {
                // 2. 解析 RFC 2822 格式的日期（如 Fri,29 Aug 2025）
                Date date = inputDateFormat.parse(originalDate);
                if (date == null) {
                    return originalDate;
                }

                // 3. 格式化为 2025/MM/dd（如 2025/08/29）
                String formattedDate = outputDateFormat.format(date);

                // 4. 去除月份/日期的前导零（如 2025/08/09 → 2025/8/9）
                return formattedDate.replaceAll("/0", "/").replaceFirst("^0", "");

            } catch (ParseException e) {
                // 5. 解析失败（如日期格式异常），返回原始值并打印日志
                Log.e("DateFormatError", "日期解析失败，原始值：" + originalDate, e);
                return originalDate;
            }
        }
    }
}