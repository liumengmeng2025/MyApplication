package com.example.inventorypda;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class DistributionDepartmentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distribution_department);

        // 初始化所有按钮
        Button btnQuery = findViewById(R.id.btnQuery);
        Button btnDetail = findViewById(R.id.btnDetail);
        Button btnDistribution = findViewById(R.id.btnDistribution);
        Button btnQueryLocation = findViewById(R.id.btnQueryLocation);
        Button btnCabinetFlow = findViewById(R.id.btnCabinetFlow);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnDoReview = findViewById(R.id.btnDoReview);

        // 设置按钮点击事件
        btnDoReview.setOnClickListener(v -> {
            Intent intent = new Intent(DistributionDepartmentActivity.this, DoReviewBillActivity.class);
            startActivity(intent);
        });

        btnQuery.setOnClickListener(v -> {
            startActivity(new Intent(DistributionDepartmentActivity.this, QueryActivity.class));
        });

        btnDetail.setOnClickListener(v -> {
            startActivity(new Intent(DistributionDepartmentActivity.this, DetailActivity.class));
        });

        btnDistribution.setOnClickListener(v -> {
            startActivity(new Intent(DistributionDepartmentActivity.this, DistributionActivity.class));
        });

        btnQueryLocation.setOnClickListener(v -> {
            startActivity(new Intent(DistributionDepartmentActivity.this, QueryLocationActivity.class));
        });

        // 装柜操作按钮 - 打开 CabinetOperationActivity
        btnCabinetFlow.setOnClickListener(v -> {
            startActivity(new Intent(DistributionDepartmentActivity.this, CabinetOperationActivity.class));
        });

        btnBack.setOnClickListener(v -> {
            finish();
        });
    }
}