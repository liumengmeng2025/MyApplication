package com.example.inventorypda;
//分货明细界面
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class DistributionActivity extends AppCompatActivity {

    private EditText etBarcode;
    private TableLayout tableLayout;
    private TextView tvLoading;
    private List<DistributionItem> currentItems;
    private ApiClient apiClient;
    private MediaPlayer deleteSuccessSound; // 添加MediaPlayer用于播放声音

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distribution);

        // 初始化UI组件
        etBarcode = findViewById(R.id.etBarcode);
        tableLayout = findViewById(R.id.tableLayout);
        tvLoading = findViewById(R.id.tvLoading);

        // 初始化ApiClient
        apiClient = ApiClient.getInstance(this);

        // 初始化删除成功音效
        initializeDeleteSound();

        // 查询按钮事件
        Button btnQuery = findViewById(R.id.btnQuery);
        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queryDistribution();
            }
        });

        // 删除按钮事件
        Button btnDelete = findViewById(R.id.btnDelete);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteDistribution();
            }
        });
    }

    // 初始化删除成功音效
    private void initializeDeleteSound() {
        try {
            // 从raw资源文件夹加载delete_success_sound音频文件
            deleteSuccessSound = MediaPlayer.create(this, R.raw.delete_success_sound);
            if (deleteSuccessSound != null) {
                deleteSuccessSound.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        // 播放完成后释放资源
                        mp.release();
                    }
                });
            }
        } catch (Exception e) {
            Log.e("InventoryPDA", "初始化删除音效失败: " + e.getMessage());
        }
    }

    // 播放删除成功音效
    private void playDeleteSuccessSound() {
        try {
            if (deleteSuccessSound != null) {
                if (deleteSuccessSound.isPlaying()) {
                    deleteSuccessSound.seekTo(0); // 如果正在播放，重置到开始位置
                } else {
                    // 重新创建MediaPlayer实例，因为之前的可能已经被释放
                    deleteSuccessSound = MediaPlayer.create(this, R.raw.delete_success_sound);
                    deleteSuccessSound.start();
                }
            } else {
                // 如果MediaPlayer为null，重新初始化
                initializeDeleteSound();
                if (deleteSuccessSound != null) {
                    deleteSuccessSound.start();
                }
            }
        } catch (Exception e) {
            Log.e("InventoryPDA", "播放删除音效失败: " + e.getMessage());
        }
    }

    // 查询分货数据（核心修改：修复ApiResponseListener的onSuccess参数类型）
    private void queryDistribution() {
        String barcode = etBarcode.getText().toString().trim();
        if (barcode.isEmpty()) {
            Toast.makeText(this, "请输入商品货号", Toast.LENGTH_SHORT).show();
            return;
        }

        tvLoading.setVisibility(View.VISIBLE);

        // 关键修改：onSuccess参数改为 JSONObject，与ApiResponseListener接口匹配
        apiClient.queryDistribution(barcode, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) { // 此处从JSONArray改为JSONObject
                tvLoading.setVisibility(View.GONE);

                try {
                    // 1. 先判断接口响应是否成功（按后端统一格式：success字段）
                    if (response.getBoolean("success")) {
                        // 2. 从JSONObject中提取data字段的JSONArray（后端返回的列表数据）
                        JSONArray dataArray = response.getJSONArray("data");

                        // 3. Gson解析JSONArray为List<DistributionItem>
                        Gson gson = new Gson();
                        currentItems = gson.fromJson(
                                dataArray.toString(),
                                new TypeToken<List<DistributionItem>>(){}.getType()
                        );

                        Log.d("InventoryPDA", "Distribution items: " + (currentItems != null ? currentItems.size() : 0));

                        // 主线程更新UI
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (currentItems != null && !currentItems.isEmpty()) {
                                    updateTable(currentItems);
                                    Toast.makeText(DistributionActivity.this,
                                            "找到 " + currentItems.size() + " 条分货记录",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    clearTable();
                                    Toast.makeText(DistributionActivity.this, "无分货数据", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } else {
                        // 接口返回success=false时，提取错误信息
                        String errorMsg = response.optString("message", "查询分货数据失败");
                        runOnUiThread(() -> Toast.makeText(DistributionActivity.this, errorMsg, Toast.LENGTH_SHORT).show());
                    }
                } catch (JSONException e) {
                    // JSON解析异常（如字段缺失、格式错误）
                    Log.e("InventoryPDA", "JSON parsing error: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(DistributionActivity.this,
                            "数据解析错误: " + e.getMessage(),
                            Toast.LENGTH_LONG).show());
                    clearTable();
                } catch (Exception e) {
                    // 其他未知异常
                    Log.e("InventoryPDA", "Unknown error: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(DistributionActivity.this,
                            "查询失败: 未知错误",
                            Toast.LENGTH_LONG).show());
                    clearTable();
                }
            }

            @Override
            public void onError(String error) {
                // 网络错误（如连接超时、服务器不可达）
                tvLoading.setVisibility(View.GONE);
                String errorMessage = (error != null) ? error : "网络请求失败";
                Log.e("InventoryPDA", errorMessage);
                runOnUiThread(() -> Toast.makeText(DistributionActivity.this, errorMessage, Toast.LENGTH_LONG).show());
            }
        });
    }

    // 删除分货数据（无需修改，已正确使用JSONObject）
    private void deleteDistribution() {
        String barcode = etBarcode.getText().toString().trim();
        if (barcode.isEmpty()) {
            Toast.makeText(this, "请输入商品货号", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentItems == null || currentItems.isEmpty()) {
            Toast.makeText(this, "无数据可删除", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示确认对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除");
        builder.setMessage("确定要删除货号为 " + barcode + " 的所有分货记录吗？");

        builder.setPositiveButton("确认", (dialog, which) -> performDeleteDistribution(barcode));
        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // 执行删除操作（添加语音提示）
    private void performDeleteDistribution(String barcode) {
        apiClient.deleteDistribution(barcode, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (response.getBoolean("success")) {
                        // 显示Toast提示
                        Toast.makeText(DistributionActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                        // 播放删除成功音效
                        playDeleteSuccessSound();
                        clearTable();
                        currentItems = null;
                    } else if (response.has("error")) {
                        Toast.makeText(DistributionActivity.this, "删除失败: " + response.getString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(DistributionActivity.this, "删除失败: 未知错误", Toast.LENGTH_LONG).show();
                    }
                } catch (JSONException e) {
                    Log.e("InventoryPDA", "JSON parsing error: " + e.getMessage());
                    Toast.makeText(DistributionActivity.this, "删除失败: 响应格式错误", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(DistributionActivity.this, "删除失败: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // 更新表格数据（无需修改）
    private void updateTable(List<DistributionItem> items) {
        // 清除旧数据（保留表头）
        int childCount = tableLayout.getChildCount();
        if (childCount > 1) {
            tableLayout.removeViews(1, childCount - 1);
        }

        // 填充新数据
        for (int i = 0; i < items.size(); i++) {
            DistributionItem item = items.get(i);
            TableRow row = new TableRow(this);

            TextView tv1 = createTableCell(item.序号 != null ? item.序号 : "");  // 序号作为文本显示
            TextView tv2 = createTableCell(String.valueOf(item.捡包数));         // 捡包数作为数字显示

            // 为序号单元格添加长按监听器（删除单个记录）
            final int position = i;
            tv1.setOnLongClickListener(v -> {
                showDeleteSingleDialog(item, position);
                return true;
            });

            row.addView(tv1);
            row.addView(tv2);

            tableLayout.addView(row);
        }
    }

    // 显示删除单个记录的对话框（无需修改）
    private void showDeleteSingleDialog(DistributionItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除");
        builder.setMessage("确定要删除序号为 " + item.序号 + " 的分货记录吗？");

        builder.setPositiveButton("确认", (dialog, which) -> deleteSingleDistribution(item, position));
        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // 删除单个分货记录（添加语音提示）
    private void deleteSingleDistribution(DistributionItem item, int position) {
        String barcode = etBarcode.getText().toString().trim();
        if (barcode.isEmpty()) {
            Toast.makeText(this, "当前商品货号为空", Toast.LENGTH_SHORT).show();
            return;
        }

        apiClient.deleteSingleDistribution(barcode, item.序号, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (response.getBoolean("success")) {
                        // 显示Toast提示
                        Toast.makeText(DistributionActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                        // 播放删除成功音效
                        playDeleteSuccessSound();
                        // 从当前列表中移除该项并刷新表格
                        if (currentItems != null && position < currentItems.size()) {
                            currentItems.remove(position);
                            updateTable(currentItems);
                        }
                    } else if (response.has("error")) {
                        Toast.makeText(DistributionActivity.this, "删除失败: " + response.getString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(DistributionActivity.this, "删除失败: 未知错误", Toast.LENGTH_LONG).show();
                    }
                } catch (JSONException e) {
                    Log.e("InventoryPDA", "JSON parsing error: " + e.getMessage());
                    Toast.makeText(DistributionActivity.this, "删除失败: 响应格式错误", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(DistributionActivity.this, "删除失败: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // 创建表格单元格（无需修改）
    private TextView createTableCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(12, 12, 12, 12);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(18);
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    // 清空表格（无需修改）
    private void clearTable() {
        int childCount = tableLayout.getChildCount();
        if (childCount > 1) {
            tableLayout.removeViews(1, childCount - 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放MediaPlayer资源
        if (deleteSuccessSound != null) {
            if (deleteSuccessSound.isPlaying()) {
                deleteSuccessSound.stop();
            }
            deleteSuccessSound.release();
            deleteSuccessSound = null;
        }
    }

    // 分货数据模型（无需修改）
    static class DistributionItem {
        @SerializedName("序号")
        public String 序号;  // 文本格式（匹配后端返回）

        @SerializedName("捡包数")
        public int 捡包数;   // 数字格式（匹配后端返回）
    }
}