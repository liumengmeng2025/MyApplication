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

        Button btnQuery = findViewById(R.id.btnQuery);
        Button btnDetail = findViewById(R.id.btnDetail);
        Button btnDistribution = findViewById(R.id.btnDistribution);
        Button btnQueryLocation = findViewById(R.id.btnQueryLocation);
        Button btnCabinetFlow = findViewById(R.id.btnCabinetFlow);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnDoReview = findViewById(R.id.btnDoReview);
        btnDoReview.setOnClickListener(v -> {
            Intent intent = new Intent(DistributionDepartmentActivity.this, DoReviewBillActivity.class);
            startActivity(intent);
        });
        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DistributionDepartmentActivity.this, QueryActivity.class));
            }
        });

        btnDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DistributionDepartmentActivity.this, DetailActivity.class));
            }
        });

        btnDistribution.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DistributionDepartmentActivity.this, DistributionActivity.class));
            }
        });

        btnQueryLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DistributionDepartmentActivity.this, QueryLocationActivity.class));
            }
        });

        btnCabinetFlow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DistributionDepartmentActivity.this, CabinetFlowActivity.class));
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