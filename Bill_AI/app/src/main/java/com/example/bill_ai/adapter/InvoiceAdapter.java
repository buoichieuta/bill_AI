package com.example.bill_ai.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bill_ai.R;
import com.example.bill_ai.model.Invoice;
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
        holder.tvAmount.setText(String.format("%,.0f VNĐ", (double) inv.totalCost));

        // 2. Logic phân loại và đổi màu Tag/Icon
        // Lưu ý: Nếu model Invoice của bạn CÓ biến category (ví dụ: inv.category) thì đổi "Other" thành inv.category
        String category = "Other";

        switch (category) {
            case "Eating":
                holder.tvCategoryTag.setText("🍔 Eating");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#D84315"));
                holder.tvCategoryTag.setBackgroundResource(R.drawable.bg_tag_eating);
                holder.flIconContainer.setBackgroundResource(R.drawable.bg_tag_eating);
                holder.tvIconEmoji.setText("☕");
                break;

            case "Transport":
                holder.tvCategoryTag.setText("🚗 Transport");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#6A1B9A"));
                holder.tvCategoryTag.setBackgroundResource(R.drawable.bg_tag_transport);
                holder.flIconContainer.setBackgroundResource(R.drawable.bg_tag_transport);
                holder.tvIconEmoji.setText("🚘");
                break;

            case "Shopping":
                holder.tvCategoryTag.setText("🛍️ Shopping");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#C2185B"));
                // holder.tvCategoryTag.setBackgroundResource(R.drawable.bg_tag_shopping);
                // holder.flIconContainer.setBackgroundResource(R.drawable.bg_tag_shopping);
                holder.tvIconEmoji.setText("👜");
                break;

            default: // Other / Khác
                holder.tvCategoryTag.setText("📦 Khác");
                holder.tvCategoryTag.setTextColor(Color.parseColor("#757575"));
                // Background mặc định màu xám nhạt (nếu cần thiết có thể tạo file bg_tag_other.xml)
                holder.flIconContainer.setBackgroundColor(Color.parseColor("#F5F5F5"));
                holder.tvCategoryTag.setBackgroundColor(Color.parseColor("#F5F5F5"));
                holder.tvIconEmoji.setText("🧾");
                break;
        }

        // 3. Xử lý click item
        holder.itemView.setOnClickListener(v -> listener.onItemClick(inv));
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // Cập nhật lại các View cho khớp với ID trong file item_invoice.xml mới
        TextView tvVendorName, tvTimestamp, tvAmount, tvCategoryTag, tvIconEmoji;
        FrameLayout flIconContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvVendorName    = itemView.findViewById(R.id.tvVendorName);
            tvTimestamp     = itemView.findViewById(R.id.tvTimestamp);
            tvAmount        = itemView.findViewById(R.id.tvAmount);
            tvCategoryTag   = itemView.findViewById(R.id.tvCategoryTag);
            tvIconEmoji     = itemView.findViewById(R.id.tvIconEmoji);
            flIconContainer = itemView.findViewById(R.id.flIconContainer);
        }
    }
}