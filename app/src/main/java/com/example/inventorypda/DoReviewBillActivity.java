package com.example.inventorypda;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class DoReviewBillActivity extends AppCompatActivity {

    private EditText etBillNumber;
    private TextView tvLoading;
    private Button btnQuery, btnBack;

    private ApiClient apiClient;
    private Handler autoQueryHandler = new Handler();
    private Runnable autoQueryRunnable;

    // 语音播放器
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_do_review_bill);

        initViews();
        apiClient = ApiClient.getInstance(this);

        setupAutoQuery();
    }

    private void initViews() {
        etBillNumber = findViewById(R.id.etBillNumber);
        tvLoading = findViewById(R.id.tvLoading);
        btnQuery = findViewById(R.id.btnQuery);
        btnBack = findViewById(R.id.btnBack);

        btnQuery.setOnClickListener(v -> queryDoBill());
        btnBack.setOnClickListener(v -> finish());

        // 请求焦点并打开软键盘
        etBillNumber.requestFocus();
    }

    private void setupAutoQuery() {
        etBillNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String billNumber = s.toString().trim();
                // 移除回车符和换行符
                billNumber = removeEnterAndNewline(billNumber);
                if (!billNumber.equals(s.toString())) {
                    etBillNumber.setText(billNumber);
                    etBillNumber.setSelection(billNumber.length());
                    return;
                }

                if (billNumber.length() > 0) {
                    // 移除之前的回调
                    if (autoQueryRunnable != null) {
                        autoQueryHandler.removeCallbacks(autoQueryRunnable);
                    }

                    // 1秒后自动查询
                    autoQueryRunnable = () -> queryDoBill();
                    autoQueryHandler.postDelayed(autoQueryRunnable, 100);
                }
            }
        });
    }

    /**
     * 移除字符串中的回车符和换行符
     */
    private String removeEnterAndNewline(String text) {
        if (text == null) return "";
        return text.replace("\r", "").replace("\n", "").trim();
    }

    private void queryDoBill() {
        String inputBillNumber = etBillNumber.getText().toString().trim();
        // 处理回车符和换行符
        final String billNumber = removeEnterAndNewline(inputBillNumber); // 使用 final 变量

        if (billNumber.isEmpty()) {
            Toast.makeText(this, "请输入单据号", Toast.LENGTH_SHORT).show();
            return;
        }

        tvLoading.setVisibility(TextView.VISIBLE);

        // 创建一个final的局部变量供lambda使用
        final String finalBillNumber = billNumber;

        apiClient.queryDoByBill(billNumber, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                runOnUiThread(() -> {
                    tvLoading.setVisibility(TextView.GONE);
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject data = response.getJSONObject("data");
                            int totalQuantity = data.getInt("total_quantity");

                            // 判断是否有明细数据
                            if (totalQuantity > 0) {
                                // 有明细数据，播报成功语音并跳转
                                playSound(R.raw.success_sound2);

                                // 跳转到复核明细界面
                                Intent intent = new Intent(DoReviewBillActivity.this, DoReviewDetailActivity.class);
                                intent.putExtra("bill_number", finalBillNumber); // 使用final变量
                                intent.putExtra("total_quantity", totalQuantity);
                                startActivity(intent);
                            } else {
                                // 没有明细数据，播报错误语音并清空输入框
                                playSound(R.raw.error_sound2);
                                etBillNumber.setText("");
                                etBillNumber.requestFocus();
                                Toast.makeText(DoReviewBillActivity.this,
                                        "单号不存在", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            // 单据不存在或其他错误，播报错误语音并清空输入框
                            playSound(R.raw.error_sound);
                            etBillNumber.setText("");
                            etBillNumber.requestFocus();
                            Toast.makeText(DoReviewBillActivity.this,
                                    response.getString("message"), Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        // 数据解析错误，播报错误语音并清空输入框
                        playSound(R.raw.error_sound);
                        etBillNumber.setText("");
                        etBillNumber.requestFocus();
                        Toast.makeText(DoReviewBillActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    tvLoading.setVisibility(TextView.GONE);
                    // 网络错误，播报错误语音并清空输入框
                    playSound(R.raw.error_sound);
                    etBillNumber.setText("");
                    etBillNumber.requestFocus();
                    Toast.makeText(DoReviewBillActivity.this, "查询失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    /**
     * 播放语音文件
     * @param soundResourceId 语音文件资源ID（如R.raw.success_sound）
     */
    private void playSound(int soundResourceId) {
        // 先释放之前的媒体播放器
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        try {
            mediaPlayer = MediaPlayer.create(this, soundResourceId);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    mediaPlayer = null;
                });
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "语音播放失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除回调防止内存泄漏
        if (autoQueryRunnable != null) {
            autoQueryHandler.removeCallbacks(autoQueryRunnable);
        }

        // 释放媒体播放器
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}