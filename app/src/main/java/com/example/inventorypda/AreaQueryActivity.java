package com.example.inventorypda;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.media.MediaPlayer;
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
    private Button btnBatchUpdateBill;
    private TextView tvResultTitle;
    private LinearLayout llResultContainer;
    private TextView tvNoResult;
    private TextView tvTotalCount;

    // Volley请求队列
    private RequestQueue requestQueue;
    private static final String API_BASE_URL = "http://121.12.156.222:5000";

    // 查询状态
    private String currentQueryType = "";
    private String currentQueryValue = "";
    private JSONArray currentResults = new JSONArray();

    // 语音播报
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_area_query);

        // 初始化控件
        initViews();
        // 初始化Volley请求队列
        requestQueue = Volley.newRequestQueue(this);
        // 初始化语音播报
        initMediaPlayer();
        // 设置按钮点击事件
        setButtonListeners();
    }

    @Override
    protected void onDestroy() {
        // 释放MediaPlayer资源
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    // 初始化MediaPlayer
    private void initMediaPlayer() {
        try {
            // 从raw文件夹加载MP3文件
            mediaPlayer = MediaPlayer.create(this, R.raw.success_sound);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // 播放完成后重置MediaPlayer
                    mp.reset();
                }
            });
        } catch (Exception e) {
            Log.e("MediaPlayer", "初始化MediaPlayer失败: " + e.getMessage());
        }
    }

    // 播放语音
    private void playSound() {
        if (mediaPlayer != null) {
            try {
                // 如果正在播放，先停止
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                // 重置并准备播放
                mediaPlayer.reset();
                mediaPlayer = MediaPlayer.create(this, R.raw.success_sound);
                mediaPlayer.start();
            } catch (Exception e) {
                Log.e("MediaPlayer", "播放声音失败: " + e.getMessage());
            }
        }
    }

    // 初始化控件
    private void initViews() {
        etAreaInput = findViewById(R.id.et_area_input);
        btnQueryArea = findViewById(R.id.btn_query_area);
        btnQueryPlate = findViewById(R.id.btn_query_plate);
        btnBatchUpdateBill = findViewById(R.id.btn_batch_update_bill);
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
                if (area.isEmpty()) {
                    Toast.makeText(AreaQueryActivity.this, "请输入柜号", Toast.LENGTH_SHORT).show();
                    return;
                }
                clearPreviousResult();
                currentQueryType = "area";
                currentQueryValue = area;
                queryAreaRecords(area);
            }
        });

        // 板标模糊查询按钮点击事件
        btnQueryPlate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String plate = etAreaInput.getText().toString().trim();
                if (plate.isEmpty()) {
                    Toast.makeText(AreaQueryActivity.this, "请输入板标", Toast.LENGTH_SHORT).show();
                    return;
                }
                clearPreviousResult();
                currentQueryType = "plate";
                currentQueryValue = plate;
                queryPlateRecords(plate);
            }
        });

        // 批量更新提单号按钮点击事件
        btnBatchUpdateBill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentResults.length() == 0) {
                    Toast.makeText(AreaQueryActivity.this, "请先查询数据", Toast.LENGTH_SHORT).show();
                    return;
                }
                showBatchBillNumberDialog();
            }
        });
    }

    // 清空之前的查询结果
    private void clearPreviousResult() {
        llResultContainer.removeAllViews();
        tvResultTitle.setVisibility(View.GONE);
        llResultContainer.setVisibility(View.GONE);
        tvNoResult.setVisibility(View.GONE);
        tvTotalCount.setVisibility(View.GONE);
        btnBatchUpdateBill.setVisibility(View.GONE);
        currentResults = new JSONArray();
    }

    // 显示批量更新提单号对话框
    private void showBatchBillNumberDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bill_number, null);
        EditText etBillNumber = dialogView.findViewById(R.id.et_bill_number);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle("批量更新提单号");
        builder.setPositiveButton("确认", (dialog, which) -> {
            String billNumber = etBillNumber.getText().toString().trim();
            if (!billNumber.isEmpty()) {
                batchUpdateBillNumber(billNumber);
            } else {
                Toast.makeText(this, "提单号不能为空", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 批量更新提单号
    private void batchUpdateBillNumber(String billNumber) {
        try {
            JSONArray updateData = new JSONArray();
            for (int i = 0; i < currentResults.length(); i++) {
                JSONObject item = currentResults.getJSONObject(i);
                String area = item.getString("区域");
                String plate = item.getString("板标");

                JSONObject updateItem = new JSONObject();
                updateItem.put("area", area);
                updateItem.put("plate", plate);
                updateItem.put("bill_number", billNumber);
                updateData.put(updateItem);
            }

            batchUpdateBillNumberApi(updateData, billNumber);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "数据处理失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 调用批量更新API
    private void batchUpdateBillNumberApi(JSONArray updateData, final String billNumber) {
        String apiUrl = API_BASE_URL + "/receiving/batch_update_bill_number";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("data", updateData);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "请求数据构建失败", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObjectRequest jsonRequest = new JsonObjectRequest(
                Request.Method.POST,
                apiUrl,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            boolean success = response.getBoolean("success");
                            String message = response.getString("message");
                            if (success) {
                                // 播放成功提示音
                                playSound();
                                Toast.makeText(AreaQueryActivity.this, message, Toast.LENGTH_SHORT).show();

                                // 重新查询数据以刷新显示
                                if (currentQueryType.equals("area")) {
                                    queryAreaRecords(currentQueryValue);
                                } else {
                                    queryPlateRecords(currentQueryValue);
                                }
                            } else {
                                Toast.makeText(AreaQueryActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(AreaQueryActivity.this, "响应解析失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                        Toast.makeText(AreaQueryActivity.this, "网络请求失败", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        requestQueue.add(jsonRequest);
    }

    // 调用后端API查询区域记录
    private void queryAreaRecords(String area) {
        String apiUrl = API_BASE_URL + "/receiving/area_summary?area=" + area;

        Log.d("API_DEBUG", "请求URL: " + apiUrl);

        JsonObjectRequest jsonRequest = new JsonObjectRequest(
                Request.Method.GET,
                apiUrl,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            Log.d("API_DEBUG", "响应内容: " + response.toString());

                            boolean success = response.getBoolean("success");
                            if (success) {
                                JSONObject dataObj = response.getJSONObject("data");
                                JSONArray dataArray = dataObj.getJSONArray("records");

                                // 保存当前查询结果
                                currentResults = dataArray;

                                int totalRecords = dataObj.optInt("total_records", dataArray.length());
                                int totalQuantity = dataObj.optInt("total_quantity", 0);

                                if (dataArray.length() > 0) {
                                    tvResultTitle.setText("区域查询结果");
                                    tvResultTitle.setVisibility(View.VISIBLE);
                                    llResultContainer.setVisibility(View.VISIBLE);
                                    btnBatchUpdateBill.setVisibility(View.VISIBLE);

                                    tvTotalCount.setText("共计 " + totalRecords + " 条记录，合计 " + totalQuantity + " 件");
                                    tvTotalCount.setVisibility(View.VISIBLE);

                                    for (int i = 0; i < dataArray.length(); i++) {
                                        JSONObject item = dataArray.getJSONObject(i);
                                        Log.d("API_DEBUG", "项目 " + i + ": " + item.toString());

                                        String areaName = item.optString("区域", "未知区域");
                                        String plate = item.optString("板标", "未知板标");
                                        String billNumber = item.optString("提单号", "未设置");
                                        int productCount = item.optInt("商品种类数", 0);

                                        addNewResultItem(areaName, plate, billNumber, productCount);
                                    }

                                    // 播放查询成功提示音

                                } else {
                                    tvNoResult.setText("未查询到该柜号的收货记录");
                                    tvNoResult.setVisibility(View.VISIBLE);
                                }
                            } else {
                                String errorMsg = response.getString("message");
                                Toast.makeText(AreaQueryActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
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

        requestQueue.add(jsonRequest);
    }

    // 调用后端API查询板标记录
    private void queryPlateRecords(String plate) {
        String apiUrl = API_BASE_URL + "/receiving/plate_summary?plate=" + plate;

        Log.d("API_DEBUG", "请求URL: " + apiUrl);

        JsonObjectRequest jsonRequest = new JsonObjectRequest(
                Request.Method.GET,
                apiUrl,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            Log.d("API_DEBUG", "响应内容: " + response.toString());

                            boolean success = response.getBoolean("success");
                            if (success) {
                                JSONObject dataObj = response.getJSONObject("data");
                                JSONArray dataArray = dataObj.getJSONArray("records");

                                // 保存当前查询结果
                                currentResults = dataArray;

                                int totalRecords = dataObj.optInt("total_records", dataArray.length());
                                int totalQuantity = dataObj.optInt("total_quantity", 0);

                                if (dataArray.length() > 0) {
                                    tvResultTitle.setText("板标查询结果");
                                    tvResultTitle.setVisibility(View.VISIBLE);
                                    llResultContainer.setVisibility(View.VISIBLE);
                                    btnBatchUpdateBill.setVisibility(View.VISIBLE);

                                    tvTotalCount.setText("共计 " + totalRecords + " 条记录，合计 " + totalQuantity + " 件");
                                    tvTotalCount.setVisibility(View.VISIBLE);

                                    for (int i = 0; i < dataArray.length(); i++) {
                                        JSONObject item = dataArray.getJSONObject(i);
                                        Log.d("API_DEBUG", "项目 " + i + ": " + item.toString());

                                        String areaName = item.optString("区域", "未知柜号");
                                        String plate = item.optString("板标", "未知板标");
                                        String billNumber = item.optString("提单号", "未设置");
                                        int productCount = item.optInt("商品种类数", 0);

                                        addNewResultItem(areaName, plate, billNumber, productCount);
                                    }

                                    // 播放查询成功提示音
                                } else {
                                    tvNoResult.setText("未查询到包含该板标的收货记录");
                                    tvNoResult.setVisibility(View.VISIBLE);
                                }
                            } else {
                                String errorMsg = response.getString("message");
                                Toast.makeText(AreaQueryActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
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

        requestQueue.add(jsonRequest);
    }

    private void addNewResultItem(String areaName, String plate, String billNumber, int productCount) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_area_result, null);

        TextView tvArea = itemView.findViewById(R.id.tv_item_area);
        TextView tvPlate = itemView.findViewById(R.id.tv_item_plate);
        TextView tvBillNumber = itemView.findViewById(R.id.tv_item_bill_number);
        TextView tvProductCount = itemView.findViewById(R.id.tv_item_count);

        tvArea.setText(areaName);
        tvPlate.setText(plate);
        tvBillNumber.setText("提单号: " + billNumber);
        tvProductCount.setText(String.valueOf(productCount));

        llResultContainer.addView(itemView);
    }
}