    package com.example.inventorypda;

    import androidx.appcompat.app.AppCompatActivity;
    import android.os.Bundle;
    import android.util.Log;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.widget.Button;
    import android.widget.EditText;
    import android.widget.LinearLayout;
    import android.widget.TextView;
    import android.widget.Toast;
    import com.android.volley.Request;
    import com.android.volley.RequestQueue;
    import com.android.volley.Response;
    import com.android.volley.VolleyError;
    import com.android.volley.toolbox.JsonObjectRequest;
    import com.android.volley.toolbox.Volley;
    import org.json.JSONArray;
    import org.json.JSONException;
    import org.json.JSONObject;

    public class AreaQueryActivity extends AppCompatActivity {

        // 控件声明
        private EditText etAreaInput;
        private Button btnQueryArea;
        private Button btnQueryPlate;
        private TextView tvResultTitle;
        private LinearLayout llResultContainer;
        private TextView tvNoResult;
        private TextView tvTotalCount;
        // Volley请求队列（用于网络请求）
        private RequestQueue requestQueue;
        private static final String API_BASE_URL = "http://121.12.156.222:5000";

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_area_query);

            // 1. 初始化控件（绑定XML中的ID）
            initViews();
            // 2. 初始化Volley请求队列
            requestQueue = Volley.newRequestQueue(this);
            // 3. 设置按钮点击事件
            setButtonListeners();
        }

        // 初始化控件（确保XML中ID与这里一致）
        private void initViews() {
            etAreaInput = findViewById(R.id.et_area_input);
            btnQueryArea = findViewById(R.id.btn_query_area);
            btnQueryPlate = findViewById(R.id.btn_query_plate);
            tvResultTitle = findViewById(R.id.tv_result_title);
            llResultContainer = findViewById(R.id.ll_result_container);
            tvNoResult = findViewById(R.id.tv_no_result);
            tvTotalCount = findViewById(R.id.tv_total_count);
        }

        // 设置按钮点击事件
        private void setButtonListeners() {
            // 区域查询按钮点击事件
            btnQueryArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String area = etAreaInput.getText().toString().trim();
                    // 校验输入：区域不能为空
                    if (area.isEmpty()) {
                        Toast.makeText(AreaQueryActivity.this, "请输入区域名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 清空之前的查询结果
                    clearPreviousResult();
                    // 调用API查询区域记录
                    queryAreaRecords(area);
                }
            });

            // 板标模糊查询按钮点击事件
            btnQueryPlate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String plate = etAreaInput.getText().toString().trim();
                    // 校验输入：板标不能为空
                    if (plate.isEmpty()) {
                        Toast.makeText(AreaQueryActivity.this, "请输入板标", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 清空之前的查询结果
                    clearPreviousResult();
                    // 调用API查询板标记录
                    queryPlateRecords(plate);
                }
            });
        }

        // 清空之前的查询结果（避免多次查询结果叠加）
        private void clearPreviousResult() {
            llResultContainer.removeAllViews(); // 清空结果列表
            tvResultTitle.setVisibility(View.GONE); // 隐藏结果标题
            llResultContainer.setVisibility(View.GONE); // 隐藏结果容器
            tvNoResult.setVisibility(View.GONE); // 隐藏无结果提示
            tvTotalCount.setVisibility(View.GONE); // 隐藏总记录数显示
        }
        // 调用后端API查询区域记录
        private void queryAreaRecords(String area) {
            // 构造API请求地址
            String apiUrl = API_BASE_URL + "/receiving/area_summary?area=" + area;

            Log.d("API_DEBUG", "请求URL: " + apiUrl);

            // 1. 创建Volley GET请求
            JsonObjectRequest jsonRequest = new JsonObjectRequest(
                    Request.Method.GET,
                    apiUrl,
                    null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                Log.d("API_DEBUG", "响应内容: " + response.toString());

                                // 解析后端返回的success字段（判断请求是否成功）
                                boolean success = response.getBoolean("success");
                                if (success) {
                                    // 解析数据
                                    JSONObject dataObj = response.getJSONObject("data");
                                    JSONArray dataArray = dataObj.getJSONArray("records");

                                    // 获取总记录数和总件数
                                    int totalRecords = dataObj.optInt("total_records", dataArray.length());
                                    int totalQuantity = dataObj.optInt("total_quantity", 0);

                                    if (dataArray.length() > 0) {
                                        // 有数据：显示结果标题和容器，遍历添加结果项
                                        tvResultTitle.setText("区域查询结果");
                                        tvResultTitle.setVisibility(View.VISIBLE);
                                        llResultContainer.setVisibility(View.VISIBLE);

                                        // 显示总记录数和总件数（在同一行）
                                        tvTotalCount.setText("共计 " + totalRecords + " 条记录，合计 " + totalQuantity + " 件");
                                        tvTotalCount.setVisibility(View.VISIBLE);

                                        for (int i = 0; i < dataArray.length(); i++) {
                                            JSONObject item = dataArray.getJSONObject(i);
                                            Log.d("API_DEBUG", "项目 " + i + ": " + item.toString());

                                            // 从后端获取字段
                                            String areaName = item.optString("区域", "未知区域");
                                            String plate = item.optString("板标", "未知板标");
                                            int productCount = item.optInt("商品种类数", 0);

                                            // 动态添加结果项到界面
                                            addNewResultItem(areaName, plate, productCount);
                                        }
                                    } else {
                                        // 无数据：显示无结果提示
                                        tvNoResult.setText("未查询到该区域的收货记录");
                                        tvNoResult.setVisibility(View.VISIBLE);
                                    }
                                } else {
                                    // 后端返回失败（如参数错误）：显示错误信息
                                    String errorMsg = response.getString("message");
                                    Toast.makeText(AreaQueryActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                // JSON解析失败（如字段不匹配）
                                e.printStackTrace();
                                Log.e("API_DEBUG", "JSON解析错误: " + e.getMessage());
                                Toast.makeText(AreaQueryActivity.this, "查询结果解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            error.printStackTrace();
                            Log.e("API_DEBUG", "网络请求错误: " + error.getMessage());
                            Toast.makeText(AreaQueryActivity.this, "网络请求失败，请检查服务器连接", Toast.LENGTH_SHORT).show();
                        }
                    }
            );

            // 4. 将请求加入队列（执行网络请求）
            requestQueue.add(jsonRequest);
        }

        // 调用后端API查询板标记录
        private void queryPlateRecords(String plate) {
            // 构造API请求地址
            String apiUrl = API_BASE_URL + "/receiving/plate_summary?plate=" + plate;

            Log.d("API_DEBUG", "请求URL: " + apiUrl);

            // 1. 创建Volley GET请求
            JsonObjectRequest jsonRequest = new JsonObjectRequest(
                    Request.Method.GET,
                    apiUrl,
                    null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                Log.d("API_DEBUG", "响应内容: " + response.toString());

                                // 解析后端返回的success字段（判断请求是否成功）
                                boolean success = response.getBoolean("success");
                                if (success) {
                                    // 解析数据
                                    JSONObject dataObj = response.getJSONObject("data");
                                    JSONArray dataArray = dataObj.getJSONArray("records");

                                    // 获取总记录数和总件数
                                    int totalRecords = dataObj.optInt("total_records", dataArray.length());
                                    int totalQuantity = dataObj.optInt("total_quantity", 0);

                                    if (dataArray.length() > 0) {
                                        // 有数据：显示结果标题和容器，遍历添加结果项
                                        tvResultTitle.setText("板标查询结果");
                                        tvResultTitle.setVisibility(View.VISIBLE);
                                        llResultContainer.setVisibility(View.VISIBLE);

                                        // 显示总记录数和总件数（在同一行）
                                        tvTotalCount.setText("共计 " + totalRecords + " 条记录，合计 " + totalQuantity + " 件");
                                        tvTotalCount.setVisibility(View.VISIBLE);

                                        for (int i = 0; i < dataArray.length(); i++) {
                                            JSONObject item = dataArray.getJSONObject(i);
                                            Log.d("API_DEBUG", "项目 " + i + ": " + item.toString());

                                            // 从后端获取字段
                                            String areaName = item.optString("区域", "未知区域");
                                            String plate = item.optString("板标", "未知板标");
                                            int productCount = item.optInt("商品种类数", 0);

                                            // 动态添加结果项到界面
                                            addNewResultItem(areaName, plate, productCount);
                                        }
                                    } else {
                                        // 无数据：显示无结果提示
                                        tvNoResult.setText("未查询到包含该板标的收货记录");
                                        tvNoResult.setVisibility(View.VISIBLE);
                                    }
                                } else {
                                    // 后端返回失败（如参数错误）：显示错误信息
                                    String errorMsg = response.getString("message");
                                    Toast.makeText(AreaQueryActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                // JSON解析失败（如字段不匹配）
                                e.printStackTrace();
                                Log.e("API_DEBUG", "JSON解析错误: " + e.getMessage());
                                Toast.makeText(AreaQueryActivity.this, "查询结果解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            error.printStackTrace();
                            Log.e("API_DEBUG", "网络请求错误: " + error.getMessage());
                            Toast.makeText(AreaQueryActivity.this, "网络请求失败，请检查服务器连接", Toast.LENGTH_SHORT).show();
                        }
                    }
            );

            // 4. 将请求加入队列（执行网络请求）
            requestQueue.add(jsonRequest);
        }

        private void addNewResultItem(String areaName, String plate, int productCount) {
            // 1. 加载单个结果项的布局
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_area_result, null);

            // 2. 绑定结果项的控件
            TextView tvArea = itemView.findViewById(R.id.tv_item_area); // 需要确保布局中有这个TextView
            TextView tvPlate = itemView.findViewById(R.id.tv_item_plate);
            TextView tvProductCount = itemView.findViewById(R.id.tv_item_count);

            // 3. 设置结果项数据
            tvArea.setText(areaName);
            tvPlate.setText(plate);
            tvProductCount.setText(String.valueOf(productCount));

            // 4. 将结果项添加到容器
            llResultContainer.addView(itemView);
        }
        }
