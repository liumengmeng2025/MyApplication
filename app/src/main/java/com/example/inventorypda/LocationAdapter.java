package com.example.inventorypda;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocationAdapter extends ArrayAdapter<QueryLocationActivity.LocationItem> {

    private LayoutInflater inflater;
    private int selectedPosition = -1;
    // 日期解析器（处理 Fri,29 Aug 2025 格式）
    private SimpleDateFormat inputDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH);
    // 日期格式化器（转换为 2025/8/29 格式）
    private SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

    public LocationAdapter(Context context, List<QueryLocationActivity.LocationItem> items) {
        super(context, 0, items);
        inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            // 加载现有布局（不含tvCreateTime）
            convertView = inflater.inflate(R.layout.list_item_location, parent, false);
            holder = new ViewHolder();
            holder.tvProduct = convertView.findViewById(R.id.tvProduct);
            holder.tvPlate = convertView.findViewById(R.id.tvPlate);
            holder.tvArea = convertView.findViewById(R.id.tvArea);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        QueryLocationActivity.LocationItem item = getItem(position);

        if (item != null) {
            // 显示内容添加前缀说明
            holder.tvProduct.setText("商品：" + item.商品货号);
            holder.tvPlate.setText("板标：" + item.板标);
            // 将区域和时间合并显示（避免使用tvCreateTime）
            holder.tvArea.setText(String.format(
                    "区域：%s | 创建时间：%s",
                    (item.区域 != null && !item.区域.isEmpty() ? item.区域 : "未绑定"),
                    formatDate(item.创建时间) // 时间格式化逻辑保留
            ));
        }

        // 选中项高亮样式
        if (position == selectedPosition) {
            convertView.setBackgroundColor(getContext().getResources().getColor(android.R.color.holo_blue_light));
        } else {
            convertView.setBackgroundColor(getContext().getResources().getColor(android.R.color.transparent));
        }

        return convertView;
    }

    private String formatDate(String originalDate) {
        if (originalDate == null || originalDate.isEmpty()) {
            return "未记录";
        }

        try {
            Date date = inputDateFormat.parse(originalDate);
            if (date != null) {
                String formatted = outputDateFormat.format(date);
                // 去除月份和日期的前导零
                return formatted.replaceAll("/0", "/").replaceFirst("^0", "");
            }
        } catch (ParseException e) {
            Log.e("DateFormat", "日期解析失败: " + originalDate, e);
        }
        return originalDate; // 解析失败时显示原始值
    }

    // 设置选中位置
    public void setSelectedPosition(int position) {
        selectedPosition = position;
        notifyDataSetChanged();
    }

    // 获取选中位置
    public int getSelectedPosition() {
        return selectedPosition;
    }

    // ViewHolder：移除tvCreateTime，仅保留现有控件
    static class ViewHolder {
        TextView tvProduct;
        TextView tvPlate;
        TextView tvArea;
        // 移除：TextView tvCreateTime;
    }
}
