package com.example.inventorypda;

import android.content.Intent; // 添加这行导入
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class DataQueryActivity extends AppCompatActivity {

    private String[] branches = {"新加坡", "马来西亚", "柬埔寨", "越南", "泰缅", "印尼", "缅甸"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_query);

        Button btnUnscannedXiangma = findViewById(R.id.btnUnscannedXiangma);
        Button btnPendingData = findViewById(R.id.btnPendingData);
        Button btnPlateCount = findViewById(R.id.btnPlateCount);
        Button btnBack = findViewById(R.id.btnBack);

        btnUnscannedXiangma.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBranchSelectionDialog("未扫描箱唛查询", "unscanned_xiangma");
            }
        });

        btnPendingData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBranchSelectionDialog("待装数据查询", "pending_data");
            }
        });

        btnPlateCount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBranchSelectionDialog("板标件数查询", "plate_count");
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void showBranchSelectionDialog(String title, final String queryType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setItems(branches, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedBranch = branches[which];
                // 根据查询类型跳转到不同的结果页面
                Intent intent = new Intent(DataQueryActivity.this, QueryResultActivity.class);
                intent.putExtra("query_type", queryType);
                intent.putExtra("branch", selectedBranch);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}