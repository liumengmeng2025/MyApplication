package com.example.inventorypda;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        // 检查更新
        AppUpdateChecker.checkForUpdate(this, false);

        Button btnReceivingDept = findViewById(R.id.btnReceivingDept);
        Button btnDistributionDept = findViewById(R.id.btnDistributionDept);
        Button btnDataQuery = findViewById(R.id.btnDataQuery); // 新增
        Button btnCheckUpdate = findViewById(R.id.btnCheckUpdate);

        btnReceivingDept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainMenuActivity.this, ReceivingDepartmentActivity.class));
            }
        });

        // 新增数据查询按钮点击事件
        btnDataQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainMenuActivity.this, DataQueryActivity.class));
            }
        });

        btnCheckUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppUpdateChecker.checkForUpdate(MainMenuActivity.this, true);
            }
        });

        // 修复这里：直接使用 DistributionDepartmentActivity.class
        btnDistributionDept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainMenuActivity.this, DistributionDepartmentActivity.class));
            }
        });
    }
}