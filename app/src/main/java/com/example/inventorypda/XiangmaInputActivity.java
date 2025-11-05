package com.example.inventorypda;

import android.app.AlertDialog;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class XiangmaInputActivity extends AppCompatActivity {

    private EditText etXiangma;
    private Button btnSingleSave, btnBatchSave, btnClearAll, btnScan;
    private TextView tvStatus, tvListCount;
    private ListView lvTempList;
    private RadioGroup rgMode;
    private RadioButton rbAutoSave, rbBatchSave;

    private MediaPlayer duplicatePlayer;
    private List<Map<String, Object>> tempList;
    private SimpleAdapter adapter;
    private Handler autoSaveHandler = new Handler();
    private Runnable autoSaveRunnable;

    private ApiClient apiClient;
    private static final String TAG = "XiangmaInput";

    // 延迟时间设为400毫秒
    private static final int AUTO_SAVE_DELAY = 400;

    // 分公司相关变量
    private String selectedBranch = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xiangma_input);

        // 初始化 ApiClient
        apiClient = ApiClient.getInstance(this);

        initViews();
        setupListeners();
        initMediaPlayer();
        initTempList();

        updateModeUI(true);
    }

    private void initViews() {
        etXiangma = findViewById(R.id.etXiangma);
        btnSingleSave = findViewById(R.id.btnSingleSave);
        btnBatchSave = findViewById(R.id.btnBatchSave);
        btnClearAll = findViewById(R.id.btnClearAll);
        btnScan = findViewById(R.id.btnScan);
        tvStatus = findViewById(R.id.tvStatus);
        tvListCount = findViewById(R.id.tvListCount);
        lvTempList = findViewById(R.id.lvTempList);
        rgMode = findViewById(R.id.rgMode);
        rbAutoSave = findViewById(R.id.rbAutoSave);
        rbBatchSave = findViewById(R.id.rbBatchSave);
    }

    private void setupListeners() {
        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isAutoMode = checkedId == R.id.rbAutoSave;
            updateModeUI(isAutoMode);
        });

        btnSingleSave.setOnClickListener(v -> {
            String xiangma = etXiangma.getText().toString().trim();
            if (!TextUtils.isEmpty(xiangma)) {
                showBranchSelectionDialog(xiangma, true);
            } else {
                showStatus("请输入箱唛", false);
            }
        });

        btnBatchSave.setOnClickListener(v -> saveBatchXiangma());
        btnClearAll.setOnClickListener(v -> clearTempList());
        btnScan.setOnClickListener(v -> startBarcodeScan());

        // 自动保存监听 - 延迟改为400毫秒
        etXiangma.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String text = s.toString().trim();
                if (!TextUtils.isEmpty(text)) {
                    if (autoSaveRunnable != null) {
                        autoSaveHandler.removeCallbacks(autoSaveRunnable);
                    }

                    autoSaveRunnable = () -> {
                        String currentText = etXiangma.getText().toString().trim();
                        if (!TextUtils.isEmpty(currentText)) {
                            // 自动保存模式下，直接弹出分公司选择
                            if (rbAutoSave.isChecked()) {
                                showBranchSelectionDialog(currentText, true);
                            } else {
                                // 批量保存模式下，弹出分公司选择添加到列表
                                showBranchSelectionDialog(currentText, false);
                            }
                        }
                    };
                    // 使用400毫秒延迟
                    autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY);
                }
            }
        });
    }

    /**
     * 显示分公司选择对话框
     */
    private void showBranchSelectionDialog(String xiangma, boolean isAutoSave) {
        selectedBranch = ""; // 重置选择

        String[] branches = {"新加坡", "马来西亚", "柬埔寨", "越南", "泰缅", "印尼"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择分公司 - 箱唛: " + xiangma);

        builder.setSingleChoiceItems(branches, -1, (dialog, which) -> {
            selectedBranch = branches[which];
        });

        builder.setPositiveButton("确认", (dialog, which) -> {
            if (!selectedBranch.isEmpty()) {
                if (isAutoSave) {
                    // 自动保存模式：直接保存到服务器
                    saveToServerWithBranch(xiangma, selectedBranch);
                } else {
                    // 批量保存模式：添加到暂存列表
                    addToTempList(xiangma, selectedBranch, "待保存");
                    etXiangma.setText("");
                    etXiangma.requestFocus();
                    showStatus("已自动添加到列表，共 " + tempList.size() + " 条待保存", true);
                }
            } else {
                Toast.makeText(this, "请选择分公司", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateModeUI(boolean isAutoMode) {
        if (isAutoMode) {
            tvStatus.setText("状态：自动保存模式 - 扫描后0.4秒自动保存");
            tvStatus.setBackgroundColor(0xFFE8F5E8);
            tvStatus.setTextColor(0xFF2E7D32);
            btnSingleSave.setText("立即保存");
        } else {
            tvStatus.setText("状态：批量保存模式 - 扫描后0.4秒自动添加到列表");
            tvStatus.setBackgroundColor(0xFFFFF3E0);
            tvStatus.setTextColor(0xFFEF6C00);
            btnSingleSave.setText("添加列表");
        }
    }

    private void initMediaPlayer() {
        try {
            int soundResId = getResources().getIdentifier("duplicate_alert", "raw", getPackageName());
            if (soundResId != 0) {
                duplicatePlayer = MediaPlayer.create(this, soundResId);
            } else {
                Log.w(TAG, "语音文件 duplicate_alert 未找到");
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化语音播放器失败", e);
        }
    }

    private void initTempList() {
        tempList = new ArrayList<>();
        adapter = new SimpleAdapter(this, tempList,
                R.layout.list_item_xiangma,
                new String[]{"xiangma", "branch", "time", "status"},
                new int[]{R.id.tvXiangma, R.id.tvBranch, R.id.tvTime, R.id.tvStatus}) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Map<String, Object> item = tempList.get(position);

                // 显示分公司信息
                TextView tvBranch = view.findViewById(R.id.tvBranch);
                String branch = (String) item.get("branch");
                tvBranch.setText("分公司: " + (branch != null ? branch : "未选择"));

                Button btnDelete = view.findViewById(R.id.btnDelete);
                btnDelete.setOnClickListener(v -> {
                    tempList.remove(position);
                    updateListCount();
                    adapter.notifyDataSetChanged();
                    showStatus("已从列表中删除", true);
                });

                return view;
            }
        };

        lvTempList.setAdapter(adapter);
        updateListCount();
    }

    private void addToTempList(String xiangma, String branch, String status) {
        // 检查是否已存在（相同的箱唛和分公司）
        for (Map<String, Object> existing : tempList) {
            if (xiangma.equals(existing.get("xiangma")) && branch.equals(existing.get("branch"))) {
                showStatus("箱唛已存在于列表中（相同分公司）", false);
                return;
            }
        }

        Map<String, Object> item = new HashMap<>();
        item.put("xiangma", xiangma);
        item.put("branch", branch);
        item.put("time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
        item.put("status", status);
        item.put("saved", status.equals("保存成功"));

        tempList.add(0, item);
        updateListCount();
        adapter.notifyDataSetChanged();
    }

    /**
     * 带分公司参数的保存到服务器
     */
    private void saveToServerWithBranch(String xiangma, String branch) {
        showStatus("保存中...", true);

        apiClient.saveXiangmaWithBranch(xiangma, branch, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                Log.d(TAG, "服务器响应: " + response.toString());
                try {
                    boolean success = response.getBoolean("success");

                    if (success) {
                        showStatus("箱唛保存成功: " + xiangma + " (" + branch + ")", true);
                        addToTempList(xiangma, branch, "保存成功");
                        etXiangma.setText("");
                        etXiangma.requestFocus();
                    } else {
                        String error = response.getString("error");
                        showStatus("保存失败: " + error, false);

                        if (error.contains("重复")) {
                            playDuplicateAlert();
                        }
                        addToTempList(xiangma, branch, "保存失败: " + error);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "解析响应JSON失败", e);
                    showStatus("数据解析错误", false);
                    addToTempList(xiangma, branch, "解析错误");
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "网络请求失败: " + error);
                showStatus("网络错误: " + error, false);
                addToTempList(xiangma, branch, "网络错误");
            }
        });
    }

    private void saveBatchXiangma() {
        if (tempList.isEmpty()) {
            showStatus("暂存列表为空", false);
            return;
        }

        List<Map<String, Object>> unsavedList = new ArrayList<>();
        for (Map<String, Object> item : tempList) {
            if (!(Boolean) item.get("saved")) {
                unsavedList.add(item);
            }
        }

        if (unsavedList.isEmpty()) {
            showStatus("没有需要保存的记录", true);
            return;
        }

        showStatus("开始批量保存 " + unsavedList.size() + " 条记录...", true);
        saveBatchItems(unsavedList, 0);
    }

    private void saveBatchItems(List<Map<String, Object>> items, int index) {
        if (index >= items.size()) {
            showStatus("批量保存完成", true);
            return;
        }

        Map<String, Object> item = items.get(index);
        String xiangma = (String) item.get("xiangma");
        String branch = (String) item.get("branch");
        saveBatchItemToServer(xiangma, branch, items, index);
    }

    private void saveBatchItemToServer(String xiangma, String branch, List<Map<String, Object>> items, int index) {
        apiClient.saveXiangmaWithBranch(xiangma, branch, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    boolean success = response.getBoolean("success");

                    if (success) {
                        updateItemStatus(xiangma, branch, "保存成功");
                    } else {
                        String error = response.getString("error");
                        if (error.contains("重复")) {
                            updateItemStatus(xiangma, branch, "重复失败");
                            playDuplicateAlert();
                        } else {
                            updateItemStatus(xiangma, branch, "保存失败: " + error);
                        }
                    }
                } catch (JSONException e) {
                    updateItemStatus(xiangma, branch, "解析错误");
                }

                saveBatchItems(items, index + 1);
            }

            @Override
            public void onError(String error) {
                updateItemStatus(xiangma, branch, "网络错误");
                saveBatchItems(items, index + 1);
            }
        });
    }

    private void updateItemStatus(String xiangma, String branch, String status) {
        for (Map<String, Object> item : tempList) {
            if (xiangma.equals(item.get("xiangma")) && branch.equals(item.get("branch"))) {
                item.put("status", status);
                item.put("saved", status.equals("保存成功"));
                break;
            }
        }
        adapter.notifyDataSetChanged();
        updateListCount();
    }

    private void updateListCount() {
        int total = tempList.size();
        int saved = 0;
        for (Map<String, Object> item : tempList) {
            if ((Boolean) item.get("saved")) {
                saved++;
            }
        }
        tvListCount.setText("共 " + total + " 条记录 (已保存: " + saved + "，待保存: " + (total - saved) + ")");
    }

    private void clearTempList() {
        List<Map<String, Object>> toRemove = new ArrayList<>();
        for (Map<String, Object> item : tempList) {
            if (!(Boolean) item.get("saved")) {
                toRemove.add(item);
            }
        }

        tempList.removeAll(toRemove);
        updateListCount();
        adapter.notifyDataSetChanged();
        showStatus("已清空未保存的记录", true);
    }

    private void playDuplicateAlert() {
        if (duplicatePlayer != null) {
            try {
                if (duplicatePlayer.isPlaying()) {
                    duplicatePlayer.stop();
                }
                duplicatePlayer.seekTo(0);
                duplicatePlayer.start();
            } catch (Exception e) {
                Log.e(TAG, "播放语音失败", e);
            }
        }
    }

    private void showStatus(String message, boolean isSuccess) {
        runOnUiThread(() -> {
            tvStatus.setText("状态：" + message);
            if (isSuccess) {
                tvStatus.setBackgroundColor(0xFFE8F5E8);
                tvStatus.setTextColor(0xFF2E7D32);
            } else {
                tvStatus.setBackgroundColor(0xFFFFEBEE);
                tvStatus.setTextColor(0xFFC62828);
            }
        });
    }

    private void startBarcodeScan() {
        Toast.makeText(this, "请使用扫码枪扫描箱唛", Toast.LENGTH_SHORT).show();
        etXiangma.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
        if (duplicatePlayer != null) {
            duplicatePlayer.release();
            duplicatePlayer = null;
        }
    }
}