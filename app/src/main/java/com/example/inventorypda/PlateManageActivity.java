package com.example.inventorypda;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class PlateManageActivity extends AppCompatActivity {

    private EditText etPlate, etQuantity;
    private TextView tvBranch, tvCategory;
    private Button btnScanPlate, btnSave, btnBack, btnSelectCategory;

    private String currentPlate = "";
    private String currentBranch = "";
    private String selectedCategory = "";
    private MediaPlayer mediaPlayer;

    private Handler scanHandler = new Handler();
    private static final int SCAN_DELAY = 1000; // 1秒延迟

    // 新增变量：标记是否正在处理扫描
    private boolean isProcessingScan = false;
    // 保存TextWatcher引用
    private TextWatcher plateTextWatcher;

    // 类别选项
    private final String[] categories = {"货品", "玩具", "毛绒", "整件", "敏货", "优先"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plate_manage);

        initViews();
        setupEventListeners();
    }

    private void initViews() {
        etPlate = findViewById(R.id.etPlate);
        etQuantity = findViewById(R.id.etQuantity);
        tvBranch = findViewById(R.id.tvBranch);
        tvCategory = findViewById(R.id.tvCategory);
        btnScanPlate = findViewById(R.id.btnScanPlate);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);
        btnSelectCategory = findViewById(R.id.btnSelectCategory);

        // 初始化显示
        tvBranch.setText("未知");
        tvCategory.setText("请选择类别");

        // 设置板标输入框的KeyListener，拦截回车键
        etPlate.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // 拦截回车键，防止扫描枪的换行符触发默认行为
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    // 直接处理扫描数据，不进行换行
                    processScanData();
                    return true; // 消费事件，防止默认行为
                }
                return false;
            }
        });
    }

    private void setupEventListeners() {
        // 创建TextWatcher并保存引用
        plateTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String plate = s.toString().trim();

                // 如果正在处理扫描，则不重复处理
                if (isProcessingScan) {
                    return;
                }

                // 检查是否包含扫描枪常见的结束符（回车、换行、制表符等）
                if (!TextUtils.isEmpty(plate) && containsScanTerminator(plate)) {
                    // 标记正在处理扫描
                    isProcessingScan = true;
                    // 立即处理扫描数据
                    processScanData();
                } else if (!TextUtils.isEmpty(plate) && !plate.equals(currentPlate)) {
                    // 普通手动输入，使用延迟查询
                    scanHandler.removeCallbacksAndMessages(null);
                    scanHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            queryPlateInfo(plate);
                        }
                    }, SCAN_DELAY);
                }
            }
        };

        // 设置文本变化监听
        etPlate.addTextChangedListener(plateTextWatcher);

        // 扫描按钮 - 实际项目中这里应该调用扫码功能
        btnScanPlate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 实际项目中这里应该打开扫码界面
                // 这里暂时保持输入框为空，让用户手动输入或使用外部扫码
                Toast.makeText(PlateManageActivity.this, "请使用扫码枪扫描板标", Toast.LENGTH_SHORT).show();
                etPlate.requestFocus();
            }
        });

        // 类别选择按钮
        btnSelectCategory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCategoryDialog();
            }
        });

        // 保存按钮
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePlateInfo();
            }
        });

        // 返回按钮
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /**
     * 处理扫描数据
     * 清理扫描数据中的终止符并立即查询
     */
    private void processScanData() {
        String rawPlate = etPlate.getText().toString();

        // 清理扫描数据：移除所有空白字符和常见终止符
        String cleanPlate = cleanScanData(rawPlate);

        if (!TextUtils.isEmpty(cleanPlate)) {
            // 移除文本变化监听，避免循环处理
            etPlate.removeTextChangedListener(plateTextWatcher);

            // 更新输入框文本为清理后的数据
            etPlate.setText(cleanPlate);
            etPlate.setSelection(cleanPlate.length()); // 将光标移到末尾

            // 重新添加文本变化监听
            etPlate.addTextChangedListener(plateTextWatcher);

            // 立即查询，不延迟
            queryPlateInfo(cleanPlate);
        }

        // 重置处理标志（使用延迟确保处理完成）
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                isProcessingScan = false;
            }
        }, 500);
    }

    /**
     * 清理扫描数据，移除终止符
     */
    private String cleanScanData(String rawData) {
        if (TextUtils.isEmpty(rawData)) {
            return rawData;
        }

        // 移除常见的扫描终止符：换行、回车、制表符等
        return rawData.replaceAll("[\\r\\n\\t]", "").trim();
    }

    /**
     * 检查是否包含扫描终止符
     */
    private boolean containsScanTerminator(String data) {
        return data.contains("\n") || data.contains("\r") || data.contains("\t");
    }

    private void queryPlateInfo(String plate) {
        if (TextUtils.isEmpty(plate)) {
            return;
        }

        Log.d("PlateManage", "查询板标: " + plate);
        currentPlate = plate; // 保存当前输入的板标

        ApiClient.getInstance(this).queryPlate(plate, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                try {
                    if (response.getBoolean("success")) {
                        org.json.JSONObject plateData = response.getJSONObject("data");
                        currentBranch = plateData.getString("分公司");
                        tvBranch.setText(currentBranch);

                        // 显示已有的类别和件数（如果有）
                        if (plateData.has("类别") && !plateData.isNull("类别")) {
                            selectedCategory = plateData.getString("类别");
                            tvCategory.setText(selectedCategory);
                        } else {
                            // 如果没有类别，先播放选择类别语音，然后弹出类别选择窗口
                            playVoice("select_category");
                            // 延迟一下再弹出对话框，让语音播放完成
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    showCategoryDialog();
                                }
                            }, 1000);
                        }

                        if (plateData.has("件数") && !plateData.isNull("件数")) {
                            etQuantity.setText(String.valueOf(plateData.getInt("件数")));
                        }

                        Toast.makeText(PlateManageActivity.this, "板标查询成功", Toast.LENGTH_SHORT).show();
                    } else {
                        // 板标不存在
                        String errorMsg = response.getString("message");
                        Toast.makeText(PlateManageActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                        playVoice("not_exist");
                        resetBranchInfo();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(PlateManageActivity.this, "解析板标信息失败", Toast.LENGTH_SHORT).show();
                    resetBranchInfo();
                }
            }

            @Override
            public void onError(String error) {
                Log.e("PlateManage", "查询板标失败: " + error);
                Toast.makeText(PlateManageActivity.this, "查询失败: " + error, Toast.LENGTH_SHORT).show();
                playVoice("not_exist");
                resetBranchInfo();
            }
        });
    }

    private void resetBranchInfo() {
        currentBranch = "";
        tvBranch.setText("未知");
        selectedCategory = "";
        tvCategory.setText("请选择类别");
        etQuantity.setText("");
    }

    private void showCategoryDialog() {
        // 获取当前选中的位置
        int checkedItem = -1;
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equals(selectedCategory)) {
                checkedItem = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择类别")
                .setSingleChoiceItems(categories, checkedItem, (dialog, which) -> {
                    selectedCategory = categories[which];
                    tvCategory.setText(selectedCategory);
                    playCategoryVoice(selectedCategory);
                    dialog.dismiss(); // 选择后自动关闭对话框
                })
                .setCancelable(true)
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    private void playCategoryVoice(String category) {
        playVoice(getVoiceResourceName(category));
    }

    private String getVoiceResourceName(String category) {
        switch (category) {
            case "货品":
                return "huopin";
            case "玩具":
                return "wanju";
            case "毛绒":
                return "maorong";
            case "整件":
                return "zhengjian";
            case "敏货":
                return "minhuo";
            case "优先":
                return "youxian";
            default:
                return "huopin";
        }
    }

    private void playVoice(String voiceName) {
        // 释放之前的播放器
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        try {
            // 获取资源ID
            int resId = getResources().getIdentifier(voiceName, "raw", getPackageName());
            if (resId != 0) {
                mediaPlayer = MediaPlayer.create(this, resId);
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                } else {
                    Log.e("PlateManage", "无法创建MediaPlayer for: " + voiceName);
                    // 播放默认提示音
                    playDefaultSound();
                }
            } else {
                Log.e("PlateManage", "未找到语音文件: " + voiceName);
                playDefaultSound();
            }
        } catch (Exception e) {
            Log.e("PlateManage", "播放语音失败: " + e.getMessage());
            playDefaultSound();
        }
    }

    private void playDefaultSound() {
        try {
            mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            if (mediaPlayer != null) {
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void savePlateInfo() {
        String plate = etPlate.getText().toString().trim();
        String quantityStr = etQuantity.getText().toString().trim();

        if (TextUtils.isEmpty(plate)) {
            Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(currentBranch) || "未知".equals(tvBranch.getText().toString())) {
            Toast.makeText(this, "板标不存在，无法保存", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(selectedCategory) || "请选择类别".equals(tvCategory.getText().toString())) {
            Toast.makeText(this, "请选择类别", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(quantityStr)) {
            Toast.makeText(this, "请输入件数", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0) {
                Toast.makeText(this, "件数必须大于0", Toast.LENGTH_SHORT).show();
                return;
            }

            // 调用API保存板标信息
            ApiClient.getInstance(this).updatePlate(plate, currentBranch, selectedCategory, quantity,
                    new ApiClient.ApiResponseListener() {
                        @Override
                        public void onSuccess(org.json.JSONObject response) {
                            try {
                                if (response.getBoolean("success")) {
                                    // 保存成功后播放成功语音
                                    playVoice("save_success_sound");
                                    Toast.makeText(PlateManageActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                                    resetForm();
                                } else {
                                    String errorMsg = response.getString("message");
                                    Toast.makeText(PlateManageActivity.this, "保存失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(PlateManageActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(PlateManageActivity.this, "保存失败: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });

        } catch (NumberFormatException e) {
            Toast.makeText(this, "件数必须为数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetForm() {
        etPlate.setText("");
        etQuantity.setText("");
        tvBranch.setText("未知");
        tvCategory.setText("请选择类别");
        currentPlate = "";
        currentBranch = "";
        selectedCategory = "";
        isProcessingScan = false; // 重置扫描处理标志
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        scanHandler.removeCallbacksAndMessages(null);
    }
}