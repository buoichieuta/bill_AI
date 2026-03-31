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
                runOnUiThread(() -> {
                    InvoiceDao dao = new InvoiceDao(LoadingActivity.this);
                    invoice.imagePath = imagePath;
                    invoice.createdAt = new java.util.Date().toString();
                    long id = dao.saveInvoice(invoice);

                    Intent intent = new Intent(LoadingActivity.this, ResultActivity.class);
                    intent.putExtra("invoice_id", id);
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