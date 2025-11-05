package com.example.inventorypda;
import android.graphics.Color;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DoReviewDetailActivity extends AppCompatActivity {

    private TextView tvBillNumber, tvTotalQuantity, tvLoading;
    private EditText etProductCode;
    private Button btnDifference, btnFinish, btnException;
    private LinearLayout productDetailLayout;

    private ApiClient apiClient;
    private MediaPlayer errorSound, exceedSound; // 移除了scanSuccessSound

    private String currentBillNumber;
    private int totalQuantity;
    private List<Map<String, Object>> doItems = new ArrayList<>();
    private Map<String, Map<String, Object>> productMap = new HashMap<>();

    // 当前显示的商品和对应的ViewHolder
    private Map<String, Object> currentProduct = null;
    private ProductDetailViewHolder currentViewHolder = null;

    // 添加数据加载状态标志
    private boolean isDataLoaded = false;
    private String pendingScanCode = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_do_review_detail);

        initData();
        initViews();
        initSounds();
        apiClient = ApiClient.getInstance(this);

        loadDoItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次界面恢复时都聚焦到输入框
        focusOnProductCodeInput();
    }

    private void initData() {
        Intent intent = getIntent();
        currentBillNumber = intent.getStringExtra("bill_number");
        totalQuantity = intent.getIntExtra("total_quantity", 0);
    }

    private void initViews() {
        tvBillNumber = findViewById(R.id.tvBillNumber);
        tvTotalQuantity = findViewById(R.id.tvTotalQuantity);
        etProductCode = findViewById(R.id.etProductCode);
        btnDifference = findViewById(R.id.btnDifference);
        btnFinish = findViewById(R.id.btnFinish);
        btnException = findViewById(R.id.btnException);
        productDetailLayout = findViewById(R.id.productDetailLayout);
        tvLoading = findViewById(R.id.tvLoading);

        tvBillNumber.setText("单据号：" + currentBillNumber);
        tvTotalQuantity.setText("数量合计：" + totalQuantity);

        setupProductCodeListener();

        btnDifference.setOnClickListener(v -> {
            showDifference();
            // 差异查询返回后聚焦输入框
            focusOnProductCodeInputDelayed();
        });
        btnFinish.setOnClickListener(v -> finishReview());
        btnException.setOnClickListener(v -> showExceptionDialog());

        // 初始聚焦输入框
        focusOnProductCodeInput();

        // 初始显示提示信息
        showEmptyPrompt();
    }

    private void initSounds() {
        try {
            errorSound = MediaPlayer.create(this, R.raw.error_sound);
            exceedSound = MediaPlayer.create(this, R.raw.exceed_sound);
            // 移除了scanSuccessSound的初始化
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupProductCodeListener() {
        etProductCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String code = s.toString().trim();

                // 检测是否包含换行符，如果包含则只处理换行符之前的内容
                if (code.contains("\n") || code.contains("\r")) {
                    // 获取换行符之前的内容
                    String cleanCode = getContentBeforeNewline(code);
                    if (!cleanCode.isEmpty() && cleanCode.length() >= 10 && cleanCode.length() <= 15) {
                        // 设置清理后的文本（不含换行符）
                        etProductCode.setText(cleanCode);
                        etProductCode.setSelection(cleanCode.length());

                        // 延迟处理，避免快速输入时的多次触发
                        etProductCode.postDelayed(() -> {
                            handleScannedCode(cleanCode);
                        }, 100);
                    }
                    return;
                }

                // 处理回车符和换行符
                code = removeEnterAndNewline(code);
                if (!code.equals(s.toString())) {
                    etProductCode.setText(code);
                    etProductCode.setSelection(code.length());
                    return;
                }

                // 放宽条件，只要长度在10-15之间都处理
                if (code.length() >= 10 && code.length() <= 15) {
                    final String finalCode = code;
                    // 延迟处理，避免快速输入时的多次触发
                    etProductCode.postDelayed(() -> {
                        String currentCode = etProductCode.getText().toString().trim();
                        currentCode = removeEnterAndNewline(currentCode);
                        if (finalCode.equals(currentCode)) {
                            handleScannedCode(finalCode);
                        }
                    }, 100);
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

    /**
     * 移除字符串中的回车符和换行符
     */
    private String removeEnterAndNewline(String text) {
        if (text == null) return "";
        return text.replace("\r", "").replace("\n", "").trim();
    }

    /**
     * 聚焦到商品货号输入框
     */
    private void focusOnProductCodeInput() {
        if (etProductCode != null) {
            etProductCode.requestFocus();
            // 可选：显示软键盘
            showSoftKeyboard();
        }
    }

    /**
     * 延迟聚焦到商品货号输入框
     */
    private void focusOnProductCodeInputDelayed() {
        etProductCode.postDelayed(this::focusOnProductCodeInput, 300);
    }

    /**
     * 显示软键盘
     */
    private void showSoftKeyboard() {
        if (etProductCode != null) {
            etProductCode.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etProductCode, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);
        }
    }

    private void handleScannedCode(String code) {
        System.out.println("=== 处理扫描码: " + code + " ===");
        System.out.println("数据加载状态: " + isDataLoaded);
        System.out.println("商品映射大小: " + productMap.size());

        if (!isDataLoaded) {
            pendingScanCode = code;
            Toast.makeText(this, "数据加载中，请稍后...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (productMap.isEmpty()) {
            playSound(errorSound);
            Toast.makeText(this, "商品数据为空，请检查单据", Toast.LENGTH_LONG).show();
            clearAndFocusInput();
            return;
        }

        processProductCode(code);
    }

    private void loadDoItems() {
        tvLoading.setVisibility(View.VISIBLE);
        isDataLoaded = false;
        productMap.clear(); // 清空映射表

        apiClient.queryDoByBill(currentBillNumber, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    try {
                        System.out.println("=== 接口返回原始数据 ===");

                        if (response.getBoolean("success")) {
                            JSONObject data = response.getJSONObject("data");
                            JSONArray itemsArray = data.getJSONArray("items");

                            System.out.println("=== 解析商品数据 ===");
                            System.out.println("商品数量: " + itemsArray.length());

                            doItems.clear();
                            for (int i = 0; i < itemsArray.length(); i++) {
                                JSONObject item = itemsArray.getJSONObject(i);

                                Map<String, Object> itemMap = new HashMap<>();
                                itemMap.put("商品货号", item.optString("商品货号", ""));
                                itemMap.put("商品名称", item.optString("商品名称", ""));
                                itemMap.put("69码", item.optString("69码", ""));
                                itemMap.put("库位", item.optString("库位", ""));

                                // 处理数值类型
                                itemMap.put("数量", safeGetDouble(item, "数量", 0.0));
                                itemMap.put("规格", safeGetDouble(item, "规格", 0.0));
                                itemMap.put("已复核数量", safeGetDouble(item, "已复核数量", 0.0));

                                doItems.add(itemMap);
                            }

                            // 构建商品映射 - 只使用当前单据的商品
                            buildProductMapFromCurrentBill();
                            isDataLoaded = true;

                            // 验证数据完整性
                            validateDataIntegrity();

                            // 数据加载完成后聚焦输入框
                            focusOnProductCodeInput();

                            // 如果有等待处理的扫描码，现在处理它
                            if (pendingScanCode != null) {
                                System.out.println("处理等待的扫描码: " + pendingScanCode);
                                String code = pendingScanCode;
                                pendingScanCode = null;
                                processProductCode(code);
                            }

                            Toast.makeText(DoReviewDetailActivity.this,
                                    "加载成功，共" + doItems.size() + "个商品", Toast.LENGTH_SHORT).show();

                        } else {
                            Toast.makeText(DoReviewDetailActivity.this,
                                    "加载失败：" + response.optString("message"), Toast.LENGTH_SHORT).show();
                            focusOnProductCodeInput();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(DoReviewDetailActivity.this,
                                "数据解析错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        focusOnProductCodeInput();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    isDataLoaded = true;
                    Toast.makeText(DoReviewDetailActivity.this, "加载失败：" + error, Toast.LENGTH_SHORT).show();
                    focusOnProductCodeInput();
                });
            }
        });
    }

    // 其他方法保持不变...
    private void buildProductMapFromCurrentBill() {
        productMap.clear();
        System.out.println("=== 从当前单据构建商品映射 ===");

        // 记录映射关系用于验证
        Map<String, String> mappingLog = new HashMap<>();

        for (Map<String, Object> item : doItems) {
            String productCode = getStringValue(item, "商品货号");
            String barcode69 = getStringValue(item, "69码");

            System.out.println("添加商品: 货号='" + productCode + "', 69码='" + barcode69 + "'");

            if (productCode != null && !productCode.trim().isEmpty()) {
                String cleanProductCode = productCode.trim();
                productMap.put(cleanProductCode, item);
                mappingLog.put(cleanProductCode, "商品货号 -> " + productCode);
            }

            if (barcode69 != null && !barcode69.trim().isEmpty()) {
                String cleanBarcode69 = barcode69.trim();
                productMap.put(cleanBarcode69, item);
                mappingLog.put(cleanBarcode69, "69码 -> " + productCode);
            }
        }

        System.out.println("=== 商品映射构建完成 ===");
        System.out.println("映射键数量: " + productMap.size());
        System.out.println("映射关系: " + mappingLog);
    }

    private String getStringValue(Map<String, Object> item, String key) {
        Object value = item.get(key);
        if (value == null) return null;
        return value.toString().trim();
    }

    private double safeGetDouble(JSONObject obj, String key, double defaultValue) {
        try {
            if (obj.has(key) && !obj.isNull(key)) {
                Object value = obj.get(key);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                } else if (value instanceof String) {
                    return Double.parseDouble((String) value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    private void validateDataIntegrity() {
        System.out.println("=== 数据完整性验证 ===");

        // 检查问题条码
        String[] problemBarcodes = {"6972073545525", "6972984889800"};
        String[] problemProducts = {"82090647801", "81010286800"};

        for (String barcode : problemBarcodes) {
            boolean existsInMap = productMap.containsKey(barcode);
            boolean existsInItems = false;
            String foundProductCode = null;

            for (Map<String, Object> item : doItems) {
                String itemBarcode = getStringValue(item, "69码");
                if (barcode.equals(itemBarcode)) {
                    existsInItems = true;
                    foundProductCode = getStringValue(item, "商品货号");
                    break;
                }
            }

            System.out.println("条码 " + barcode + ":");
            System.out.println("  在映射表中: " + existsInMap);
            System.out.println("  在商品列表中: " + existsInItems);
            System.out.println("  对应商品: " + foundProductCode);

            if (existsInMap != existsInItems) {
                System.out.println("!!! 数据不一致警告");
            }
        }

        for (String productCode : problemProducts) {
            boolean exists = false;
            for (Map<String, Object> item : doItems) {
                if (productCode.equals(getStringValue(item, "商品货号"))) {
                    exists = true;
                    break;
                }
            }
            System.out.println("商品 " + productCode + " 在单据中: " + exists);
        }
    }

    private void processProductCode(String code) {
        String cleanCode = code.trim();
        System.out.println("=== 开始处理商品码 ===");
        System.out.println("扫描的码: '" + cleanCode + "'");
        System.out.println("映射表大小: " + productMap.size());

        // 检查映射是否存在
        if (!productMap.containsKey(cleanCode)) {
            System.out.println("!!! 条码不存在于当前单据的映射表中");
            System.out.println("!!! 可用的条码: " + getAvailableBarcodes());

            playSound(errorSound);
            Toast.makeText(this, "商品不存在于当前单据中！条码: " + cleanCode, Toast.LENGTH_LONG).show();
            clearAndFocusInput();
            return;
        }

        // 获取映射的商品
        currentProduct = productMap.get(cleanCode);
        String mappedProductCode = getStringValue(currentProduct, "商品货号");
        String mappedBarcode = getStringValue(currentProduct, "69码");

        System.out.println(">>> 条码映射信息:");
        System.out.println(">>> 扫描的码: " + cleanCode);
        System.out.println(">>> 映射到商品: " + mappedProductCode);
        System.out.println(">>> 商品的实际69码: " + mappedBarcode);

        // 严格验证映射关系
        if (cleanCode.length() == 13) { // 69码通常是13位
            // 扫描的是69码，必须与商品的实际69码匹配
            if (!cleanCode.equals(mappedBarcode)) {
                System.out.println("!!! 严重错误: 69码映射不匹配!");
                System.out.println("!!! 扫描的69码: " + cleanCode);
                System.out.println("!!! 商品的实际69码: " + mappedBarcode);

                playSound(errorSound);
                Toast.makeText(this, "条码验证失败！请扫描正确的商品条码", Toast.LENGTH_LONG).show();
                clearAndFocusInput();
                return;
            }
        } else {
            // 扫描的是商品货号，必须与商品的实际货号匹配
            if (!cleanCode.equals(mappedProductCode)) {
                System.out.println("!!! 严重错误: 商品货号映射不匹配!");
                playSound(errorSound);
                Toast.makeText(this, "商品货号验证失败！", Toast.LENGTH_LONG).show();
                clearAndFocusInput();
                return;
            }
        }

        // 验证通过，继续处理
        handleMatchedProduct(cleanCode, mappedProductCode, mappedBarcode);
    }

    /**
     * 清空输入框并聚焦
     */
    private void clearAndFocusInput() {
        etProductCode.setText("");
        focusOnProductCodeInput();
    }

    private List<String> getAvailableBarcodes() {
        List<String> barcodes = new ArrayList<>();
        for (String key : productMap.keySet()) {
            if (key.length() == 13) { // 只显示69码
                barcodes.add(key);
            }
        }
        return barcodes;
    }

    private void handleMatchedProduct(String scannedCode, String productCode, String actualBarcode) {
        System.out.println(">>> 商品匹配成功:");
        System.out.println(">>> 扫描的码: " + scannedCode);
        System.out.println(">>> 商品货号: " + productCode);
        System.out.println(">>> 实际69码: " + actualBarcode);

        // 获取当前复核数量
        double reviewedQty = (Double) currentProduct.get("已复核数量");
        double quantity = (Double) currentProduct.get("数量");
        double specification = (Double) currentProduct.get("规格");

        System.out.println("当前复核数量: " + reviewedQty);
        System.out.println("规格: " + specification);
        System.out.println("计划数量: " + quantity);

        // 增加复核数量
        double newReviewedQty = reviewedQty + specification;

        // 检查是否超量
        if (newReviewedQty > quantity) {
            playSound(exceedSound);
            Toast.makeText(this, "复核数量超过开单数量！", Toast.LENGTH_LONG).show();
            clearAndFocusInput();
            return;
        }

        // 移除了扫描成功音效播放

        // 更新复核数量
        updateReviewedQuantity(productCode, (int) newReviewedQty);
    }

    private void updateReviewedQuantity(String productCode, int reviewedQty) {
        apiClient.updateDoReviewedQty(currentBillNumber, productCode, reviewedQty,
                new ApiClient.ApiResponseListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        runOnUiThread(() -> {
                            try {
                                if (response.getBoolean("success")) {
                                    // 更新本地数据
                                    for (Map<String, Object> item : doItems) {
                                        if (productCode.equals(getStringValue(item, "商品货号"))) {
                                            item.put("已复核数量", (double) reviewedQty);
                                            currentProduct = item;
                                            break;
                                        }
                                    }
                                    // 重新构建映射
                                    buildProductMapFromCurrentBill();
                                    // 更新显示
                                    showProductDetail(currentProduct);
                                    // 清空输入框并聚焦
                                    clearAndFocusInput();
                                    Toast.makeText(DoReviewDetailActivity.this, "扫描成功", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(DoReviewDetailActivity.this,
                                            response.getString("message"), Toast.LENGTH_SHORT).show();
                                    focusOnProductCodeInput();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                focusOnProductCodeInput();
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(DoReviewDetailActivity.this, "更新失败：" + error, Toast.LENGTH_SHORT).show();
                            clearAndFocusInput();
                        });
                    }
                });
    }

    private void showProductDetail(Map<String, Object> product) {
        productDetailLayout.removeAllViews();

        if (product == null) {
            showEmptyPrompt();
            return;
        }

        try {
            LayoutInflater inflater = LayoutInflater.from(this);
            View productView = inflater.inflate(R.layout.item_product_detail, productDetailLayout, false);

            currentViewHolder = new ProductDetailViewHolder(productView);
            currentViewHolder.bindData(product);

            setupUpdateButton(currentViewHolder, product);

            productDetailLayout.addView(productView);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "显示商品详情失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupUpdateButton(ProductDetailViewHolder viewHolder, Map<String, Object> product) {
        final String productCode = getStringValue(product, "商品货号");
        final double quantity = (Double) product.get("数量");
        final double currentReviewedQty = (Double) product.get("已复核数量");

        viewHolder.btnUpdate.setOnClickListener(v -> {
            String newQtyStr = viewHolder.etReviewed.getText().toString().trim();
            if (!newQtyStr.isEmpty()) {
                try {
                    int newQty = Integer.parseInt(newQtyStr);
                    if (newQty > quantity) {
                        playSound(exceedSound);
                        Toast.makeText(this, "不能超过开单数量", Toast.LENGTH_SHORT).show();
                        viewHolder.etReviewed.setText(String.valueOf((int) currentReviewedQty));
                    } else if (newQty != currentReviewedQty) {
                        // 移除了手动更新时的成功音效
                        updateReviewedQuantity(productCode, newQty);
                    }
                    // 更新完成后聚焦到主输入框
                    focusOnProductCodeInput();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                    viewHolder.etReviewed.setText(String.valueOf((int) currentReviewedQty));
                    focusOnProductCodeInput();
                }
            }
        });
    }

    private void showEmptyPrompt() {
        productDetailLayout.removeAllViews();

        TextView tvPrompt = new TextView(this);
        tvPrompt.setText("请扫描商品货号或69码");
        tvPrompt.setTextSize(18);
        tvPrompt.setTextColor(0xFF999999);
        tvPrompt.setGravity(Gravity.CENTER);
        tvPrompt.setPadding(0, 100, 0, 0);

        productDetailLayout.addView(tvPrompt);
    }

    private void showDifference() {
        Intent intent = new Intent(this, DoReviewDifferenceActivity.class);
        intent.putExtra("bill_number", currentBillNumber);
        startActivity(intent);
    }

    private void finishReview() {
        // 检查复核状态并显示相应的确认消息
        String confirmMessage = getReviewStatusMessage();

        new AlertDialog.Builder(this)
                .setTitle("确认完成复核")
                .setMessage(confirmMessage)
                .setPositiveButton("确认完成", (dialog, which) -> {
                    apiClient.finishDoReview(currentBillNumber, new ApiClient.ApiResponseListener() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            runOnUiThread(() -> {
                                try {
                                    if (response.getBoolean("success")) {
                                        String message = response.optString("message", "复核完成");
                                        String reviewStatus = getOverallReviewStatus();

                                        // 根据复核状态显示不同的成功消息
                                        String successMessage = "复核完成！状态：" + getStatusDisplayText(reviewStatus);
                                        Toast.makeText(DoReviewDetailActivity.this,
                                                successMessage, Toast.LENGTH_LONG).show();
                                        finish();
                                    } else {
                                        Toast.makeText(DoReviewDetailActivity.this,
                                                response.getString("message"), Toast.LENGTH_SHORT).show();
                                        focusOnProductCodeInput();
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    focusOnProductCodeInput();
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(DoReviewDetailActivity.this, "操作失败：" + error, Toast.LENGTH_SHORT).show();
                                focusOnProductCodeInput();
                            });
                        }
                    });
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    // 取消后聚焦输入框
                    focusOnProductCodeInput();
                })
                .show();
    }

    /**
     * 获取复核状态确认消息
     */
    private String getReviewStatusMessage() {
        String overallStatus = getOverallReviewStatus();
        StringBuilder message = new StringBuilder();

        message.append("当前复核状态：").append(getStatusDisplayText(overallStatus)).append("\n\n");
        message.append("确定要完成复核吗？完成后数据将保存到已审核明细表。\n\n");

        // 添加详细状态统计
        int normalCount = 0, partialDiffCount = 0, diffCount = 0, totalCount = doItems.size();

        for (Map<String, Object> item : doItems) {
            double quantity = (Double) item.get("数量");
            double reviewedQty = (Double) item.get("已复核数量");

            if (reviewedQty == quantity) {
                normalCount++;
            } else if (reviewedQty == 0) {
                diffCount++;
            } else if (reviewedQty < quantity) {
                partialDiffCount++;
            }
        }

        message.append("商品统计：\n");
        message.append("• 正常：").append(normalCount).append("个\n");
        message.append("• 部分差异：").append(partialDiffCount).append("个\n");
        message.append("• 差异：").append(diffCount).append("个\n");
        message.append("• 总计：").append(totalCount).append("个");

        return message.toString();
    }

    /**
     * 获取整体复核状态
     */
    private String getOverallReviewStatus() {
        boolean allNormal = true;
        boolean allDiff = true;
        boolean hasPartial = false;

        for (Map<String, Object> item : doItems) {
            double quantity = (Double) item.get("数量");
            double reviewedQty = (Double) item.get("已复核数量");

            if (reviewedQty != quantity) {
                allNormal = false;
            }
            if (reviewedQty != 0) {
                allDiff = false;
            }
            if (reviewedQty > 0 && reviewedQty < quantity) {
                hasPartial = true;
            }
        }

        if (allNormal) {
            return "正常";
        } else if (allDiff) {
            return "差异";
        } else if (hasPartial) {
            return "部分差异";
        } else {
            return "混合状态";
        }
    }

    /**
     * 获取状态显示文本
     */
    private String getStatusDisplayText(String status) {
        switch (status) {
            case "正常":
                return "✅ 正常（全部完成）";
            case "部分差异":
                return "⚠️ 部分差异";
            case "差异":
                return "❌ 差异";
            case "混合状态":
                return "🔀 混合状态";
            default:
                return status;
        }
    }

    private void showExceptionDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("复核异常")
                .setMessage("请选择操作：")
                .setPositiveButton("重新复核", (dialog1, which) -> {
                    // 重新复核逻辑
                    apiClient.resetDoReview(currentBillNumber, new ApiClient.ApiResponseListener() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            runOnUiThread(() -> {
                                Toast.makeText(DoReviewDetailActivity.this,
                                        "复核数量已清空", Toast.LENGTH_SHORT).show();
                                loadDoItems();
                                showEmptyPrompt();
                                currentProduct = null;
                                currentViewHolder = null;
                                focusOnProductCodeInput();
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(DoReviewDetailActivity.this, "操作失败：" + error, Toast.LENGTH_SHORT).show();
                                focusOnProductCodeInput();
                            });
                        }
                    });
                })
                .setNegativeButton("临时保存", (dialog12, which) -> {
                    // 临时保存逻辑
                    apiClient.tempSaveDoReview(currentBillNumber, new ApiClient.ApiResponseListener() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            runOnUiThread(() -> {
                                Toast.makeText(DoReviewDetailActivity.this,
                                        "已临时保存", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(DoReviewDetailActivity.this, "保存失败：" + error, Toast.LENGTH_SHORT).show();
                                focusOnProductCodeInput();
                            });
                        }
                    });
                })
                .setNeutralButton("取消", (dialog13, which) -> {
                    // 取消后聚焦输入框
                    focusOnProductCodeInput();
                })
                .create();

        // 显示后设置按钮样式
        dialog.show();

        // 设置按钮文字样式
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

        if (positiveButton != null) {
            positiveButton.setTextSize(18);
            positiveButton.setTextColor(Color.RED);
        }
        if (negativeButton != null) {
            negativeButton.setTextSize(18);
            negativeButton.setTextColor(Color.BLUE);
        }
        if (neutralButton != null) {
            neutralButton.setTextSize(18);
            neutralButton.setTextColor(Color.GRAY); // 改为灰色
        }
    }

    private void playSound(MediaPlayer player) {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.seekTo(0);
                } else {
                    player.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (errorSound != null) {
            errorSound.release();
        }
        if (exceedSound != null) {
            exceedSound.release();
        }
        // 移除了scanSuccessSound的释放
    }

    // ViewHolder内部类
    private class ProductDetailViewHolder {
        LinearLayout container;
        TextView tvProductCode;
        TextView tvProductName;
        TextView tvBarcode;
        TextView tvSpecification;
        TextView tvLocation;
        TextView tvQuantity;
        EditText etReviewed;
        Button btnUpdate;

        public ProductDetailViewHolder(View itemView) {
            container = itemView.findViewById(R.id.productDetailContainer);
            tvProductCode = itemView.findViewById(R.id.tvProductCode);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvBarcode = itemView.findViewById(R.id.tvBarcode);
            tvSpecification = itemView.findViewById(R.id.tvSpecification);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            etReviewed = itemView.findViewById(R.id.etReviewed);
            btnUpdate = itemView.findViewById(R.id.btnUpdate);
        }

        public void bindData(Map<String, Object> product) {
            if (product == null) return;

            tvProductCode.setText(getStringValue(product, "商品货号"));
            tvProductName.setText(getStringValue(product, "商品名称"));
            tvBarcode.setText(getStringValue(product, "69码"));

            double specification = (Double) product.get("规格");
            tvSpecification.setText(String.valueOf((int) specification));

            tvLocation.setText(getStringValue(product, "库位"));

            double quantity = (Double) product.get("数量");
            tvQuantity.setText(String.valueOf((int) quantity));

            double reviewedQty = (Double) product.get("已复核数量");
            etReviewed.setText(String.valueOf((int) reviewedQty));

            // 添加复核状态显示
            updateReviewStatusDisplay(product);
        }

        /**
         * 更新复核状态显示
         */
        private void updateReviewStatusDisplay(Map<String, Object> product) {
            double quantity = (Double) product.get("数量");
            double reviewedQty = (Double) product.get("已复核数量");

            // 移除之前的状态文本视图（如果存在）
            TextView existingStatusView = container.findViewWithTag("status_view");
            if (existingStatusView != null) {
                container.removeView(existingStatusView);
            }

            // 创建状态显示文本
            TextView tvStatus = new TextView(container.getContext());
            tvStatus.setTag("status_view");
            tvStatus.setTextSize(14);
            tvStatus.setPadding(0, 8, 0, 0);

            String statusText;
            int textColor;

            if (reviewedQty == quantity) {
                statusText = "✅ 正常";
                textColor = 0xFF4CAF50; // 绿色
            } else if (reviewedQty == 0) {
                statusText = "❌ 差异";
                textColor = 0xFFF44336; // 红色
            } else if (reviewedQty < quantity) {
                statusText = "⚠️ 部分差异 (" + (int)reviewedQty + "/" + (int)quantity + ")";
                textColor = 0xFFFF9800; // 橙色
            } else {
                statusText = "❓ 未知状态";
                textColor = 0xFF9E9E9E; // 灰色
            }

            tvStatus.setText(statusText);
            tvStatus.setTextColor(textColor);

            // 添加到容器中
            container.addView(tvStatus);
        }
    }
}