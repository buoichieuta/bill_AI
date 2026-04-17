package com.example.bill_ai.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.bill_ai.R;
import com.example.bill_ai.db.InvoiceDao;
import com.example.bill_ai.model.Notification;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationHelper {

    private static final String CHANNEL_ID = "invoice_channel";
    private static final String CHANNEL_NAME = "Invoice Notifications";

    public static void showNotification(Context context, String userId, String title, String message) {
        // 1. Lưu vào Database
        InvoiceDao dao = new InvoiceDao(context);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        dao.addNotification(new Notification(userId, title, message, time));

        // 2. Hiển thị thông báo trên hệ thống
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
