package com.example.inventorypda;

import java.util.Map;
import java.util.HashMap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class CabinetFlowActivity extends AppCompatActivity {

    // 1. UI组件
    private Button btnRefreshDetails;
    private ListView lvCabinetDetails;
    private EditText etCabinetFlow;
    private TextView tvResult;

    // 2. 数据与工具（Handler改为静态弱引用，避免内存泄漏）
    private ApiClient apiClient;
    private List<CabinetDetail> cabinetDetailList;
    private CabinetDetailAdapter detailAdapter;
    private MyHandler handler; // 自定义弱引用Handler
    private MediaPlayer mediaPlayer;
    private long lastInputTime = 0;
    private static final long AUTO_SUBMIT_DELAY = 1000;
    private static final int MSG_SUBMIT = 1001; // 提交任务的消息标识

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cabinet_flow);

        initViews();
        initData();
        initMediaPlayer();
        handler = new MyHandler(this); // 初始化弱引用Handler
        setupListeners();
        loadCabinetDetails();
    }

    // -------------------------- 1. 初始化相关 --------------------------
    private void initViews() {
        btnRefreshDetails = findViewById(R.id.btnRefreshDetails);
        lvCabinetDetails = findViewById(R.id.lvCabinetDetails);
        etCabinetFlow = findViewById(R.id.etCabinetFlow);
        tvResult = findViewById(R.id.tvResult);
        apiClient = ApiClient.getInstance(this);
    }

    private void initData() {
        cabinetDetailList = new ArrayList<>();
        detailAdapter = new CabinetDetailAdapter();
        lvCabinetDetails.setAdapter(detailAdapter);
    }

    private void initMediaPlayer() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.sound_no_serial);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(false);
                Log.d("MediaPlayer", "语音播放器初始化成功");
            } else {
                Log.e("MediaPlayer", "语音播放器初始化失败（文件缺失或格式错误）");
            }
        } catch (Exception e) {
            Log.e("MediaPlayer", "初始化异常：" + e.getMessage(), e);
            mediaPlayer = null;
        }
    }

    // -------------------------- 2. 关键修复：静态弱引用Handler（避免内存泄漏） --------------------------
    // 静态内部类不持有外部Activity的强引用，通过WeakReference持有，防止内存泄漏
    private static class MyHandler extends Handler {
        private WeakReference<CabinetFlowActivity> activityRef;

        public MyHandler(CabinetFlowActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            CabinetFlowActivity activity = activityRef.get();
            // 1. 判断Activity是否存活（避免销毁后执行）
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                Log.d("MyHandler", "Activity已销毁，取消提交任务");
                return;
            }
            // 2. 处理提交任务
            if (msg.what == MSG_SUBMIT) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - activity.lastInputTime >= AUTO_SUBMIT_DELAY - 100) {
                    activity.submitCabinetFlow();
                }
            }
        }
    }

    // -------------------------- 3. 事件绑定（优化自动提交逻辑） --------------------------
    private void setupListeners() {
        // 刷新按钮
        btnRefreshDetails.setOnClickListener(v -> {
            if (isActivityAlive()) loadCabinetDetails();
        });

        // 装柜流水扫描框：1秒无输入自动提交（用Message代替直接postRunnable）
        etCabinetFlow.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isActivityAlive()) return;

                // 移除之前的提交任务
                if (handler != null) {
                    handler.removeMessages(MSG_SUBMIT);
                }

                // 更新最后输入时间，发送新的延迟任务
                lastInputTime = System.currentTimeMillis();
                if (handler != null) {
                    handler.sendEmptyMessageDelayed(MSG_SUBMIT, AUTO_SUBMIT_DELAY);
                }
            }
        });
    }

    // -------------------------- 4. 核心1：加载装柜明细（添加Activity存活判断） --------------------------
    private void loadCabinetDetails() {
        if (!isActivityAlive()) return;
        showResult("正在加载已有明细...", Color.GRAY);

        apiClient.getRequest("/cabinet/getCabinetDetails", new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                // 先判断Activity是否存活，再执行UI操作
                if (!isActivityAlive()) return;

                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray data = response.getJSONArray("data");
                            cabinetDetailList.clear();

                            // 解析后端中文字段"流水号""装柜流水"
                            int filledCount = 0; // 统计装柜流水不为空的数量

                            for (int i = 0; i < data.length(); i++) {
                                JSONObject item = data.getJSONObject(i);
                                String serialNo = item.getString("流水号");

                                // 正确处理JSON null值
                                String cabinetFlow;
                                if (item.isNull("装柜流水")) {
                                    cabinetFlow = null; // 明确设置为null
                                } else {
                                    cabinetFlow = item.optString("装柜流水", "");
                                }

                                // 调试：打印前5条和后5条记录的装柜流水值
                                if (i < 5 || i >= data.length() - 5) {
                                    Log.d("CabinetDebug", "记录 " + i + ": 流水号=" + serialNo + ", 装柜流水='" + cabinetFlow + "'");
                                }

                                // 统计装柜流水不为空的数量
                                if (cabinetFlow != null && !cabinetFlow.trim().isEmpty()) {
                                    filledCount++;
                                } else {
                                    // 调试：打印空值的记录
                                    if (i < 5 || i >= data.length() - 5) {
                                        Log.d("CabinetDebug", "空装柜流水记录: 流水号=" + serialNo);
                                    }
                                }

                                cabinetDetailList.add(new CabinetDetail(serialNo, cabinetFlow));
                            }

                            detailAdapter.notifyDataSetChanged();

                            // 显示统计信息
                            String resultMsg = "加载成功，共" + cabinetDetailList.size() + "条";
                            if (filledCount > 0) {
                                resultMsg += "，已录入" + filledCount + "条";
                            }
                            showResult(resultMsg, Color.GREEN);

                            // 调试：打印最终统计结果
                            Log.d("CabinetDebug", "总记录数: " + cabinetDetailList.size() + ", 装柜流水不为空: " + filledCount);
                        } else {
                            String errorMsg = response.optString("message", "加载失败");
                            showResult("加载失败：" + errorMsg, Color.RED);
                        }
                    } catch (JSONException e) {
                        showResult("明细解析错误（字段名不匹配）", Color.RED);
                        Log.e("LoadDetails", "JSON解析异常：" + e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (isActivityAlive()) {
                    runOnUiThread(() -> showResult("加载失败：" + error, Color.RED));
                }
            }
        });
    }

    // -------------------------- 5. 核心2：提交装柜流水（优化存活判断和流水号提取） --------------------------
    private void submitCabinetFlow() {
        if (!isActivityAlive()) return;

        // 1. 获取并校验装柜流水
        String cabinetFlow = etCabinetFlow.getText().toString().trim();
        if (cabinetFlow.isEmpty()) {
            showResult("请扫描装柜流水", Color.RED);
            return;
        }

        // 2. 提取流水号（优化逻辑：兼容无分隔符/空分割的情况）
        String extractedSerialNo = extractSerialNoFromCabinetFlow(cabinetFlow);
        if (extractedSerialNo.isEmpty()) {
            showResult("装柜流水格式错误，无法提取流水号", Color.RED);
            return;
        }

        // 3. 检查流水号是否存在
        if (!isSerialNoExists(extractedSerialNo)) {
            showResult("扫描单号不存在，核对后再扫描", Color.RED);
            playNoSerialSound();
            return;
        }

        // 4. 构造参数（与后端中文字段匹配）
        Map<String, String> params = new HashMap<>();
        params.put("流水号", extractedSerialNo);
        params.put("装柜流水", cabinetFlow);

        // 5. 调用接口提交
        apiClient.postRequest("/cabinet/updateCabinetFlow", params, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isActivityAlive()) return;

                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            showResult("写入成功！装柜流水：" + cabinetFlow, Color.GREEN);
                            // 清空输入框（判断控件是否存活）
                            if (etCabinetFlow != null) etCabinetFlow.setText("");

                            // 刷新明细并显示统计信息
                            loadCabinetDetails();
                        } else {
                            String errorMsg = response.optString("message", "写入失败");
                            showResult("写入失败：" + errorMsg, Color.RED);
                        }
                    } catch (JSONException e) {
                        showResult("写入结果解析错误", Color.RED);
                        Log.e("SubmitFlow", "JSON解析异常：" + e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (isActivityAlive()) {
                    runOnUiThread(() -> showResult("写入失败：" + error, Color.RED));
                }
            }
        });
    }

    // -------------------------- 6. 辅助方法（优化健壮性） --------------------------
    // 新增：判断Activity是否存活（避免销毁后操作UI）
    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    // 优化：提取流水号（兼容多种格式，如"2099286""2099286-CAB2025""-2099286-"）
    private String extractSerialNoFromCabinetFlow(String cabinetFlow) {
        if (cabinetFlow == null || cabinetFlow.trim().isEmpty()) {
            return "";
        }
        String trimmedFlow = cabinetFlow.trim();
        String[] parts = trimmedFlow.split("-");
        // 遍历分割结果，取第一个非空部分
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (!trimmedPart.isEmpty()) {
                return trimmedPart;
            }
        }
        // 若所有分割部分为空，返回完整字符串
        return trimmedFlow;
    }

    // 检查流水号是否存在
    private boolean isSerialNoExists(String serialNo) {
        for (CabinetDetail detail : cabinetDetailList) {
            if (detail.getSerialNo().equals(serialNo)) {
                return true;
            }
        }
        return false;
    }

    // 播放语音（判断播放器和Activity是否存活）
    private void playNoSerialSound() {
        if (isActivityAlive() && mediaPlayer != null && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
            } catch (Exception e) {
                Log.e("MediaPlayer", "播放失败：" + e.getMessage(), e);
            }
        }
    }

    // 显示结果（判断UI控件是否存活）
    private void showResult(String message, int color) {
        if (isActivityAlive() && tvResult != null) {
            tvResult.setText(message);
            tvResult.setTextColor(color);
        }
    }

    // -------------------------- 7. 列表适配器（不变） --------------------------
    private class CabinetDetailAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return cabinetDetailList.size();
        }

        @Override
        public Object getItem(int position) {
            return cabinetDetailList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = View.inflate(CabinetFlowActivity.this, android.R.layout.simple_list_item_2, null);
                holder = new ViewHolder();
                holder.tvSerialNo = convertView.findViewById(android.R.id.text1);
                holder.tvCabinetFlow = convertView.findViewById(android.R.id.text2);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            CabinetDetail detail = cabinetDetailList.get(position);
            holder.tvSerialNo.setText("流水号：" + detail.getSerialNo());
            holder.tvCabinetFlow.setText("装柜流水：" + detail.getCabinetFlow());

            return convertView;
        }

        class ViewHolder {
            TextView tvSerialNo;
            TextView tvCabinetFlow;
        }
    }

    // -------------------------- 8. 资源释放（关键：移除Handler和释放资源） --------------------------
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 1. 移除Handler所有任务，避免内存泄漏
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        // 2. 释放语音播放器
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // 3. 清空列表，释    放内存
        if (cabinetDetailList != null) {
            cabinetDetailList.clear();
            cabinetDetailList = null;
        }
        // 4. 置空UI控件，避免引用
        btnRefreshDetails = null;
        lvCabinetDetails = null;
        etCabinetFlow = null;
        tvResult = null;
    }
}