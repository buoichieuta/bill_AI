package com.example.bill_ai;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bill_ai.adapter.NotificationAdapter;
import com.example.bill_ai.db.InvoiceDao;
import com.example.bill_ai.model.Notification;
import com.example.bill_ai.network.SupabaseManager;
import java.util.ArrayList;
import java.util.List;

public class NotificationHistoryActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private List<Notification> notificationList = new ArrayList<>();
    private InvoiceDao dao;
    private String userId;
    private LinearLayout layoutEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_history);

        dao = new InvoiceDao(this);
        userId = new SupabaseManager(this).getCurrentUserId();

        rvNotifications = findViewById(R.id.rvNotifications);
        layoutEmpty = findViewById(R.id.layoutEmptyNotif);
        ImageButton btnBack = findViewById(R.id.btnBackNotif);
        ImageButton btnClearAll = findViewById(R.id.btnClearAllNotif);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        
        btnBack.setOnClickListener(v -> finish());
        btnClearAll.setOnClickListener(v -> clearAllNotifications());

        loadNotifications();
    }

    private void loadNotifications() {
        if (userId == null) return;
        
        notificationList = dao.getNotificationsByUserId(userId);
        if (notificationList.isEmpty()) {
            rvNotifications.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            rvNotifications.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            adapter = new NotificationAdapter(notificationList, n -> {
                dao.deleteNotification(n.id);
                loadNotifications();
            });
            rvNotifications.setAdapter(adapter);
        }
    }

    private void clearAllNotifications() {
        if (notificationList.isEmpty()) return;
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xóa tất cả")
                .setMessage("Bạn có chắc chắn muốn xóa toàn bộ lịch sử thông báo?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    dao.clearAllNotifications(userId);
                    loadNotifications();
                    Toast.makeText(this, "Đã xóa toàn bộ thông báo", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
