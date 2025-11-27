package com.example.inventorypda;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AreaBatchBindActivity extends AppCompatActivity {

    private EditText etPlatePrefix;
    private Button btnQuery, btnBatchBind;
    private ListView lvItems;
    private TextView tvResultCount;

    private List<HashMap<String, String>> itemList;
    private SimpleAdapter adapter;
    private MediaPlayer successSound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_area_batch_bind);

        initViews();
        setupListView();
        setupClickListeners();

        // 初始化语音播放器 - 确保你有这个语音文件
        successSound = MediaPlayer.create(this, R.raw.bind_success_sound);
    }

    private void initViews() {
        etPlatePrefix = findViewById(R.id.etPlatePrefix);
        btnQuery = findViewById(R.id.btnQuery);
        btnBatchBind = findViewById(R.id.btnBatchBind);
        lvItems = findViewById(R.id.lvItems);
        tvResultCount = findViewById(R.id.tvResultCount);
        // 已删除 btnBack 的初始化
    }

    private void setupListView() {
        itemList = new ArrayList<>();
        adapter = new SimpleAdapter(this, itemList,
                R.layout.list_item_batch_bind,
                new String[]{"plate", "count"},
                new int[]{R.id.tvPlate, R.id.tvCount});
        lvItems.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queryItems();
            }
        });

        btnBatchBind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBatchBindDialog();
            }
        });

    }

    private void queryItems() {
        String platePrefix = etPlatePrefix.getText().toString().trim();
        if (platePrefix.isEmpty()) {
            Toast.makeText(this, "请输入板标前缀", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = HttpUtil.BASE_URL + "/receiving/query_by_plate_prefix?plate_prefix=" + platePrefix;

        HttpUtil.sendHttpRequest(url, "GET", null, new HttpUtil.HttpCallbackListener() {
            public void onFinish(String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleQueryResponse(response);
                    }
                });
            }

            public void onError(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AreaBatchBindActivity.this, "查询失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void handleQueryResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            boolean success = jsonResponse.getBoolean("success");

            if (success) {
                JSONArray data = jsonResponse.getJSONArray("data");
                itemList.clear();

                int totalItems = 0;
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    HashMap<String, String> map = new HashMap<>();
                    map.put("plate", item.getString("板标"));
                    map.put("count", String.valueOf(item.getInt("商品数量")));
                    itemList.add(map);
                    totalItems += item.getInt("商品数量");
                }

                adapter.notifyDataSetChanged();
                tvResultCount.setText(String.format("查询结果：%d个板标，共%d件商品", itemList.size(), totalItems));
                btnBatchBind.setEnabled(itemList.size() > 0);

                Toast.makeText(this, String.format("查询到%d个板标，共%d件商品", itemList.size(), totalItems), Toast.LENGTH_SHORT).show();
            } else {
                String error = jsonResponse.getString("error");
                Toast.makeText(this, "查询失败：" + error, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "解析响应失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void showBatchBindDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("批量绑定柜号");
        builder.setMessage("将把所有以 \"" + etPlatePrefix.getText().toString() + "\" 开头的板标绑定到指定柜号");

        final EditText input = new EditText(this);
        input.setHint("请输入柜号");
        builder.setView(input);

        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String area = input.getText().toString().trim();
                if (!area.isEmpty()) {
                    batchBindArea(area);
                } else {
                    Toast.makeText(AreaBatchBindActivity.this, "柜号不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void batchBindArea(String area) {
        String platePrefix = etPlatePrefix.getText().toString().trim();
        String requestData = "plate_prefix=" + platePrefix + "&area=" + area;
        String url = HttpUtil.BASE_URL + "/receiving/batch_bind_area";

        HttpUtil.sendHttpRequest(url, "POST", requestData, new HttpUtil.HttpCallbackListener() {
            public void onFinish(String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleBatchBindResponse(response, area);
                    }
                });
            }

            public void onError(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AreaBatchBindActivity.this, "绑定失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void handleBatchBindResponse(String response, String area) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            boolean success = jsonResponse.getBoolean("success");

            if (success) {
                // 播放成功语音
                if (successSound != null) {
                    successSound.start();
                }

                String message = jsonResponse.getString("message");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                // 刷新列表
                queryItems();
            } else {
                String error = jsonResponse.getString("error");
                Toast.makeText(this, "绑定失败：" + error, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "解析响应失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (successSound != null) {
            successSound.release();
        }
    }
}