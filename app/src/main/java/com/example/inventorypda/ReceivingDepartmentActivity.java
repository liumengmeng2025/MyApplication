package com.example.inventorypda;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class ReceivingDepartmentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiving_department);

        Button btnReceiving = findViewById(R.id.btnReceiving);
        Button btnBindArea = findViewById(R.id.btnBindArea);
        Button btnAreaQuery = findViewById(R.id.btnAreaQuery);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnBatchBind = findViewById(R.id.btnBatchBind);
        btnReceiving.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ReceivingDepartmentActivity.this, ReceivingActivity.class));
            }
        });
        btnBatchBind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ReceivingDepartmentActivity.this, AreaBatchBindActivity.class));
            }
        });
        btnBindArea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ReceivingDepartmentActivity.this, BindAreaActivity.class));
            }
        });

        // 新增区域查询按钮点击事件
        btnAreaQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ReceivingDepartmentActivity.this, AreaQueryActivity.class));
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}