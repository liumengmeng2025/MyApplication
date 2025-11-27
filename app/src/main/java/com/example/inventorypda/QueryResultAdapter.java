package com.example.inventorypda;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class QueryResultAdapter extends RecyclerView.Adapter<QueryResultAdapter.ViewHolder> {

    private List<Map<String, String>> dataList;
    private String queryType;

    public QueryResultAdapter(List<Map<String, String>> dataList, String queryType) {
        this.dataList = dataList;
        this.queryType = queryType;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_query_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, String> item = dataList.get(position);

        switch (queryType) {
            case "unscanned_xiangma":
                holder.tvField1.setText("创建时间: " + item.get("创建时间"));
                holder.tvField2.setText("箱唛: " + item.get("箱唛"));
                holder.tvField3.setVisibility(View.GONE);
                holder.tvField4.setVisibility(View.GONE);
                holder.tvField5.setVisibility(View.GONE);
                break;

            case "pending_data":
                holder.tvField2.setText("分公司: " + item.get("分公司"));
                holder.tvField3.setText("件数: " + item.get("件数"));
                holder.tvField4.setText("立方: " + item.get("立方"));
                holder.tvField5.setText("重量: " + item.get("重量"));
                break;

            case "plate_count":
                holder.tvField1.setText("板标: " + item.get("板标"));
                holder.tvField2.setText("类别: " + item.get("类别"));
                holder.tvField3.setText("件数: " + item.get("件数"));
                holder.tvField4.setText("已绑定件数: " + item.get("已绑定件数"));
                holder.tvField5.setText("差异: " + item.get("差异"));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvField1, tvField2, tvField3, tvField4, tvField5;

        public ViewHolder(View view) {
            super(view);
            tvField1 = view.findViewById(R.id.tvField1);
            tvField2 = view.findViewById(R.id.tvField2);
            tvField3 = view.findViewById(R.id.tvField3);
            tvField4 = view.findViewById(R.id.tvField4);
            tvField5 = view.findViewById(R.id.tvField5);
        }
    }
}