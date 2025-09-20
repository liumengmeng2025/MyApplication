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

        Button btnReceivingDept = findViewById(R.id.btnReceivingDept);
        Button btnDistributionDept = findViewById(R.id.btnDistributionDept);

        btnReceivingDept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainMenuActivity.this, ReceivingDepartmentActivity.class));
            }
        });

        btnDistributionDept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainMenuActivity.this, DistributionDepartmentActivity.class));
            }
        });
    }
}