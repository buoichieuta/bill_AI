package com.example.bill_ai;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bill_ai.db.InvoiceDao;
import com.example.bill_ai.model.Invoice;
import com.example.bill_ai.network.ApiService;
import com.example.bill_ai.utils.NotificationHelper;
import com.example.bill_ai.network.SupabaseManager;
import java.io.File;

public class LoadingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        String imagePath = getIntent().getStringExtra("image_path");
        if (imagePath == null) { finish(); return; }

        // Hiện ảnh mờ phía sau
        ImageView ivBg = findViewById(R.id.ivBackground);
        Bitmap bm = BitmapFactory.decodeFile(imagePath);
        if (bm != null) ivBg.setImageBitmap(bm);

        // Gọi API
        File imageFile = new File(imagePath);
        new ApiService().extractInvoice(imageFile, new ApiService.Callback() {
            @Override
            public void onSuccess(Invoice invoice) {
                // Sửa logic: Không lưu ngay lập tức, mà truyền object sang màn hình Kết quả để user chọn Lưu
                invoice.imagePath = imagePath;

                // THÊM: Hiện thông báo và lưu lịch sử
                SupabaseManager supabase = new SupabaseManager(LoadingActivity.this);
                String userId = supabase.getCurrentUserId();
                NotificationHelper.showNotification(LoadingActivity.this, userId, 
                        "Nhận diện thành công!", 
                        "Hóa đơn từ " + (invoice.seller != null ? invoice.seller : "người bán") + " đã sẵn sàng.");

                runOnUiThread(() -> {
                    Intent intent = new Intent(LoadingActivity.this, ResultActivity.class);
                    intent.putExtra("invoice_obj", invoice); // Truyền toàn bộ object
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(LoadingActivity.this,
                            "Loi: " + message, android.widget.Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }
}