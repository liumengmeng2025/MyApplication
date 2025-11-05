package com.example.inventorypda;

import java.util.ArrayList;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.media.MediaPlayer;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReceivingActivity extends AppCompatActivity {

    private EditText etPlate, etBarcode;
    private Button btnScanPlate, btnSave, btnClear;
    private ListView lvItems;
    private TextView tvScanCount;
    private List<ReceivingItem> itemList;
    private ReceivingAdapter adapter;
    private String currentPlate = "";
    private String selectedBranch = "";
    private ApiClient apiClient;

    private MediaPlayer plateFixedSound;
    private MediaPlayer scanSuccessSound;
    private MediaPlayer saveSuccessSound;
    private MediaPlayer duplicateSound;
    private MediaPlayer dialogSound;
    private MediaPlayer deleteSuccessSound;
    private MediaPlayer checkExistsSound;
    private MediaPlayer shanchuSuccessSound;

    private Handler handler = new Handler();
    private Runnable scanRunnable;
    private long lastInputTime = 0;
    private static final long SCAN_DELAY = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiving);

        initSounds();
        initViews();
        setupListeners();
    }

    private void initSounds() {
        try {
            plateFixedSound = MediaPlayer.create(this, R.raw.plate_fixed_sound);
            scanSuccessSound = MediaPlayer.create(this, R.raw.scan_success_sound);
            saveSuccessSound = MediaPlayer.create(this, R.raw.save_success_sound);
            duplicateSound = MediaPlayer.create(this, R.raw.duplicate_beep);
            dialogSound = MediaPlayer.create(this, R.raw.dialog_sound);
            deleteSuccessSound = MediaPlayer.create(this, R.raw.delete_success_sound);
            checkExistsSound = MediaPlayer.create(this, R.raw.dialog_sound);
            shanchuSuccessSound = MediaPlayer.create(this, R.raw.shanchu_success);
        } catch (Exception e) {
            Log.e("SoundError", "无法加载声音文件", e);
        }
    }

    private void playSound(MediaPlayer sound) {
        if (sound != null) {
            try {
                if (sound.isPlaying()) {
                    sound.seekTo(0);
                } else {
                    sound.start();
                }
            } catch (Exception e) {
                Log.e("SoundError", "播放声音失败", e);
            }
        }
    }

    private void initViews() {
        etPlate = findViewById(R.id.etPlate);
        etBarcode = findViewById(R.id.etBarcode);
        btnScanPlate = findViewById(R.id.btnScanPlate);
        btnSave = findViewById(R.id.btnSave);
        btnClear = findViewById(R.id.btnClear);
        lvItems = findViewById(R.id.lvItems);
        tvScanCount = findViewById(R.id.tvScanCount);

        apiClient = ApiClient.getInstance(this);
        itemList = new ArrayList<>();
        adapter = new ReceivingAdapter(this, itemList);
        lvItems.setAdapter(adapter);

        updateScanCount();
    }

    private void setupListeners() {
        btnScanPlate.setOnClickListener(v -> {
            String plate = etPlate.getText().toString().trim();
            if (plate.isEmpty()) {
                Toast.makeText(this, "请输入板标", Toast.LENGTH_SHORT).show();
                return;
            }

            currentPlate = plate;
            etPlate.setEnabled(false);
            btnScanPlate.setEnabled(false);
            Toast.makeText(this, "板标已固定: " + plate, Toast.LENGTH_SHORT).show();
            playSound(plateFixedSound);
            loadTempItems();
        });

        btnSave.setOnClickListener(v -> saveReceiving());

        btnClear.setOnClickListener(v -> clearCurrentTemp());

        etBarcode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0 && s.charAt(s.length() - 1) == '\n') {
                    String input = s.toString().replace("\n", "").trim();
                    if (!input.isEmpty()) {
                        etBarcode.setText(input);
                        handler.removeCallbacks(scanRunnable);
                        scanBarcode();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (scanRunnable != null) {
                    handler.removeCallbacks(scanRunnable);
                }

                lastInputTime = System.currentTimeMillis();

                scanRunnable = () -> {
                    if (System.currentTimeMillis() - lastInputTime >= SCAN_DELAY - 100 && s.length() > 0) {
                        scanBarcode();
                    }
                };

                handler.postDelayed(scanRunnable, SCAN_DELAY);
            }
        });

        lvItems.setOnItemLongClickListener((parent, view, position, id) -> {
            ReceivingItem selectedItem = itemList.get(position);
            showDeleteConfirmDialog(selectedItem);
            return true;
        });
    }

    private void showBranchSelectionDialog() {
        selectedBranch = ""; // 重置选择

        String[] branches = {"新加坡", "马来西亚", "柬埔寨", "越南", "泰缅", "印尼"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择分公司");

        builder.setSingleChoiceItems(branches, -1, (dialog, which) -> {
            selectedBranch = branches[which];
        });

        builder.setPositiveButton("确认保存", (dialog, which) -> {
            if (!selectedBranch.isEmpty()) {
                performSaveWithBranch();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer(plateFixedSound);
        releaseMediaPlayer(scanSuccessSound);
        releaseMediaPlayer(saveSuccessSound);
        releaseMediaPlayer(duplicateSound);
        releaseMediaPlayer(dialogSound);
        releaseMediaPlayer(deleteSuccessSound);
        releaseMediaPlayer(checkExistsSound);
        releaseMediaPlayer(shanchuSuccessSound);
    }

    private void releaseMediaPlayer(MediaPlayer mp) {
        if (mp != null) {
            mp.release();
        }
    }

    private void showDeleteConfirmDialog(ReceivingItem item) {
        playSound(shanchuSuccessSound);
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("是否删除商品货号为 " + item.商品货号 + " 的明细记录？")
                .setPositiveButton("确认", (dialog, which) -> deleteTempItem(item))
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void deleteTempItem(ReceivingItem item) {
        Toast.makeText(this, "正在删除...", Toast.LENGTH_SHORT).show();

        apiClient.deleteTempItem(currentPlate, item.商品货号, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            playSound(deleteSuccessSound);
                            Toast.makeText(ReceivingActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                            loadTempItems();
                        } else {
                            String errorMsg = response.optString("message", "删除失败");
                            Toast.makeText(ReceivingActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(ReceivingActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ReceivingActivity.this, "删除失败: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void scanBarcode() {
        String barcode = etBarcode.getText().toString().trim();
        if (barcode.isEmpty()) return;

        if (currentPlate.isEmpty()) {
            Toast.makeText(this, "请先扫描板标", Toast.LENGTH_SHORT).show();
            etBarcode.setText("");
            etBarcode.requestFocus();
            return;
        }

        apiClient.addToTemp(currentPlate, barcode, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    String message = response.getString("message");
                    Toast.makeText(ReceivingActivity.this, message, Toast.LENGTH_SHORT).show();

                    boolean existsInTemp = response.optBoolean("exists_in_temp", false);
                    boolean existsInMain = response.optBoolean("exists_in_main", true);

                    if (existsInTemp) {
                        playSound(duplicateSound);
                    } else if (!existsInMain) {
                        playSound(deleteSuccessSound);
                    } else {
                        playSound(scanSuccessSound);
                    }

                    etBarcode.selectAll();
                    etBarcode.requestFocus();
                    loadTempItems();
                } catch (JSONException e) {
                    handleError("数据解析错误");
                }
            }

            @Override
            public void onError(String error) {
                if (error.contains("已存在") || error.contains("重复")) {
                    playSound(duplicateSound);
                } else if (error.contains("不存在")) {
                    playSound(deleteSuccessSound);
                }
                handleError("添加商品失败: " + error);
            }

            private void handleError(String message) {
                Toast.makeText(ReceivingActivity.this, message, Toast.LENGTH_SHORT).show();
                etBarcode.setText("");
                etBarcode.requestFocus();
            }
        });
    }

    private void loadTempItems() {
        if (currentPlate.isEmpty()) return;

        apiClient.getTempItems(currentPlate, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray data = response.getJSONArray("data");
                            itemList.clear();

                            for (int i = 0; i < data.length(); i++) {
                                JSONObject item = data.getJSONObject(i);
                                ReceivingItem receivingItem = new ReceivingItem(
                                        item.getInt("id"),
                                        item.getString("板标"),
                                        item.getString("商品货号"),
                                        item.getString("创建时间")
                                );
                                itemList.add(receivingItem);
                            }

                            adapter.notifyDataSetChanged();
                            updateScanCount();
                        } else {
                            String error = response.getString("message");
                            Toast.makeText(ReceivingActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(ReceivingActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                        Log.e("LoadError", "解析失败", e);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ReceivingActivity.this, "加载商品失败: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateScanCount() {
        if (tvScanCount != null) {
            tvScanCount.setText("已扫描: " + itemList.size() + " 条记录");
        }
    }

    private void saveReceiving() {
        if (currentPlate.isEmpty()) {
            Toast.makeText(this, "请先扫描板标", Toast.LENGTH_SHORT).show();
            return;
        }

        if (itemList.isEmpty()) {
            Toast.makeText(this, "没有商品可保存", Toast.LENGTH_SHORT).show();
            return;
        }

        apiClient.checkDuplicates(currentPlate, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            boolean hasDuplicates = response.getJSONObject("data").getBoolean("has_duplicates");

                            if (hasDuplicates) {
                                playSound(dialogSound);
                                showDuplicateConfirmDialog();
                            } else {
                                showBranchSelectionDialog();
                            }
                        } else {
                            String error = response.getString("message");
                            Toast.makeText(ReceivingActivity.this, "检查重复失败: " + error, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(ReceivingActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ReceivingActivity.this, "检查重复失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDuplicateConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("发现重复商品")
                .setMessage("收货表中已存在相同商品，是否继续保存？")
                .setPositiveButton("是", (dialog, which) -> showBranchSelectionDialog())
                .setNegativeButton("否", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void performSaveWithBranch() {
        if (selectedBranch.isEmpty()) {
            Toast.makeText(this, "请选择分公司", Toast.LENGTH_SHORT).show();
            return;
        }

        apiClient.checkBranchBarcodes(currentPlate, selectedBranch, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            boolean allExist = response.getJSONObject("data").getBoolean("all_exist");

                            if (allExist) {
                                saveWithBranch(false);
                            } else {
                                playSound(dialogSound);
                                showBranchNotExistConfirmDialog();
                            }
                        } else {
                            String error = response.getString("message");
                            Toast.makeText(ReceivingActivity.this, "检查分公司商品失败: " + error, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(ReceivingActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ReceivingActivity.this, "检查分公司商品失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showBranchNotExistConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("商品不存在警告")
                .setMessage("部分商品在分公司 " + selectedBranch + " 中不存在，是否继续保存？")
                .setPositiveButton("继续保存", (dialog, which) -> saveWithBranch(true))
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void saveWithBranch(boolean force) {
        apiClient.saveTempToMainWithBranch(currentPlate, selectedBranch, force, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            playSound(saveSuccessSound);
                            clearInterface();
                            Toast.makeText(ReceivingActivity.this, "保存成功（分公司：" + selectedBranch + "）", Toast.LENGTH_SHORT).show();
                        } else {
                            String error = response.getString("message");
                            Toast.makeText(ReceivingActivity.this, "保存失败: " + error, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(ReceivingActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ReceivingActivity.this, "保存失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearCurrentTemp() {
        if (currentPlate.isEmpty()) {
            Toast.makeText(this, "请先扫描板标", Toast.LENGTH_SHORT).show();
            return;
        }

        playSound(shanchuSuccessSound);
        new AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("是否清空当前板标 " + currentPlate + " 的临时表数据？")
                .setPositiveButton("确认", (dialog, which) -> performClear())
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void performClear() {
        apiClient.clearTemp(currentPlate, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            playSound(deleteSuccessSound);
                            Toast.makeText(ReceivingActivity.this, "清空成功", Toast.LENGTH_SHORT).show();
                            loadTempItems();
                        } else {
                            String error = response.getString("message");
                            Toast.makeText(ReceivingActivity.this, "清空失败: " + error, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(ReceivingActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ReceivingActivity.this, "清空失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearInterface() {
        etPlate.setText("");
        etBarcode.setText("");
        etPlate.setEnabled(true);
        btnScanPlate.setEnabled(true);
        itemList.clear();
        adapter.notifyDataSetChanged();
        currentPlate = "";
        selectedBranch = "";
        updateScanCount();
        etPlate.requestFocus();
    }

    public static class ReceivingItem {
        public int id;
        public String 板标;
        public String 商品货号;
        public String 创建时间;

        public ReceivingItem(int id, String 板标, String 商品货号, String 创建时间) {
            this.id = id;
            this.板标 = 板标;
            this.商品货号 = 商品货号;
            this.创建时间 = 创建时间;
        }
    }

    public class ReceivingAdapter extends android.widget.BaseAdapter {
        private android.content.Context context;
        private List<ReceivingItem> items;

        private final SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        private final SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

        public ReceivingAdapter(android.content.Context context, List<ReceivingItem> items) {
            this.context = context;
            this.items = items;
        }

        class ViewHolder {
            TextView tvLine1;
            TextView tvLine2;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = View.inflate(context, android.R.layout.simple_list_item_2, null);
                holder = new ViewHolder();
                holder.tvLine1 = convertView.findViewById(android.R.id.text1);
                holder.tvLine2 = convertView.findViewById(android.R.id.text2);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ReceivingItem item = items.get(position);
            holder.tvLine1.setText(String.format("商品货号：%s | 板标：%s", item.商品货号, item.板标));
            holder.tvLine2.setText(String.format("创建时间：%s", formatDate(item.创建时间)));

            return convertView;
        }

        private String formatDate(String originalDate) {
            if (originalDate == null || originalDate.isEmpty()) {
                return "未记录";
            }

            try {
                Date date = inputDateFormat.parse(originalDate);
                if (date == null) {
                    return originalDate;
                }
                return outputDateFormat.format(date);
            } catch (ParseException e) {
                Log.e("DateError", "解析失败: " + originalDate, e);
                return originalDate;
            }
        }
    }
}