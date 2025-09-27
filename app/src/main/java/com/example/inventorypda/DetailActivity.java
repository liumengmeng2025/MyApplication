package com.example.inventorypda;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DetailActivity extends AppCompatActivity {

    private TableLayout tableLayout;
    private TextView tvLoading;
    private List<QueryActivity.Product> currentProducts;
    private List<QueryActivity.Product> allProducts;
    private EditText etPackageSearch;
    private Button btnSearchPackage;
    private Button btnSortByLocation;
    private Button btnSortByQuantity;
    private ApiClient apiClient;
    private MediaPlayer deleteSuccessSound; // 添加MediaPlayer用于播放声音

    // 分页相关变量
    private int currentPage = 0;
    private static final int PAGE_SIZE = 500;
    private Button btnPrevPage;
    private Button btnNextPage;
    private TextView tvPageInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        // 初始化UI组件
        tableLayout = findViewById(R.id.tableLayout);
        tvLoading = findViewById(R.id.tvLoading);
        etPackageSearch = findViewById(R.id.etPackageSearch);
        btnSearchPackage = findViewById(R.id.btnSearchPackage);
        btnSortByLocation = findViewById(R.id.btnSortByLocation);
        btnSortByQuantity = findViewById(R.id.btnSortByQuantity);

        // 分页控件初始化
        btnPrevPage = findViewById(R.id.btnPrevPage);
        btnNextPage = findViewById(R.id.btnNextPage);
        tvPageInfo = findViewById(R.id.tvPageInfo);

        // 初始化ApiClient
        apiClient = ApiClient.getInstance(this);

        // 初始化删除成功音效
        initializeDeleteSound();

        // 加载所有库存按钮事件
        Button btnLoadAll = findViewById(R.id.btnLoadAll);
        btnLoadAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadAllInventory();
            }
        });

        // 包数搜索按钮事件
        btnSearchPackage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchByPackage();
            }
        });

        // 按库位排序按钮事件
        btnSortByLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortByLocation();
            }
        });

        // 按数量降序排序按钮事件
        btnSortByQuantity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortByQuantityDesc();
            }
        });

        // 上一页按钮事件
        btnPrevPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prevPage();
            }
        });


        btnNextPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextPage();
            }
        });
    }

    // 初始化删除成功音效
    private void initializeDeleteSound() {
        try {

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

    // 加载所有库存数据
    private void loadAllInventory() {
        tvLoading.setVisibility(View.VISIBLE);
        etPackageSearch.setText(""); // 清空搜索框
        currentPage = 0; // 重置为第一页

        apiClient.loadAllInventory(new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                tvLoading.setVisibility(View.GONE);

                try {
                    if (response.getBoolean("success")) {
                        JSONArray data = response.getJSONArray("data");
                        Gson gson = new Gson();
                        allProducts = gson.fromJson(data.toString(), new TypeToken<List<QueryActivity.Product>>(){}.getType());
                        currentProducts = new ArrayList<>(allProducts);

                        Log.d("InventoryPDA", "Loaded products: " + (allProducts != null ? allProducts.size() : 0));

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (allProducts != null && !allProducts.isEmpty()) {
                                    updateTable(currentProducts);
                                    updatePageInfo();
                                    Toast.makeText(DetailActivity.this, "已加载 " + allProducts.size() + " 条记录", Toast.LENGTH_SHORT).show();
                                } else {
                                    clearTable();
                                    Toast.makeText(DetailActivity.this, "无库存数据", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } else {
                        String error = response.optString("message", "加载失败");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(DetailActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e("InventoryPDA", "JSON parsing error: " + e.getMessage());
                    clearTable();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DetailActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                tvLoading.setVisibility(View.GONE);
                String errorMessage = (error != null) ? error : "发生未知错误";
                Log.e("InventoryPDA", errorMessage);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DetailActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // 按包数搜索（精确匹配）
    private void searchByPackage() {
        String packageText = etPackageSearch.getText().toString().trim();
        if (packageText.isEmpty()) {
            Toast.makeText(this, "请输入区域", Toast.LENGTH_SHORT).show();
            return;
        }

        if (allProducts == null || allProducts.isEmpty()) {
            Toast.makeText(this, "请先加载数据", Toast.LENGTH_SHORT).show();
            return;
        }

        // 过滤出包数完全匹配输入文本的记录（精确匹配）
        List<QueryActivity.Product> filteredProducts = new ArrayList<>();
        for (QueryActivity.Product product : allProducts) {
            if (product.包数 != null && product.包数.equals(packageText)) {
                filteredProducts.add(product);
            }
        }

        if (filteredProducts.isEmpty()) {
            Toast.makeText(this, "未找到区域 '" + packageText + "' 的记录", Toast.LENGTH_SHORT).show();
        } else {
            currentProducts = filteredProducts;
            currentPage = 0; // 搜索后显示第一页
            updateTable(currentProducts);
            updatePageInfo();
            Toast.makeText(this, "找到 " + filteredProducts.size() + " 条区域 '" + packageText + "' 的记录", Toast.LENGTH_SHORT).show();
        }
    }

    // 按库位排序
    private void sortByLocation() {
        if (currentProducts == null || currentProducts.isEmpty()) {
            Toast.makeText(this, "请先加载数据", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Collections.sort(currentProducts, new Comparator<QueryActivity.Product>() {
                @Override
                public int compare(QueryActivity.Product p1, QueryActivity.Product p2) {
                    // 安全处理null值
                    String location1 = (p1.库位 != null) ? p1.库位 : "";
                    String location2 = (p2.库位 != null) ? p2.库位 : "";
                    return location1.compareTo(location2);
                }
            });

            updateTable(currentProducts);
            Toast.makeText(this, "已按库位排序", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("InventoryPDA", "排序失败: " + e.getMessage());
            Toast.makeText(this, "排序失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    // 按数量降序排序
    private void sortByQuantityDesc() {
        if (currentProducts == null || currentProducts.isEmpty()) {
            Toast.makeText(this, "请先加载数据", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Collections.sort(currentProducts, new Comparator<QueryActivity.Product>() {
                @Override
                public int compare(QueryActivity.Product p1, QueryActivity.Product p2) {
                    return Integer.compare(p2.要货数量, p1.要货数量);
                }
            });

            updateTable(currentProducts);
            Toast.makeText(this, "已按数量降序排序", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("InventoryPDA", "排序失败: " + e.getMessage());
            Toast.makeText(this, "排序失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    // 上一页
    private void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            updateTable(currentProducts);
            updatePageInfo();
        } else {
            Toast.makeText(this, "已经是第一页", Toast.LENGTH_SHORT).show();
        }
    }

    // 下一页
    private void nextPage() {
        int totalPages = (int) Math.ceil((double) currentProducts.size() / PAGE_SIZE);
        if (currentPage < totalPages - 1) {
            currentPage++;
            updateTable(currentProducts);
            updatePageInfo();
        } else {
            Toast.makeText(this, "已经是最后一页", Toast.LENGTH_SHORT).show();
        }
    }

    // 更新分页信息
    private void updatePageInfo() {
        if (currentProducts == null || currentProducts.isEmpty()) {
            tvPageInfo.setText("无数据");
            return;
        }

        int totalPages = (int) Math.ceil((double) currentProducts.size() / PAGE_SIZE);
        int startItem = currentPage * PAGE_SIZE + 1;
        int endItem = Math.min((currentPage + 1) * PAGE_SIZE, currentProducts.size());

        tvPageInfo.setText("第 " + (currentPage + 1) + " 页，共 " + totalPages + " 页 (" +
                startItem + "-" + endItem + " / " + currentProducts.size() + ")");
    }

    // 更新表格数据（分页显示）
    private void updateTable(List<QueryActivity.Product> products) {
        int childCount = tableLayout.getChildCount();
        if (childCount > 1) {
            tableLayout.removeViews(1, childCount - 1);
        }

        if (products == null || products.isEmpty()) {
            return;
        }

        // 计算当前页的数据范围
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, products.size());

        for (int i = start; i < end; i++) {
            QueryActivity.Product product = products.get(i);
            TableRow row = new TableRow(this);

            row.setTag(product);

            TextView tv1 = createTableCell(product.商品货号);
            TextView tv2 = createTableCell(String.valueOf(product.要货数量));
            TextView tv3 = createTableCell(product.包数 != null ? product.包数 : "");  // 处理可能的null值
            TextView tv4 = createTableCell(product.库位);

            row.addView(tv1);
            row.addView(tv2);
            row.addView(tv3);
            row.addView(tv4);

            final int position = i;
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    QueryActivity.Product selectedProduct = (QueryActivity.Product) v.getTag();
                    showDeleteConfirmationDialog(selectedProduct, position);
                    return true;
                }
            });

            tableLayout.addView(row);
        }
    }

    // 显示删除确认对话框
    private void showDeleteConfirmationDialog(final QueryActivity.Product product, final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除");
        builder.setMessage("确定要删除货号为 " + product.商品货号 + " 的记录吗？");

        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteProduct(product, position);
            }
        });

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // 删除商品记录（添加语音提示）
    private void deleteProduct(final QueryActivity.Product product, final int position) {
        apiClient.deleteProduct(product.商品货号, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (response.getBoolean("success")) {
                        if (currentProducts != null && position < currentProducts.size()) {
                            currentProducts.remove(position);
                            if (allProducts != null) {
                                for (int i = 0; i < allProducts.size(); i++) {
                                    if (allProducts.get(i).商品货号.equals(product.商品货号)) {
                                        allProducts.remove(i);
                                        break;
                                    }
                                }
                            }
                            updateTable(currentProducts);
                            updatePageInfo();
                            // 显示Toast提示
                            Toast.makeText(DetailActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                            // 播放删除成功音效
                            playDeleteSuccessSound();
                        }
                    } else if (response.has("message")) {
                        Toast.makeText(DetailActivity.this, "删除失败: " + response.getString("message"), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(DetailActivity.this, "删除失败: 未知错误", Toast.LENGTH_LONG).show();
                    }
                } catch (JSONException e) {
                    Log.e("InventoryPDA", "JSON parsing error: " + e.getMessage());
                    Toast.makeText(DetailActivity.this, "删除失败: 响应格式错误", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(DetailActivity.this, "删除失败: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // 创建表格单元格
    private TextView createTableCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(12, 12, 12, 12);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    // 清空表格
    private void clearTable() {
        int childCount = tableLayout.getChildCount();
        if (childCount > 1) {
            tableLayout.removeViews(1, childCount - 1);
        }
        tvPageInfo.setText("无数据");
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
}