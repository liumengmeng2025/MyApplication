package com.example.inventorypda;

import android.app.AlertDialog;
import android.content.DialogInterface;
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

public class QueryActivity extends AppCompatActivity {

    private EditText etBarcode;
    private TableLayout tableLayout;
    private List<Product> currentProducts;
    private ApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_query);

        // 初始化UI组件
        etBarcode = findViewById(R.id.etBarcode);
        tableLayout = findViewById(R.id.tableLayout);

        // 初始化ApiClient
        apiClient = ApiClient.getInstance(this);

        // 查询按钮事件
        Button btnQuery = findViewById(R.id.btnQuery);
        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                                }
                            }
                        });
                    } else {
                        String error = response.optString("message", "查询失败");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(QueryActivity.this, error, Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(QueryActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                clearTable();
                            }
                        });
                    } else {
                        String error = response.optString("message", "删除失败");
                        final String finalError = error;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(QueryActivity.this, finalError, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(QueryActivity.this, "响应格式错误", Toast.LENGTH_SHORT).show();
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
                    }
                });
            }
        });
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