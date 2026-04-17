package com.example.bill_ai.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bill_ai.R;
import com.example.bill_ai.model.Notification;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<Notification> list;
    private OnNotifDeleteListener listener;

    public interface OnNotifDeleteListener {
        void onDelete(Notification n);
    }

    public NotificationAdapter(List<Notification> list, OnNotifDeleteListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification n = list.get(position);
        holder.tvTitle.setText(n.title);
        holder.tvMessage.setText(n.message);
        holder.tvTime.setText(n.timestamp);
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(n));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvTime;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNotifTitle);
            tvMessage = itemView.findViewById(R.id.tvNotifMessage);
            tvTime = itemView.findViewById(R.id.tvNotifTime);
            btnDelete = itemView.findViewById(R.id.btnDeleteNotif);
        }
    }
}
