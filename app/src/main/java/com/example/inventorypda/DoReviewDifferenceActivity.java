package com.example.inventorypda;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DoReviewDifferenceActivity extends AppCompatActivity {

    private LinearLayout differencesLayout;
    private TextView tvLoading;
    private Button btnBack;
    private TextView tvStats; // 统计信息文本

    private ApiClient apiClient;
    private String currentBillNumber;
    private List<Map<String, Object>> originalDifferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_do_review_difference);

        currentBillNumber = getIntent().getStringExtra("bill_number");
        initViews();
        apiClient = ApiClient.getInstance(this);

        loadDifferences();
    }

    private void initViews() {
        differencesLayout = findViewById(R.id.differencesLayout);
        tvLoading = findViewById(R.id.tvLoading);
        btnBack = findViewById(R.id.btnBack);
        tvStats = findViewById(R.id.tvStats); // 初始化统计文本视图

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadDifferences() {
        tvLoading.setVisibility(View.VISIBLE);
        differencesLayout.removeAllViews();

        apiClient.getDoReviewDifferences(currentBillNumber, new ApiClient.ApiResponseListener() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray differencesArray = response.getJSONArray("data");
                            originalDifferences = parseDifferences(differencesArray);
                            // 默认按库位排序
                            sortByLocation();
                            // 更新统计信息
                            updateStatistics(originalDifferences);
                        } else {
                            Toast.makeText(DoReviewDifferenceActivity.this,
                                    response.getString("message"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(DoReviewDifferenceActivity.this,
                                "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    Toast.makeText(DoReviewDifferenceActivity.this, "加载失败：" + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private List<Map<String, Object>> parseDifferences(JSONArray differencesArray) throws JSONException {
        List<Map<String, Object>> differences = new ArrayList<>();

        for (int i = 0; i < differencesArray.length(); i++) {
            JSONObject diff = differencesArray.getJSONObject(i);
            Map<String, Object> diffMap = new HashMap<>();

            diffMap.put("商品货号", diff.optString("商品货号", ""));
            diffMap.put("商品名称", diff.optString("商品名称", ""));
            diffMap.put("69码", diff.optString("69码", ""));
            diffMap.put("库位", diff.optString("库位", ""));
            diffMap.put("规格", safeGetDouble(diff, "规格", 0.0));
            diffMap.put("数量", safeGetDouble(diff, "数量", 0.0));
            diffMap.put("已复核数量", safeGetDouble(diff, "已复核数量", 0.0));
            diffMap.put("差异数量", safeGetDouble(diff, "差异数量", 0.0));

            differences.add(diffMap);
        }

        return differences;
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

    private void updateDifferences(List<Map<String, Object>> differences) {
        differencesLayout.removeAllViews();

        if (differences.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("无差异数据");
            tvEmpty.setTextSize(16);
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, 50, 0, 0);
            differencesLayout.addView(tvEmpty);
            return;
        }

        // 添加数据卡片
        for (Map<String, Object> diff : differences) {
            View itemView = createDifferenceItem(diff);
            differencesLayout.addView(itemView);
        }
    }

    private View createDifferenceItem(Map<String, Object> diff) {
        // 加载布局文件
        LayoutInflater inflater = LayoutInflater.from(this);
        View itemView = inflater.inflate(R.layout.item_difference_detail, differencesLayout, false);

        // 获取控件
        TextView tvProductCode = itemView.findViewById(R.id.tvProductCode);
        TextView tvProductName = itemView.findViewById(R.id.tvProductName);
        TextView tvBarcode = itemView.findViewById(R.id.tvBarcode);
        TextView tvSpecification = itemView.findViewById(R.id.tvSpecification);
        TextView tvLocation = itemView.findViewById(R.id.tvLocation);
        TextView tvQuantity = itemView.findViewById(R.id.tvQuantity);
        TextView tvReviewedQty = itemView.findViewById(R.id.tvReviewedQty);
        TextView tvDifferenceQty = itemView.findViewById(R.id.tvDifferenceQty);

        // 设置数据
        tvProductCode.setText((String) diff.get("商品货号"));
        tvProductName.setText((String) diff.get("商品名称"));
        tvBarcode.setText((String) diff.get("69码"));

        // 修复类型转换错误：Double -> int
        double specificationValue = (Double) diff.get("规格");
        tvSpecification.setText(String.valueOf((int) specificationValue));

        tvLocation.setText((String) diff.get("库位"));

        double quantityValue = (Double) diff.get("数量");
        tvQuantity.setText(String.valueOf((int) quantityValue));

        double reviewedQtyValue = (Double) diff.get("已复核数量");
        tvReviewedQty.setText(String.valueOf((int) reviewedQtyValue));

        double diffQty = (Double) diff.get("差异数量");
        tvDifferenceQty.setText(String.valueOf((int) diffQty));

        return itemView;
    }

    private void sortByLocation() {
        if (originalDifferences == null) return;

        List<Map<String, Object>> sortedList = new ArrayList<>(originalDifferences);
        Collections.sort(sortedList, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                String location1 = (String) o1.get("库位");
                String location2 = (String) o2.get("库位");
                return location1.compareTo(location2);
            }
        });

        updateDifferences(sortedList);
    }

    private void updateStatistics(List<Map<String, Object>> differences) {
        if (differences == null || differences.isEmpty()) {
            tvStats.setText("差异商品：0个，差异总数：0");
            return;
        }

        int productCount = differences.size();
        int totalDifference = 0;

        for (Map<String, Object> diff : differences) {
            double diffQty = (Double) diff.get("差异数量");
            totalDifference += (int) diffQty;
        }

        tvStats.setText(String.format("差异商品：%d个，差异总数：%d", productCount, totalDifference));
    }
}