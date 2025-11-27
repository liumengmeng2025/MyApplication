package com.example.inventorypda;

import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class BranchClearActivity extends AppCompatActivity {

    private RadioGroup branchRadioGroup;
    private Button btnClearData, btnCancel;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_branch_clear);

        initViews();
        setupListeners();
    }

    private void initViews() {
        branchRadioGroup = findViewById(R.id.branchRadioGroup);
        btnClearData = findViewById(R.id.btnClearData);
        btnCancel = findViewById(R.id.btnCancel);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        btnClearData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearData();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void clearData() {
        int selectedId = branchRadioGroup.getCheckedRadioButtonId();

        if (selectedId == -1) {
            Toast.makeText(this, "请选择分公司", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRadioButton = findViewById(selectedId);
        String branch = selectedRadioButton.getText().toString();

        // 显示确认对话框
        new AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("确定要清空分公司 '" + branch + "' 的所有板标数据吗？\n\n此操作将清空商品货号、板标、区域等字段，但保留箱唛和分公司信息。")
                .setPositiveButton("确定", (dialog, which) -> {
                    new ClearBranchDataTask().execute(branch);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class ClearBranchDataTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
            btnClearData.setEnabled(false);
            btnCancel.setEnabled(false);
        }

        @Override
        protected String doInBackground(String... params) {
            String branch = params[0];
            String result = "";

            try {
                URL url = new URL("http://192.168.17.121:5000/receiving/clear_branch_data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                JSONObject jsonParam = new JSONObject();
                jsonParam.put("branch", branch);

                OutputStream os = conn.getOutputStream();
                os.write(jsonParam.toString().getBytes("utf-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8");
                    result = scanner.useDelimiter("\\A").next();
                    scanner.close();
                } else {
                    result = "错误: " + responseCode;
                }

                conn.disconnect();
            } catch (Exception e) {
                result = "异常: " + e.getMessage();
            }

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            progressBar.setVisibility(View.GONE);
            btnClearData.setEnabled(true);
            btnCancel.setEnabled(true);

            try {
                JSONObject jsonResponse = new JSONObject(result);
                boolean success = jsonResponse.getBoolean("success");
                String message = jsonResponse.getString("message");

                if (success) {
                    Toast.makeText(BranchClearActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    Toast.makeText(BranchClearActivity.this, "清空失败: " + message, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(BranchClearActivity.this, "解析响应失败: " + result, Toast.LENGTH_LONG).show();
            }
        }
    }
}