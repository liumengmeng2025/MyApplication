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
import java.util.HashMap;
import java.util.Map;

public class CabinetWithdrawActivity extends AppCompatActivity {

    private EditText etInput;
    private Button btnWithdrawSerial, btnWithdrawPlate, btnQueryLoaded;
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
        btnQueryLoaded = findViewById(R.id.btnQueryLoaded);
        tvResult = findViewById(R.id.tvResult);
    }

    private void setupButtonListeners() {
        // 撤回流水按钮
        btnWithdrawSerial.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入流水号", Toast.LENGTH_SHORT).show();
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

    private void withdrawSerial(String serialNo) {
        Map<String, String> params = new HashMap<>();
        params.put("serial_no", serialNo);

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

    private void withdrawPlate(String barcode) {
        Map<String, String> params = new HashMap<>();
        params.put("barcode", barcode);

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
}