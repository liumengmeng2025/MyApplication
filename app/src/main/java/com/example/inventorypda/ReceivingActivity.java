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
    private Button btnScanPlate, btnSave, btnSaveAll, btnClear, btnCheckBarcodeExists;
    private ListView lvItems;
    private TextView tvScanCount;
    private List<ReceivingItem> itemList;
    private ReceivingAdapter adapter;
    private String currentPlate = "";
    private ApiClient apiClient;

    private MediaPlayer plateFixedSound;
    private MediaPlayer scanSuccessSound;
    private MediaPlayer saveSuccessSound;
    private MediaPlayer duplicateSound;
    private MediaPlayer dialogSound; // 对话框提示音
    private MediaPlayer deleteSuccessSound; // 删除成功提示音
    private MediaPlayer checkExistsSound; // 检查存在提示音
    private MediaPlayer shanchuSuccessSound; // 点击删除弹出对话框时播放

    private Handler handler = new Handler();
    private Runnable scanRunnable;
    private long lastInputTime = 0;
    private static final long SCAN_DELAY = 300;

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
            dialogSound = MediaPlayer.create(this, R.raw.dialog_sound); // 初始化对话框提示音
            deleteSuccessSound = MediaPlayer.create(this, R.raw.delete_success_sound); // 删除成功后播放
            checkExistsSound = MediaPlayer.create(this, R.raw.dialog_sound); // 检查存在提示音
            shanchuSuccessSound = MediaPlayer.create(this, R.raw.shanchu_success); // 点击删除弹出对话框时播放
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
        btnSaveAll = findViewById(R.id.btnSaveAll);
        btnClear = findViewById(R.id.btnClear);
        btnCheckBarcodeExists = findViewById(R.id.btnCheckBarcodeExists);
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

        btnSaveAll.setOnClickListener(v -> saveAllReceiving());

        btnClear.setOnClickListener(v -> clearCurrentTemp());

        // 新增：检查货号是否存在按钮点击事件
        btnCheckBarcodeExists.setOnClickListener(v -> checkBarcodeExists());

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

    /// 修改后的检查货号是否存在方法
    private void checkBarcodeExists() {
        Toast.makeText(this, "正在检查明细表中所有商品是否存在...", Toast.LENGTH_SHORT).show();

        apiClient.checkBarcodeExistence(new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject data = response.getJSONObject("data");
                            int totalCount = data.getInt("total_count");
                            int existsCount = data.getInt("exists_count");
                            JSONArray barcodeList = data.getJSONArray("barcode_list");

                            StringBuilder message = new StringBuilder();
                            message.append("明细表商品存在性检查结果：\n");
                            message.append("总商品数：").append(totalCount).append("\n");
                            message.append("已存在数：").append(existsCount);

                            // 如果有存在的商品，显示详细信息
                            if (existsCount > 0) {
                                message.append("\n\n已存在的商品货号：");
                                for (int i = 0; i < barcodeList.length(); i++) {
                                    JSONObject item = barcodeList.getJSONObject(i);
                                    if (item.getBoolean("exists")) {
                                        message.append("\n• ").append(item.getString("barcode"));
                                    }
                                }
                                // 有重复时播放提示音
                                playSound(dialogSound);
                            }

                            new AlertDialog.Builder(ReceivingActivity.this)
                                    .setTitle("检查结果")
                                    .setMessage(message.toString())
                                    .setPositiveButton("确定", null)
                                    .show();

                        } else {
                            String errorMsg = response.optString("message", "检查失败");
                            Toast.makeText(ReceivingActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(ReceivingActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                        Log.e("CheckBarcode", "解析失败", e);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() ->
                        Toast.makeText(ReceivingActivity.this, "检查失败: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }
    private void showDeleteConfirmDialog(ReceivingItem item) {
        // 点击删除弹出对话框时播放shanchu_success
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
                            // 删除成功后播放delete_success_sound
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

                    if (message.contains("已添加") || message.contains("已存在")) {
                        playSound(duplicateSound);
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
                                // 只有在有重复时才播放dialog_sound
                                playSound(dialogSound);
                                showDuplicateConfirmDialog();
                            } else {
                                performSave(false);
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
                .setPositiveButton("是", (dialog, which) -> performSave(true))
                .setNegativeButton("否", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void performSave(boolean force) {
        apiClient.saveTempToMain(currentPlate, force, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            playSound(saveSuccessSound);
                            clearInterface();
                            Toast.makeText(ReceivingActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
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

    private void saveAllReceiving() {
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
                                // 只有在有重复时才播放dialog_sound
                                playSound(dialogSound);
                                showAllSaveConfirmDialog();
                            } else {
                                performAllSave(false);
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

    private void showAllSaveConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("发现重复商品")
                .setMessage("收货表中已存在相同商品，是否强制全部保存？")
                .setPositiveButton("是", (dialog, which) -> performAllSave(true))
                .setNegativeButton("否", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void performAllSave(boolean force) {
        apiClient.saveAllTempToMain(currentPlate, force, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    try {
                        if (response.getBoolean("success")) {
                            playSound(saveSuccessSound);
                            clearInterface();
                            Toast.makeText(ReceivingActivity.this, "全部保存成功", Toast.LENGTH_SHORT).show();
                        } else {
                            String error = response.getString("message");
                            Toast.makeText(ReceivingActivity.this, "全部保存失败: " + error, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(ReceivingActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ReceivingActivity.this, "全部保存失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearCurrentTemp() {
        if (currentPlate.isEmpty()) {
            Toast.makeText(this, "请先扫描板标", Toast.LENGTH_SHORT).show();
            return;
        }

        // 清空操作使用shanchu_success声音
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
                            // 清空成功播放delete_success_sound
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