package com.example.inventorypda;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CabinetWithdrawActivity extends AppCompatActivity {

    private EditText etInput;
    private Button btnWithdrawSerial, btnWithdrawPlate, btnQuerySerial, btnQueryPlate, btnQueryLoaded;
    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cabinet_withdraw);

        initViews();
        setupButtonListeners();
    }

    private void initViews() {
        etInput = findViewById(R.id.etInput);
        btnWithdrawSerial = findViewById(R.id.btnWithdrawSerial);
        btnWithdrawPlate = findViewById(R.id.btnWithdrawPlate);
        btnQuerySerial = findViewById(R.id.btnQuerySerial);
        btnQueryPlate = findViewById(R.id.btnQueryPlate);
        btnQueryLoaded = findViewById(R.id.btnQueryLoaded);
        tvResult = findViewById(R.id.tvResult);
    }

    private void setupButtonListeners() {
        // 撤回流水按钮
        btnWithdrawSerial.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入商品货号", Toast.LENGTH_SHORT).show();
                return;
            }
            withdrawSerial(input);
        });

        // 撤回板标按钮
        btnWithdrawPlate.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
                return;
            }
            withdrawPlate(input);
        });

        // 流水查询按钮
        btnQuerySerial.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入商品货号", Toast.LENGTH_SHORT).show();
                return;
            }
            querySerial(input);
        });

        // 板标查询按钮
        btnQueryPlate.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
                return;
            }
            queryPlate(input);
        });

        // 提单号查询按钮
        btnQueryLoaded.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入提单号", Toast.LENGTH_SHORT).show();
                return;
            }
            queryByBillNumber(input);
        });
    }

    private void withdrawSerial(String barcode) {
        Map<String, String> params = new HashMap<>();
        params.put("barcode", barcode);

        ApiClient.getInstance(this).postRequest("/cabinet/withdraw_serial", params,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            String message = response.getString("message");
                            tvResult.setText("撤回流水结果: " + message);
                            Toast.makeText(CabinetWithdrawActivity.this, message, Toast.LENGTH_SHORT).show();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            tvResult.setText("撤回流水结果: 数据解析错误");
                        }
                    }

                    @Override
                    public void onError(String error) {
                        tvResult.setText("撤回流水失败: " + error);
                    }
                });
    }

    private void withdrawPlate(String plate) {
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);

        ApiClient.getInstance(this).postRequest("/cabinet/withdraw_plate", params,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            String message = response.getString("message");
                            tvResult.setText("撤回板标结果: " + message);
                            Toast.makeText(CabinetWithdrawActivity.this, message, Toast.LENGTH_SHORT).show();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            tvResult.setText("撤回板标结果: 数据解析错误");
                        }
                    }

                    @Override
                    public void onError(String error) {
                        tvResult.setText("撤回板标失败: " + error);
                    }
                });
    }

    private void querySerial(String barcode) {
        ApiClient.getInstance(this).getRequest("/cabinet/query_serial?barcode=" + barcode,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            if (response.getBoolean("success")) {
                                JSONObject data = response.getJSONObject("data");
                                int totalCount = data.getInt("total_count");
                                JSONArray records = data.getJSONArray("records");

                                if (totalCount == 0) {
                                    tvResult.setText("未找到商品货号 " + barcode + " 的相关记录");
                                    return;
                                }

                                StringBuilder result = new StringBuilder();
                                result.append("商品货号: ").append(barcode).append("\n");
                                result.append("查询结果: 共找到 ").append(totalCount).append(" 条记录\n\n");

                                for (int i = 0; i < records.length(); i++) {
                                    JSONObject record = records.getJSONObject(i);
                                    String plate = record.optString("板标", "");
                                    String area = record.optString("区域", "");
                                    String billNumber = record.optString("提单号", "");
                                    String timestamp = record.optString("timestamp", "");
                                    String ip = record.optString("ip", "");

                                    // 格式化时间
                                    String formattedTime = formatTimestamp(timestamp);

                                    result.append("记录 ").append(i + 1).append(":\n");
                                    result.append("板标: ").append(plate).append("\n");
                                    result.append("区域: ").append(area).append("\n");
                                    result.append("提单号: ").append(billNumber).append("\n");
                                    result.append("操作时间: ").append(formattedTime).append("\n");
                                    result.append("操作IP: ").append(ip).append("\n");
                                    result.append("---\n");
                                }

                                tvResult.setText(result.toString());
                            } else {
                                tvResult.setText("查询失败: " + response.optString("error", "未知错误"));
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            tvResult.setText("数据解析错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        tvResult.setText("查询失败: " + error);
                    }
                });
    }

    private void queryPlate(String plate) {
        ApiClient.getInstance(this).getRequest("/cabinet/query_plate?plate=" + plate,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            if (response.getBoolean("success")) {
                                JSONObject data = response.getJSONObject("data");
                                int totalCount = data.getInt("total_count");
                                JSONArray records = data.getJSONArray("records");

                                if (totalCount == 0) {
                                    tvResult.setText("未找到板标 " + plate + " 的相关记录");
                                    return;
                                }

                                StringBuilder result = new StringBuilder();
                                result.append("板标: ").append(plate).append("\n");
                                result.append("查询结果: 共找到 ").append(totalCount).append(" 条记录\n\n");

                                for (int i = 0; i < records.length(); i++) {
                                    JSONObject record = records.getJSONObject(i);
                                    String barcode = record.optString("barcode", "");
                                    String area = record.optString("区域", "");
                                    String billNumber = record.optString("提单号", "");
                                    String timestamp = record.optString("timestamp", "");
                                    String ip = record.optString("ip", "");

                                    // 格式化时间
                                    String formattedTime = formatTimestamp(timestamp);

                                    result.append("记录 ").append(i + 1).append(":\n");
                                    result.append("商品货号: ").append(barcode).append("\n");
                                    result.append("区域: ").append(area).append("\n");
                                    result.append("提单号: ").append(billNumber).append("\n");
                                    result.append("操作时间: ").append(formattedTime).append("\n");
                                    result.append("操作IP: ").append(ip).append("\n");
                                    result.append("---\n");
                                }

                                tvResult.setText(result.toString());
                            } else {
                                tvResult.setText("查询失败: " + response.optString("error", "未知错误"));
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            tvResult.setText("数据解析错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        tvResult.setText("查询失败: " + error);
                    }
                });
    }

    private void queryByBillNumber(String billNumber) {
        ApiClient.getInstance(this).getRequest("/receiving/query_by_bill_number?bill_number=" + billNumber,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            if (response.getBoolean("success")) {
                                JSONObject data = response.getJSONObject("data");
                                int totalRecords = data.getInt("total_records");
                                int totalPlates = data.getInt("total_plates");
                                int totalBarcodes = data.getInt("total_barcodes");
                                int distinctBarcodes = data.getInt("distinct_barcodes");
                                JSONArray items = data.getJSONArray("items");

                                if (totalRecords == 0) {
                                    tvResult.setText("未找到相关提单号的收货信息");
                                    return;
                                }

                                StringBuilder result = new StringBuilder();
                                result.append("提单号: ").append(billNumber).append("\n");
                                result.append("查询结果: 共找到 ").append(totalRecords).append(" 条记录\n");
                                result.append("共计: ").append(totalPlates).append(" 个板\n");
                                result.append("件数: ").append(totalBarcodes).append("\n");
                                result.append("不重复件数: ").append(distinctBarcodes).append("\n\n");

                                // 显示每个板的详细信息
                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject item = items.getJSONObject(i);
                                    String plateLabel = item.optString("板标", "");
                                    int barcodeCount = item.optInt("barcode_count", 0);
                                    int distinctBarcodeCount = item.optInt("distinct_barcode_count", 0);

                                    result.append("板标: ").append(plateLabel).append("\n");
                                    result.append("件数: ").append(barcodeCount).append("\n");
                                    result.append("不重复件数: ").append(distinctBarcodeCount).append("\n");
                                    result.append("---\n");
                                }

                                tvResult.setText(result.toString());
                            } else {
                                tvResult.setText("查询失败: " + response.optString("error", "未知错误"));
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            tvResult.setText("数据解析错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        tvResult.setText("查询失败: " + error);
                    }
                });
    }

    private String formatTimestamp(String timestamp) {
        try {
            if (timestamp.contains("T")) {
                // 处理ISO格式时间: 2024-01-01T12:00:00
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date date = inputFormat.parse(timestamp);
                return outputFormat.format(date);
            } else {
                // 已经是普通格式
                return timestamp;
            }
        } catch (Exception e) {
            return timestamp;
        }
    }
}