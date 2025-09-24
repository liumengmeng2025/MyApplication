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

    // UI组件
    private Button btnRefreshDetails;
    private Button btnUpdateData;
    private ListView lvCabinetDetails;
    private EditText etBillNo;  // 改为提单号输入框
    private EditText etCabinetFlow;  // 装柜流水扫描框
    private TextView tvResult;

    // 数据与工具
    private ApiClient apiClient;
    private List<CabinetDetail> cabinetDetailList;
    private CabinetDetailAdapter detailAdapter;
    private MyHandler handler;
    private MediaPlayer mediaPlayerError;  // 错误提示音
    private MediaPlayer mediaPlayerSuccess; // 成功提示音
    private long lastInputTime = 0;
    private static final long AUTO_SUBMIT_DELAY = 1000; // 恢复为1秒
    private static final int MSG_SUBMIT = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cabinet_flow);

        initViews();
        initData();
        initMediaPlayers();  // 改为初始化多个播放器
        handler = new MyHandler(this);
        setupListeners();
        loadCabinetDetails();
    }

    private void initViews() {
        btnRefreshDetails = findViewById(R.id.btnRefreshDetails);
        btnUpdateData = findViewById(R.id.btnUpdateData);
        lvCabinetDetails = findViewById(R.id.lvCabinetDetails);
        etBillNo = findViewById(R.id.etBillNo);  // 提单号输入框
        etCabinetFlow = findViewById(R.id.etCabinetFlow);  // 装柜流水扫描框
        tvResult = findViewById(R.id.tvResult);
        apiClient = ApiClient.getInstance(this);
    }

    private void initData() {
        cabinetDetailList = new ArrayList<>();
        detailAdapter = new CabinetDetailAdapter();
        lvCabinetDetails.setAdapter(detailAdapter);
    }

    private void initMediaPlayers() {
        try {
            // 初始化错误提示音
            mediaPlayerError = MediaPlayer.create(this, R.raw.sound_no_serial);
            if (mediaPlayerError != null) {
                mediaPlayerError.setLooping(false);
            }

            // 初始化成功提示音
            mediaPlayerSuccess = MediaPlayer.create(this, R.raw.scan_success_sound);
            if (mediaPlayerSuccess != null) {
                mediaPlayerSuccess.setLooping(false);
            }

            Log.d("MediaPlayer", "语音播放器初始化成功");
        } catch (Exception e) {
            Log.e("MediaPlayer", "初始化异常：" + e.getMessage(), e);
            mediaPlayerError = null;
            mediaPlayerSuccess = null;
        }
    }

    private static class MyHandler extends Handler {
        private WeakReference<CabinetFlowActivity> activityRef;

        public MyHandler(CabinetFlowActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            CabinetFlowActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (msg.what == MSG_SUBMIT) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - activity.lastInputTime >= AUTO_SUBMIT_DELAY - 100) {
                    activity.submitCabinetFlow();
                }
            }
        }
    }

    private void setupListeners() {
        // 刷新按钮 - 刷新装柜明细列表
        btnRefreshDetails.setOnClickListener(v -> {
            if (isActivityAlive()) loadCabinetDetails();
        });

        // 更新数据按钮 - 根据提单号导入商品货号
        btnUpdateData.setOnClickListener(v -> {
            if (isActivityAlive()) importByBillNo();
        });

        // 装柜流水扫描框 - 1秒无输入自动提交
        etCabinetFlow.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isActivityAlive()) return;

                if (handler != null) {
                    handler.removeMessages(MSG_SUBMIT);
                }

                lastInputTime = System.currentTimeMillis();
                if (handler != null) {
                    handler.sendEmptyMessageDelayed(MSG_SUBMIT, AUTO_SUBMIT_DELAY);
                }
            }
        });
    }

    // 新增方法：根据提单号导入商品货号
    private void importByBillNo() {
        if (!isActivityAlive()) return;

        String billNo = etBillNo.getText().toString().trim();
        if (billNo.isEmpty()) {
            showResult("请输入提单号", Color.RED);
            return;
        }

        showResult("正在导入数据...", Color.GRAY);

        Map<String, String> params = new HashMap<>();
        params.put("提单号", billNo);

        apiClient.postRequest("/cabinet/importByBillNo", params, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isActivityAlive()) return;

                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            String message = response.getString("message");
                            showResult(message, Color.GREEN);
                            // 导入成功后刷新明细列表
                            loadCabinetDetails();
                        } else {
                            String errorMsg = response.optString("message", "导入失败");
                            showResult("导入失败：" + errorMsg, Color.RED);
                        }
                    } catch (JSONException e) {
                        showResult("导入结果解析错误", Color.RED);
                        Log.e("ImportByBillNo", "JSON解析异常：" + e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (isActivityAlive()) {
                    runOnUiThread(() -> showResult("导入失败：" + error, Color.RED));
                }
            }
        });
    }

    // 加载装柜明细列表（不变）
    private void loadCabinetDetails() {
        if (!isActivityAlive()) return;
        showResult("正在加载装柜明细...", Color.GRAY);

        apiClient.getRequest("/cabinet/getCabinetDetails", new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isActivityAlive()) return;

                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray data = response.getJSONArray("data");
                            cabinetDetailList.clear();

                            int filledCount = 0;
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject item = data.getJSONObject(i);
                                String serialNo = item.getString("流水号");

                                String cabinetFlow;
                                if (item.isNull("装柜流水")) {
                                    cabinetFlow = null;
                                } else {
                                    cabinetFlow = item.optString("装柜流水", "");
                                }

                                if (cabinetFlow != null && !cabinetFlow.trim().isEmpty()) {
                                    filledCount++;
                                }

                                cabinetDetailList.add(new CabinetDetail(serialNo, cabinetFlow));
                            }

                            detailAdapter.notifyDataSetChanged();

                            String resultMsg = "加载成功，共" + cabinetDetailList.size() + "条";
                            if (filledCount > 0) {
                                resultMsg += "，已录入" + filledCount + "条";
                            }
                            showResult(resultMsg, Color.GREEN);

                        } else {
                            String errorMsg = response.optString("message", "加载失败");
                            showResult("加载失败：" + errorMsg, Color.RED);
                        }
                    } catch (JSONException e) {
                        showResult("明细解析错误", Color.RED);
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

    // 提交装柜流水（添加成功语音播放）
    private void submitCabinetFlow() {
        if (!isActivityAlive()) return;

        String cabinetFlow = etCabinetFlow.getText().toString().trim();
        if (cabinetFlow.isEmpty()) {
            showResult("请扫描装柜流水", Color.RED);
            return;
        }

        // 提取流水号（现在是商品货号）
        String extractedSerialNo = extractSerialNoFromCabinetFlow(cabinetFlow);
        if (extractedSerialNo.isEmpty()) {
            showResult("装柜流水格式错误，无法提取商品货号", Color.RED);
            return;
        }

        if (!isSerialNoExists(extractedSerialNo)) {
            showResult("商品货号不存在，请先更新数据", Color.RED);
            playSound(mediaPlayerError);  // 播放错误提示音
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("流水号", extractedSerialNo);
        params.put("装柜流水", cabinetFlow);

        apiClient.postRequest("/cabinet/updateCabinetFlow", params, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isActivityAlive()) return;

                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            showResult("写入成功！装柜流水：" + cabinetFlow, Color.GREEN);
                            if (etCabinetFlow != null) etCabinetFlow.setText("");

                            // 播放成功提示音
                            playSound(mediaPlayerSuccess);

                            // 刷新明细列表
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

    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private String extractSerialNoFromCabinetFlow(String cabinetFlow) {
        if (cabinetFlow == null || cabinetFlow.trim().isEmpty()) {
            return "";
        }
        String trimmedFlow = cabinetFlow.trim();
        String[] parts = trimmedFlow.split("-");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (!trimmedPart.isEmpty()) {
                return trimmedPart;
            }
        }
        return trimmedFlow;
    }

    private boolean isSerialNoExists(String serialNo) {
        for (CabinetDetail detail : cabinetDetailList) {
            if (detail.getSerialNo().equals(serialNo)) {
                return true;
            }
        }
        return false;
    }

    // 通用的声音播放方法
    private void playSound(MediaPlayer mediaPlayer) {
        if (isActivityAlive() && mediaPlayer != null) {
            try {
                // 如果正在播放，先重置
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.seekTo(0);
                } else {
                    mediaPlayer.start();
                }
            } catch (Exception e) {
                Log.e("MediaPlayer", "播放失败：" + e.getMessage(), e);
            }
        }
    }

    // 保留原有的错误声音播放方法（兼容性）
    private void playNoSerialSound() {
        playSound(mediaPlayerError);
    }

    private void showResult(String message, int color) {
        if (isActivityAlive() && tvResult != null) {
            tvResult.setText(message);
            tvResult.setTextColor(0xFFFF0000); // 强制红色
        }
    }

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
            holder.tvSerialNo.setText("箱唛：" + detail.getSerialNo());
            holder.tvCabinetFlow.setText("装柜流水：" + detail.getCabinetFlow());

            return convertView;
        }

        class ViewHolder {
            TextView tvSerialNo;
            TextView tvCabinetFlow;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除Handler所有任务
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }

        // 释放语音播放器
        if (mediaPlayerError != null) {
            mediaPlayerError.release();
            mediaPlayerError = null;
        }
        if (mediaPlayerSuccess != null) {
            mediaPlayerSuccess.release();
            mediaPlayerSuccess = null;
        }

        // 清空数据
        if (cabinetDetailList != null) {
            cabinetDetailList.clear();
            cabinetDetailList = null;
        }
    }
}