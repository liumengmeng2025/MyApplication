package com.example.inventorypda;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryResultActivity extends AppCompatActivity {

    private static final String TAG = "QueryResultActivity";
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvLoading, tvQueryTitle, tvTotalCount;
    private QueryResultAdapter adapter;
    private List<Map<String, String>> dataList = new ArrayList<>();
    private String queryType, branch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_query_result);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvLoading = findViewById(R.id.tvLoading);
        tvQueryTitle = findViewById(R.id.tvQueryTitle);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        Button btnBack = findViewById(R.id.btnBack);


        queryType = getIntent().getStringExtra("query_type");
        branch = getIntent().getStringExtra("branch");

        Log.d(TAG, "查询参数 - queryType: " + queryType + ", branch: " + branch);


        String title = "";
        switch (queryType) {
            case "unscanned_xiangma":
                title = "未扫描箱唛查询 - " + branch;
                break;
            case "pending_data":
                title = "待装数据查询 - " + branch;
                break;
            case "plate_count":
                title = "板标件数查询 - " + branch;
                break;
        }
        tvQueryTitle.setText(title);


        tvTotalCount.setVisibility(View.GONE);


        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QueryResultAdapter(dataList, queryType);
        recyclerView.setAdapter(adapter);


        loadData();

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void loadData() {
        String baseUrl = "http://121.12.156.222:5000";
        String url = baseUrl + "/query/" + queryType;

        Log.d(TAG, "完整请求URL: " + url);
        Log.d(TAG, "请求方法: POST");
        Log.d(TAG, "查询类型: " + queryType);
        Log.d(TAG, "分公司: " + branch);

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        progressBar.setVisibility(View.GONE);
                        tvLoading.setVisibility(View.GONE);
                        Log.d(TAG, "服务器响应成功，响应内容: " + response.substring(0, Math.min(200, response.length())));

                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            boolean success = jsonResponse.getBoolean("success");
                            Log.d(TAG, "响应success字段: " + success);

                            if (success) {
                                JSONArray data = jsonResponse.getJSONArray("data");
                                Log.d(TAG, "数据数组长度: " + data.length());
                                dataList.clear();

                                for (int i = 0; i < data.length(); i++) {
                                    JSONObject item = data.getJSONObject(i);
                                    Map<String, String> map = new HashMap<>();

                                    // 根据查询类型解析不同的字段
                                    switch (queryType) {
                                        case "unscanned_xiangma":
                                            map.put("创建时间", item.optString("创建时间", ""));
                                            map.put("箱唛", item.optString("箱唛", ""));
                                            break;
                                        case "pending_data":
                                            map.put("立方", item.optString("立方", ""));
                                            map.put("重量", item.optString("重量", ""));
                                            map.put("分公司", item.optString("分公司", ""));
                                            map.put("件数", item.optString("件数", ""));
                                            break;
                                        case "plate_count":
                                            map.put("类别", item.optString("类别", ""));
                                            map.put("件数", item.optString("件数", ""));
                                            map.put("已绑定件数", item.optString("已绑定件数", ""));
                                            map.put("板标", item.optString("板标", ""));
                                            map.put("差异", item.optString("差异", ""));
                                            break;
                                    }
                                    dataList.add(map);
                                }

                                adapter.notifyDataSetChanged();

                                // 更新记录总数显示
                                updateTotalCountDisplay();

                                if (dataList.isEmpty()) {
                                    tvLoading.setText("没有查询到数据");
                                    tvLoading.setVisibility(View.VISIBLE);
                                    Log.d(TAG, "查询成功但没有数据");
                                } else {
                                    Log.d(TAG, "成功加载 " + dataList.size() + " 条数据");
                                }

                            } else {
                                String errorMsg = jsonResponse.getString("message");
                                Toast.makeText(QueryResultActivity.this, "服务器返回错误: " + errorMsg, Toast.LENGTH_LONG).show();
                                Log.e(TAG, "服务器返回错误: " + errorMsg);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(QueryResultActivity.this, "数据解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e(TAG, "JSON解析错误: " + e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        progressBar.setVisibility(View.GONE);
                        tvLoading.setText("网络请求失败");

                        String errorDetails = "Volley错误: ";
                        if (error.networkResponse != null) {
                            errorDetails += "状态码=" + error.networkResponse.statusCode +
                                    ", 数据=" + new String(error.networkResponse.data);
                        } else {
                            errorDetails += error.getMessage();
                        }

                        Toast.makeText(QueryResultActivity.this, "网络请求失败，请查看日志", Toast.LENGTH_LONG).show();
                        Log.e(TAG, errorDetails);

                        // 打印完整的错误堆栈
                        error.printStackTrace();
                    }
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("branch", branch);
                Log.d(TAG, "POST参数: branch=" + branch);
                return params;
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                return headers;
            }
        };

        // 设置超时和重试策略
        stringRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                15000, // 15秒超时
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Log.d(TAG, "开始发送Volley请求...");
        Volley.newRequestQueue(this).add(stringRequest);
    }

    /**
     * 更新记录总数显示
     */
    private void updateTotalCountDisplay() {
        int totalCount = dataList.size();

        // 只在未扫描箱唛查询中显示记录数
        if ("unscanned_xiangma".equals(queryType)) {
            tvTotalCount.setText("共计：" + totalCount + " 条记录");
            tvTotalCount.setVisibility(View.VISIBLE);
        } else {
            tvTotalCount.setVisibility(View.GONE);
        }
    }
}