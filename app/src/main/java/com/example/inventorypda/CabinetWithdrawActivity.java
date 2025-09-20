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
            withdrawPlate(input); // 现在这个方法已经被正确定义
        });

        // 已装查询按钮
        btnQueryLoaded.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
                return;
            }
            queryLoaded(input);
        });
    }

    // 保留一个withdrawSerial方法，处理流水号撤回
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

    // 新增withdrawPlate方法，处理板标撤回
    private void withdrawPlate(String barcode) {
        Map<String, String> params = new HashMap<>();
        params.put("barcode", barcode);

        ApiClient.getInstance(this).postRequest("/cabinet/withdraw_plate", params, // 假设使用不同的API端点
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

    private void queryLoaded(String plate) {
        ApiClient.getInstance(this).getRequest("/cabinet/query_loaded?plate=" + plate,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            JSONObject data = response.getJSONObject("data");
                            int plateCount = data.getInt("plate_count");
                            JSONArray plates = data.getJSONArray("plates");

                            StringBuilder result = new StringBuilder();
                            result.append("已装柜查询结果:\n");
                            result.append("共找到 ").append(plateCount).append(" 个板\n\n");

                            for (int i = 0; i < plates.length(); i++) {
                                JSONObject plateInfo = plates.getJSONObject(i);
                                result.append("板标: ").append(plateInfo.getString("板标")).append("\n");
                                result.append("不重复件数: ").append(plateInfo.getInt("product_count")).append("\n");
                                result.append("件数: ").append(plateInfo.getInt("total_count")).append("\n\n");
                            }

                            tvResult.setText(result.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                            tvResult.setText("已装柜查询结果: 数据解析错误");
                        }
                    }

                    @Override
                    public void onError(String error) {
                        tvResult.setText("已装柜查询失败: " + error);
                    }
                });
    }
}
