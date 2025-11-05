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

    // 防抖相关变量
    private static final long AUTO_QUERY_DELAY = 500; //
    private static final int MIN_BILL_NUMBER_LENGTH = 11; // 最小单据号长度
    private boolean isQueryInProgress = false; // 防止重复查询

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

                // 检测是否包含换行符，如果包含则只处理换行符之前的内容
                if (billNumber.contains("\n") || billNumber.contains("\r")) {
                    // 获取换行符之前的内容
                    String cleanBillNumber = getContentBeforeNewline(billNumber);
                    if (!cleanBillNumber.isEmpty() && cleanBillNumber.length() >= MIN_BILL_NUMBER_LENGTH) {
                        // 设置清理后的文本（不含换行符）
                        etBillNumber.setText(cleanBillNumber);
                        etBillNumber.setSelection(cleanBillNumber.length());

                        // 直接查询，不清空输入框
                        queryDoBill();
                    }
                    return;
                }

                // 移除之前的回调
                if (autoQueryRunnable != null) {
                    autoQueryHandler.removeCallbacks(autoQueryRunnable);
                }

                // 只有满足最小长度时才自动查询
                if (billNumber.length() >= MIN_BILL_NUMBER_LENGTH && !isQueryInProgress) {
                    autoQueryRunnable = () -> {
                        if (!isQueryInProgress) {
                            queryDoBill();
                        }
                    };
                    autoQueryHandler.postDelayed(autoQueryRunnable, AUTO_QUERY_DELAY);
                }
            }
        });
    }

    /**
     * 获取换行符之前的内容
     */
    private String getContentBeforeNewline(String text) {
        if (text == null) return "";

        // 找到第一个换行符的位置
        int newlineIndex = text.indexOf('\n');
        if (newlineIndex == -1) {
            newlineIndex = text.indexOf('\r');
        }

        // 如果有换行符，只返回换行符之前的内容
        if (newlineIndex != -1) {
            return text.substring(0, newlineIndex).trim();
        }

        return text.trim();
    }

    private String removeEnterAndNewline(String text) {
        if (text == null) return "";
        return text.replace("\r", "").replace("\n", "").trim();
    }

    private void queryDoBill() {
        // 防止重复查询
        if (isQueryInProgress) {
            return;
        }

        String inputBillNumber = etBillNumber.getText().toString().trim();
        // 处理回车符和换行符 - 使用新的方法只获取换行前的内容
        final String billNumber = getContentBeforeNewline(inputBillNumber);

        if (billNumber.isEmpty()) {
            Toast.makeText(this, "请输入单据号", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查最小长度
        if (billNumber.length() < MIN_BILL_NUMBER_LENGTH) {
            Toast.makeText(this, "单据号长度不足", Toast.LENGTH_SHORT).show();
            return;
        }

        isQueryInProgress = true;
        tvLoading.setVisibility(TextView.VISIBLE);

        // 创建一个final的局部变量供lambda使用
        final String finalBillNumber = billNumber;

        apiClient.queryDoByBill(billNumber, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                runOnUiThread(() -> {
                    isQueryInProgress = false;
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
                                intent.putExtra("bill_number", finalBillNumber);
                                intent.putExtra("total_quantity", totalQuantity);
                                startActivity(intent);

                                // 跳转后清空输入框，为下次输入做准备
                                etBillNumber.setText("");
                            } else {
                                // 没有明细数据，播报错误语音
                                playSound(R.raw.error_sound2);
                                Toast.makeText(DoReviewBillActivity.this,
                                        "单号不存在", Toast.LENGTH_LONG).show();
                                // 不清空输入框，只聚焦
                                etBillNumber.requestFocus();
                            }
                        } else {
                            // 单据不存在或其他错误，播报错误语音
                            playSound(R.raw.error_sound);
                            Toast.makeText(DoReviewBillActivity.this,
                                    response.getString("message"), Toast.LENGTH_LONG).show();
                            // 不清空输入框，只聚焦
                            etBillNumber.requestFocus();
                        }
                    } catch (JSONException e) {
                        isQueryInProgress = false;
                        // 数据解析错误，播报错误语音
                        playSound(R.raw.error_sound);
                        Toast.makeText(DoReviewBillActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                        // 不清空输入框，只聚焦
                        etBillNumber.requestFocus();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isQueryInProgress = false;
                    tvLoading.setVisibility(TextView.GONE);
                    // 网络错误，播报错误语音
                    playSound(R.raw.error_sound);
                    Toast.makeText(DoReviewBillActivity.this, "查询失败: " + error, Toast.LENGTH_LONG).show();
                    // 不清空输入框，只聚焦
                    etBillNumber.requestFocus();
                });
            }
        });
    }

    /**
     * 播放语音文件
     */
    private void playSound(int soundResourceId) {
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
        if (autoQueryRunnable != null) {
            autoQueryHandler.removeCallbacks(autoQueryRunnable);
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        isQueryInProgress = false;
    }
}