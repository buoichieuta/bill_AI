package com.example.bill_ai.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bill_ai.R;
import com.example.bill_ai.model.Invoice;
import com.bumptech.glide.Glide;
import java.io.File;
import java.util.List;

public class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Invoice invoice);
    }

    private final List<Invoice> list;
    private final OnItemClickListener listener;

    public InvoiceAdapter(List<Invoice> list, OnItemClickListener listener) {
        this.list     = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_invoice, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Invoice inv = list.get(position);

        // 1. Gán dữ liệu cơ bản (Tên, Ngày, Số tiền)
        holder.tvVendorName.setText((inv.seller == null || inv.seller.isEmpty()) ? "Không rõ" : inv.seller);
        holder.tvTimestamp.setText((inv.timestamp == null || inv.timestamp.isEmpty()) ? inv.createdAt : inv.timestamp);
        
        // Load ảnh bằng Glide (hỗ trợ cả URL và local path)
        if (inv.imagePath != null && !inv.imagePath.isEmpty()) {
            holder.ivBillPreview.setVisibility(View.VISIBLE);
            holder.tvIconEmoji.setVisibility(View.GONE);
            Glide.with(holder.itemView.getContext())
                    .load(inv.imagePath)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(holder.ivBillPreview);
        } else {
            holder.ivBillPreview.setVisibility(View.GONE);
            holder.tvIconEmoji.setVisibility(View.VISIBLE);
        }
        holder.tvAmount.setText(String.format("%,.0f đ", (double) inv.totalCost).replace(",", "."));

        // 2. Logic phân loại và đổi màu Tag/Icon
        String category = inv.category != null ? inv.category : "Other";
        holder.tvCategoryTag.setText(category);

        switch (category) {
            case "Eating":
            case "Ăn uống":
                holder.tvIconEmoji.setText("🍔");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#FFAB91"));
                break;
            case "Transport":
            case "Di chuyển":
                holder.tvIconEmoji.setText("🚗");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#CE93D8"));
                break;
            case "Shopping":
            case "Mua sắm":
                holder.tvIconEmoji.setText("🛍️");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#F48FB1"));
                break;
            case "Medical":
            case "Y tế":
                holder.tvIconEmoji.setText("🏥");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#81C784"));
                break;
            case "Entertainment":
            case "Giải trí":
                holder.tvIconEmoji.setText("🎬");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#FFF176"));
                break;
            default:
                holder.tvIconEmoji.setText("🧾");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#80CBC4"));
                break;
        }

        // 3. Xử lý click item
        holder.itemView.setOnClickListener(v -> listener.onItemClick(inv));

        // 4. Xử lý nút Download
        if (holder.btnDownloadCsv != null) {
            holder.btnDownloadCsv.setOnClickListener(v -> {
                com.example.bill_ai.utils.CsvExportHelper.exportInvoiceToCsv(v.getContext(), inv.id);
            });
        }
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // Cập nhật lại các View cho khớp với ID trong file item_invoice.xml mới
        TextView tvVendorName, tvTimestamp, tvAmount, tvCategoryTag, tvIconEmoji;
        ImageView ivBillPreview;
        android.view.View btnDownloadCsv;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvVendorName    = itemView.findViewById(R.id.tvVendorName);
            tvTimestamp     = itemView.findViewById(R.id.tvTimestamp);
            tvAmount        = itemView.findViewById(R.id.tvAmount);
            tvCategoryTag   = itemView.findViewById(R.id.tvCategoryTag);
            tvIconEmoji     = itemView.findViewById(R.id.tvIconEmoji);
            ivBillPreview   = itemView.findViewById(R.id.ivBillPreview);
            btnDownloadCsv  = itemView.findViewById(R.id.btnDownloadCsv);
        }
    }
}