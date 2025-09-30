package com.example.inventorypda;

import android.content.Intent;
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

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CabinetOperationActivity extends AppCompatActivity {

    // UI组件声明
    private EditText etSearch, etBillNumber;
    private Button btnByProduct, btnByPlate, btnUnbind, btnCabinetQuery, btnCabinetScan, btnFixBillNumber;
    private ListView lvItems;
    private TextView tvCurrentBillNumber;

    // 数据列表和适配器
    private List<CabinetItem> itemList;
    private CabinetAdapter adapter;

    // 声音播放器
    private MediaPlayer unbindSuccessSound;
    private MediaPlayer fixBillSuccessSound;
    private MediaPlayer billNotExistSound;

    // 当前查询类型和值
    private String currentSearchType = "";
    private String currentSearchValue = "";
    private String fixedBillNumber = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cabinet_operation);

        // 初始化声音资源
        initSounds();

        // 初始化UI组件
        initViews();
        // 初始化数据和适配器
        initData();
        // 设置按钮点击事件
        setupButtonListeners();
        // 添加列表事件绑定
        setupListListeners();
    }

    // 初始化声音资源
    private void initSounds() {
        try {
            unbindSuccessSound = MediaPlayer.create(this, R.raw.unbind_success_sound);
            fixBillSuccessSound = MediaPlayer.create(this, R.raw.success_sound2); // 固定成功声音
            billNotExistSound = MediaPlayer.create(this, R.raw.not_exist_sound); // 提单号不存在声音
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
        etBillNumber = findViewById(R.id.etBillNumber);
        btnByProduct = findViewById(R.id.btnByProduct);
        btnByPlate = findViewById(R.id.btnByPlate);
        btnUnbind = findViewById(R.id.btnUnbind);
        btnCabinetQuery = findViewById(R.id.btnCabinetQuery);
        btnCabinetScan = findViewById(R.id.btnCabinetScan);
        btnFixBillNumber = findViewById(R.id.btnFixBillNumber);
        lvItems = findViewById(R.id.lvItems);
        tvCurrentBillNumber = findViewById(R.id.tvCurrentBillNumber);
    }

    // 初始化数据
    private void initData() {
        itemList = new ArrayList<>();
        adapter = new CabinetAdapter(this, itemList);
        lvItems.setAdapter(adapter);

        // 初始化当前提单号显示
        updateBillNumberDisplay();
    }

    // 更新提单号显示和按钮状态
    private void updateBillNumberDisplay() {
        if (fixedBillNumber.isEmpty()) {
            tvCurrentBillNumber.setText("当前提单号：未固定");
            tvCurrentBillNumber.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            // 未固定时，按钮可点击
            btnFixBillNumber.setEnabled(true);
            btnFixBillNumber.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
        } else {
            tvCurrentBillNumber.setText("当前提单号：" + fixedBillNumber);
            tvCurrentBillNumber.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            // 已固定时，按钮不可点击
            btnFixBillNumber.setEnabled(false);
            btnFixBillNumber.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
            // 在输入框中显示当前提单号
            etBillNumber.setText(fixedBillNumber);
        }
    }

    // 按钮点击事件
    private void setupButtonListeners() {
        // 固定提单号
        btnFixBillNumber.setOnClickListener(v -> fixBillNumber());
        // 按商品货号查询
        btnByProduct.setOnClickListener(v -> searchByProduct());
        // 按板标查询
        btnByPlate.setOnClickListener(v -> searchByPlate());
        // 批量解除绑定
        btnUnbind.setOnClickListener(v -> performBatchDelete());
        // 装柜数据查询
        btnCabinetQuery.setOnClickListener(v -> {
            Intent intent = new Intent(CabinetOperationActivity.this, CabinetWithdrawActivity.class);
            startActivity(intent);
        });
        // 装柜扫描
        btnCabinetScan.setOnClickListener(v -> {
            Intent intent = new Intent(CabinetOperationActivity.this, CabinetFlowActivity.class);
            startActivity(intent);
        });
    }

    // 列表事件绑定方法
    private void setupListListeners() {
        // 列表项点击（选中项高亮）
        lvItems.setOnItemClickListener((parent, view, position, id) -> {
            adapter.setSelectedPosition(position);
        });

        // 列表项长按（弹出确认对话框）
        lvItems.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                CabinetItem selectedItem = itemList.get(position);
                if (selectedItem.板标 == null || selectedItem.板标.isEmpty()) {
                    Toast.makeText(CabinetOperationActivity.this, "无法删除板标为空的明细", Toast.LENGTH_SHORT).show();
                    return true;
                }
                showDeleteConfirmDialog(selectedItem);
                return true;
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
        if (fixBillSuccessSound != null) {
            fixBillSuccessSound.release();
        }
        if (billNotExistSound != null) {
            billNotExistSound.release();
        }
    }

    // 固定提单号 - 修改后的版本
    private void fixBillNumber() {
        String billNumber = etBillNumber.getText().toString().trim();
        if (billNumber.isEmpty()) {
            Toast.makeText(this, "请输入提单号", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查提单号是否存在
        checkBillNumberExists(billNumber);
    }

    // 检查提单号是否存在
    private void checkBillNumberExists(String billNumber) {
        String url = "/cabinet/check_bill?bill_number=" + billNumber;

        Log.d("CheckBill", "检查提单号是否存在: " + billNumber);

        ApiClient.getInstance(this).getRequest(url,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            Log.d("CheckBill", "收到提单号检查响应: " + response.toString());

                            boolean success = response.getBoolean("success");
                            if (success) {
                                JSONObject data = response.getJSONObject("data");
                                boolean exists = data.getBoolean("exists");

                                if (exists) {
                                    // 提单号存在，固定成功
                                    fixedBillNumber = billNumber;
                                    updateBillNumberDisplay();
                                    playSound(fixBillSuccessSound);
                                    Toast.makeText(CabinetOperationActivity.this, "提单号已固定为：" + billNumber, Toast.LENGTH_SHORT).show();
                                } else {
                                    // 提单号不存在
                                    playSound(billNotExistSound);
                                    Toast.makeText(CabinetOperationActivity.this, "提单号不存在：" + billNumber, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                String error = response.optString("error", response.optString("message", "检查提单号失败"));
                                Toast.makeText(CabinetOperationActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Log.e("CheckBill", "提单号检查数据解析错误", e);
                            e.printStackTrace();
                            Toast.makeText(CabinetOperationActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("CheckBill", "检查提单号失败: " + error);
                        String errorMessage = (error != null) ? error : "发生未知错误";
                        Toast.makeText(CabinetOperationActivity.this, "检查提单号失败: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
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
        if (fixedBillNumber.isEmpty()) {
            Toast.makeText(this, "请先固定提单号", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = "/cabinet/query?type=" + type + "&value=" + value + "&bill_number=" + fixedBillNumber;

        Log.d("CabinetSearch", "发送查询请求: " + url);

        ApiClient.getInstance(this).getRequest(url,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            Log.d("CabinetSearch", "收到响应: " + response.toString());

                            boolean success = response.getBoolean("success");
                            if (success) {
                                JSONArray data = response.getJSONArray("data");
                                itemList.clear();

                                // 解析数据
                                for (int i = 0; i < data.length(); i++) {
                                    JSONObject item = data.getJSONObject(i);

                                    Log.d("CabinetSearch", "解析项目 " + i + ": " + item.toString());

                                    itemList.add(new CabinetItem(
                                            item.getInt("id"),
                                            item.getString("商品货号"),
                                            item.getString("板标"),
                                            item.optString("区域", "未绑定"),
                                            item.optString("创建时间", ""),
                                            item.optString("更新时间", ""),
                                            item.optString("提单号", "")
                                    ));
                                }

                                adapter.notifyDataSetChanged();

                                if (itemList.isEmpty()) {
                                    Toast.makeText(CabinetOperationActivity.this, "没有找到匹配的记录", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(CabinetOperationActivity.this, "找到" + itemList.size() + "条记录", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                String error = response.optString("error", response.optString("message", "查询失败"));
                                Toast.makeText(CabinetOperationActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Log.e("CabinetSearch", "数据解析错误", e);
                            e.printStackTrace();
                            Toast.makeText(CabinetOperationActivity.this, "数据解析错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("CabinetSearch", "查询失败: " + error);
                        String errorMessage = (error != null) ? error : "发生未知错误";
                        Toast.makeText(CabinetOperationActivity.this, "查询失败: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 执行批量删除
    private void performBatchDelete() {
        if (itemList.isEmpty()) {
            Toast.makeText(this, "没有记录可删除", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否有板标为空的记录
        for (CabinetItem item : itemList) {
            if (item.板标 == null || item.板标.isEmpty()) {
                Toast.makeText(this, "存在板标为空的记录，无法批量删除", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        showBatchDeleteConfirmDialog();
    }

    // 显示批量删除确认对话框
    private void showBatchDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("确认批量解绑")
                .setMessage("确定要解绑选中的 " + itemList.size() + " 条记录吗？")
                .setPositiveButton("确定", (dialogInterface, which) -> {
                    executeBatchDelete();
                    dialogInterface.dismiss();
                })
                .setNegativeButton("取消", (dialogInterface, which) -> {
                    Toast.makeText(CabinetOperationActivity.this, "已取消解绑", Toast.LENGTH_SHORT).show();
                    dialogInterface.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    // 执行批量删除
    private void executeBatchDelete() {
        final int total = itemList.size();
        final int[] successCount = {0};
        final int[] failCount = {0};
        final List<CabinetItem> itemsToRemove = new ArrayList<>();

        Log.d("CabinetBatchDelete", "开始批量删除，共" + total + "条记录");

        for (final CabinetItem item : itemList) {
            Map<String, String> params = new HashMap<>();
            params.put("id", String.valueOf(item.id));

            Log.d("CabinetBatchDelete", "发送删除请求: ID=" + item.id + ", 商品=" + item.商品货号 + ", 板标=" + item.板标);

            ApiClient.getInstance(this).postRequest("/receiving/delete", params,
                    new ApiClient.ApiResponseListener() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            Log.d("CabinetBatchDelete", "删除成功: " + item.商品货号 + "-" + item.板标);
                            successCount[0]++;
                            itemsToRemove.add(item);

                            if (successCount[0] + failCount[0] == total) {
                                handleBatchDeleteComplete(successCount[0], failCount[0], itemsToRemove);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            Log.e("CabinetBatchDelete", "删除失败: " + item.商品货号 + "-" + item.板标 + ", 错误: " + error);
                            failCount[0]++;

                            if (successCount[0] + failCount[0] == total) {
                                handleBatchDeleteComplete(successCount[0], failCount[0], itemsToRemove);
                            }
                        }
                    });
        }
    }

    // 处理批量删除完成
    private void handleBatchDeleteComplete(int successCount, int failCount, List<CabinetItem> itemsToRemove) {
        runOnUiThread(() -> {
            // 从列表中移除成功的项目
            for (CabinetItem item : itemsToRemove) {
                itemList.remove(item);
            }
            adapter.notifyDataSetChanged();

            // 播放解绑成功音效 - 只要成功解绑至少一条就播放
            if (successCount > 0) {
                playSound(unbindSuccessSound);
            }

            // 显示结果提示
            String message;
            if (failCount == 0) {
                message = "成功解绑 " + successCount + " 条记录";
            } else {
                message = "成功解绑 " + successCount + " 条记录，" + failCount + " 条记录解绑失败";
            }

            Toast.makeText(CabinetOperationActivity.this, message, Toast.LENGTH_LONG).show();

            if (itemList.isEmpty()) {
                Toast.makeText(CabinetOperationActivity.this, "所有记录已处理完毕", Toast.LENGTH_SHORT).show();
            }

            Log.d("CabinetBatchDelete", "批量删除完成: " + message);
        });
    }

    // 长按删除确认对话框
    private void showDeleteConfirmDialog(CabinetItem item) {
        new AlertDialog.Builder(this)
                .setTitle("确认解绑")
                .setMessage(String.format(
                        "是否解绑以下记录？\n商品货号：%s\n板标：%s\n区域：%s",
                        item.商品货号, item.板标, item.区域
                ))
                .setPositiveButton("是", (dialog, which) -> {
                    deleteSingleItem(item);
                    dialog.dismiss();
                })
                .setNegativeButton("否", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    // 单条删除
    private void deleteSingleItem(CabinetItem item) {
        Map<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(item.id));

        Log.d("CabinetSingleDelete", "发送单条删除请求: ID=" + item.id);

        ApiClient.getInstance(this).postRequest("/receiving/delete", params,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        runOnUiThread(() -> {
                            itemList.remove(item);
                            adapter.notifyDataSetChanged();

                            // 播放解绑成功音效
                            playSound(unbindSuccessSound);

                            if (itemList.isEmpty()) {
                                Toast.makeText(CabinetOperationActivity.this, "记录已解绑，列表为空", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(CabinetOperationActivity.this, "记录已解绑", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(CabinetOperationActivity.this, "解绑失败: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    // 数据模型：装柜操作项
    public static class CabinetItem {
        public int id;
        public String 商品货号;
        public String 板标;
        public String 区域;
        public String 创建时间;
        public String 更新时间;
        public String 提单号;

        public CabinetItem(int id, String 商品货号, String 板标, String 区域, String 创建时间, String 更新时间, String 提单号) {
            this.id = id;
            this.商品货号 = 商品货号;
            this.板标 = 板标;
            this.区域 = 区域;
            this.创建时间 = 创建时间;
            this.更新时间 = 更新时间;
            this.提单号 = 提单号;
        }
    }

    // 自定义适配器
    public class CabinetAdapter extends android.widget.BaseAdapter {
        private android.content.Context context;
        private List<CabinetItem> items;
        private int selectedPosition = -1;

        private final SimpleDateFormat inputDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH);
        private final SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

        public CabinetAdapter(android.content.Context context, List<CabinetItem> items) {
            this.context = context;
            this.items = items;
        }

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

        class ViewHolder {
            TextView tvLine1;
            TextView tvLine2;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = View.inflate(context, android.R.layout.simple_list_item_2, null);
                holder = new ViewHolder();
                holder.tvLine1 = convertView.findViewById(android.R.id.text1);
                holder.tvLine2 = convertView.findViewById(android.R.id.text2);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            CabinetItem item = items.get(position);
            holder.tvLine1.setText(String.format("商品：%s | 板标：%s", item.商品货号, item.板标));

            String billInfo = !isBillNumberEmpty(item.提单号) ?
                    "提单号：" + item.提单号 : "提单号：未绑定";

            holder.tvLine2.setText(String.format(
                    "区域：%s | %s | 创建时间：%s",
                    item.区域,
                    billInfo,
                    formatCreateDate(item.创建时间)
            ));

            if (position == selectedPosition) {
                convertView.setBackgroundColor(context.getResources().getColor(android.R.color.darker_gray));
            } else {
                convertView.setBackgroundColor(context.getResources().getColor(android.R.color.white));
            }

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
                Log.e("DateFormatError", "日期解析失败，原始值：" + originalDate, e);
                return originalDate;
            }
        }

        private boolean isBillNumberEmpty(String billNumber) {
            if (billNumber == null) {
                return true;
            }

            String trimmed = billNumber.trim();
            return trimmed.isEmpty() ||
                    "null".equalsIgnoreCase(trimmed) ||
                    "undefined".equalsIgnoreCase(trimmed) ||
                    "NaN".equalsIgnoreCase(trimmed);
        }
    }
}