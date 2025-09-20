package com.example.inventorypda;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class ReceivingAdapter extends ArrayAdapter<ReceivingActivity.ReceivingItem> {

    private LayoutInflater inflater;

    public ReceivingAdapter(Context context, List<ReceivingActivity.ReceivingItem> items) {
        super(context, 0, items);
        inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.list_item_receiving, parent, false);
            holder = new ViewHolder();
            holder.tvProduct = convertView.findViewById(R.id.tvProduct);
            holder.tvPlate = convertView.findViewById(R.id.tvPlate);
            holder.tvTime = convertView.findViewById(R.id.tvTime);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ReceivingActivity.ReceivingItem item = getItem(position);

        if (item != null) {
            holder.tvProduct.setText(item.商品货号);
            holder.tvPlate.setText(item.板标);
            holder.tvTime.setText(item.创建时间);
        }

        return convertView;
    }

    static class ViewHolder {
        TextView tvProduct;
        TextView tvPlate;
        TextView tvTime;
    }
}