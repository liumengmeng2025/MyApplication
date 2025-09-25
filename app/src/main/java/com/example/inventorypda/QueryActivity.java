package com.example.inventorypda;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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

public class QueryActivity extends AppCompatActivity {

    private EditText etBarcode;
    private TableLayout tableLayout;
    private List<Product> currentProducts;
    private ApiClient apiClient;
    private MediaPlayer mediaPlayer;
    private MediaPlayer notExistMediaPlayer;

    // 防抖查询相关
    private Handler debounceHandler;
    private static final int DEBOUNCE_DELAY = 1000; // 1秒防抖
    private Runnable queryRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_query);

        // 初始化UI组件
        etBarcode = findViewById(R.id.etBarcode);
        tableLayout = findViewById(R.id.tableLayout);

        // 初始化ApiClient
        apiClient = ApiClient.getInstance(this);

        // 初始化MediaPlayer - 删除成功声音
        mediaPlayer = MediaPlayer.create(this, R.raw.delete_success_sound);

        // 初始化MediaPlayer - 商品不存在声音
        notExistMediaPlayer = MediaPlayer.create(this, R.raw.not_exist_sound);

        // 初始化防抖Handler
        debounceHandler = new Handler();
        queryRunnable = new Runnable() {
            @Override
            public void run() {
                queryProduct();
            }
        };

        // 设置输入框监听
        setupEditTextListeners();

        // 查询按钮事件
        Button btnQuery = findViewById(R.id.btnQuery);
        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 移除之前的延迟查询
                debounceHandler.removeCallbacks(queryRunnable);
                queryProduct();
            }
        });

        // 删除按钮事件
        Button btnDelete = findViewById(R.id.btnDelete);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteProduct();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放MediaPlayer资源
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (notExistMediaPlayer != null) {
            notExistMediaPlayer.release();
            notExistMediaPlayer = null;
        }

        // 移除所有回调
        if (debounceHandler != null) {
            debounceHandler.removeCallbacksAndMessages(null);
        }
    }

    // 设置输入框监听
    private void setupEditTextListeners() {
        // 文本变化监听 - 防抖查询
        etBarcode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 移除之前的查询任务
                debounceHandler.removeCallbacks(queryRunnable);
                // 如果输入不为空，延迟1秒执行查询
                if (s.length() > 0) {
                    debounceHandler.postDelayed(queryRunnable, DEBOUNCE_DELAY);
                } else {
                    // 输入为空时清空表格
                    clearTable();
                }
            }
        });

        // 回车键监听
        etBarcode.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    // 立即执行查询
                    debounceHandler.removeCallbacks(queryRunnable);
                    queryProduct();
                    return true;
                }
                return false;
            }
        });

        // 设置软键盘回车键动作
        etBarcode.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_GO) {
                    // 立即执行查询
                    debounceHandler.removeCallbacks(queryRunnable);
                    queryProduct();
                    return true;
                }
                return false;
            }
        });
    }

    // 查询商品数据
    private void queryProduct() {
        String barcode = etBarcode.getText().toString().trim();
        if (barcode.isEmpty()) {
            Toast.makeText(this, "请输入货号", Toast.LENGTH_SHORT).show();
            return;
        }

        apiClient.queryProduct(barcode, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    // 处理成功响应
                    if (response.getBoolean("success")) {
                        JSONArray data = response.getJSONArray("data");
                        Gson gson = new Gson();
                        currentProducts = gson.fromJson(data.toString(), new TypeToken<List<Product>>(){}.getType());

                        Log.d("InventoryPDA", "Parsed products: " + (currentProducts != null ? currentProducts.size() : 0));

                        // 在主线程更新UI
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (currentProducts != null && !currentProducts.isEmpty()) {
                                    updateTable();
                                    Toast.makeText(QueryActivity.this, "找到 " + currentProducts.size() + " 条记录", Toast.LENGTH_SHORT).show();
                                } else {
                                    clearTable();
                                    Toast.makeText(QueryActivity.this, "无匹配数据", Toast.LENGTH_SHORT).show();
                                    // 商品不存在时播放MP3语音
                                    playNotExistSound();
                                }

                                // 查询完成后焦点回到输入框并全选内容
                                etBarcode.requestFocus();
                                etBarcode.selectAll(); // 新增：全选输入框内容
                            }
                        });
                    } else {
                        String error = response.optString("message", "查询失败");
                        final String finalError = error;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(QueryActivity.this, finalError, Toast.LENGTH_SHORT).show();
                                // 查询完成后焦点回到输入框并全选内容
                                etBarcode.requestFocus();
                                etBarcode.selectAll(); // 新增：全选输入框内容
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e("InventoryPDA", "JSON parsing error: " + e.getMessage());
                    clearTable();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(QueryActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                            // 查询完成后焦点回到输入框并全选内容
                            etBarcode.requestFocus();
                            etBarcode.selectAll(); // 新增：全选输入框内容
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                String errorMessage = (error != null) ? error : "发生未知错误";
                Log.e("InventoryPDA", errorMessage);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(QueryActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        // 查询完成后焦点回到输入框并全选内容
                        etBarcode.requestFocus();
                        etBarcode.selectAll(); // 新增：全选输入框内容
                    }
                });
            }
        });
    }

    // 删除商品记录
    private void deleteProduct() {
        if (currentProducts == null || currentProducts.isEmpty()) {
            Toast.makeText(this, "无数据可删除", Toast.LENGTH_SHORT).show();
            return;
        }

        String productId = currentProducts.get(0).商品货号;
        apiClient.deleteProduct(productId, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (response.getBoolean("success")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // 播放成功声音
                                playSuccessSound();

                                Toast.makeText(QueryActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                clearTable();

                                // 清空输入框内容，进入待输入状态
                                etBarcode.setText("");
                                etBarcode.requestFocus(); // 自动获取焦点，方便继续输入
                            }
                        });
                    } else {
                        String error = response.optString("message", "删除失败");
                        final String finalError = error;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(QueryActivity.this, finalError, Toast.LENGTH_SHORT).show();
                                etBarcode.requestFocus();
                            }
                        });
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(QueryActivity.this, "响应格式错误", Toast.LENGTH_SHORT).show();
                            etBarcode.requestFocus();
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                final String errorMessage = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(QueryActivity.this, "删除失败: " + errorMessage, Toast.LENGTH_LONG).show();
                        etBarcode.requestFocus();
                    }
                });
            }
        });
    }

    // 播放删除成功声音
    private void playSuccessSound() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.seekTo(0);
                mediaPlayer.start();
            } catch (Exception e) {
                Log.e("InventoryPDA", "播放删除成功声音失败: " + e.getMessage());
            }
        }
    }

    // 播放商品不存在声音
    private void playNotExistSound() {
        if (notExistMediaPlayer != null) {
            try {
                if (notExistMediaPlayer.isPlaying()) {
                    notExistMediaPlayer.stop();
                }
                notExistMediaPlayer.seekTo(0);
                notExistMediaPlayer.start();
            } catch (Exception e) {
                Log.e("InventoryPDA", "播放商品不存在声音失败: " + e.getMessage());
            }
        }
    }

    // 更新表格数据
    private void updateTable() {
        // 清除旧数据（保留表头）
        int childCount = tableLayout.getChildCount();
        if (childCount > 1) {
            tableLayout.removeViews(1, childCount - 1);
        }

        // 填充新数据
        if (currentProducts != null) {
            for (Product product : currentProducts) {
                TableRow row = new TableRow(this);

                TextView tv1 = createTableCell(product.商品货号);
                TextView tv2 = createTableCell(String.valueOf(product.要货数量));
                TextView tv3 = createTableCell(product.包数 != null ? product.包数 : "");
                TextView tv4 = createTableCell(product.库位);

                row.addView(tv1);
                row.addView(tv2);
                row.addView(tv3);
                row.addView(tv4);

                tableLayout.addView(row);
            }
        }
    }

    // 创建表格单元格
    private TextView createTableCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 16, 16, 16);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    // 清空表格
    private void clearTable() {
        int childCount = tableLayout.getChildCount();
        if (childCount > 1) {
            tableLayout.removeViews(1, childCount - 1);
        }
        currentProducts = null;
    }

    // 产品数据模型 - 使用中文字段名与服务器返回的JSON匹配
    static class Product {
        @SerializedName("商品货号")
        public String 商品货号;

        @SerializedName("要货数量")
        public int 要货数量;

        @SerializedName("包数")
        public String 包数;

        @SerializedName("库位")
        public String 库位;
    }
}