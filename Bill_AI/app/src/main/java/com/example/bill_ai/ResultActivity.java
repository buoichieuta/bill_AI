package com.example.bill_ai;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bill_ai.adapter.ProductAdapter;
import com.example.bill_ai.model.Invoice;
import com.example.bill_ai.network.SupabaseManager;
import com.bumptech.glide.Glide;
import java.io.File;

public class ResultActivity extends AppCompatActivity {

    private SupabaseManager supabase;
    private long invoiceId;
    private Invoice currentInvoice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        supabase = new SupabaseManager(this);
        invoiceId  = getIntent().getLongExtra("invoice_id", -1);
        currentInvoice = (Invoice) getIntent().getSerializableExtra("invoice_obj");

        // Nút Lưu
        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            if (currentInvoice != null && invoiceId == -1) {
                // Đây là bill mới quét, cần upload ảnh lên Supabase trước khi lưu bản ghi
                btnSave.setEnabled(false);
                btnSave.setText("Đang tải ảnh...");

                File imageFile = new File(currentInvoice.imagePath);
                String fileName = "bill_" + System.currentTimeMillis() + ".jpg";

                supabase.uploadFile(imageFile, "invoices", fileName, new SupabaseManager.AuthCallback() {
                    @Override
                    public void onSuccess(String url, String email) {
                        // Cập nhật đường dẫn ảnh thành URL của Supabase
                        currentInvoice.imagePath = url;
                        
                        runOnUiThread(() -> btnSave.setText("Đang lưu dữ liệu..."));

                        // Sau đó mới lưu bản ghi vào database
                        supabase.saveInvoice(currentInvoice, new SupabaseManager.AuthCallback() {
                            @Override
                            public void onSuccess(String id, String mail) {
                                runOnUiThread(() -> {
                                    Toast.makeText(ResultActivity.this, "Đã đồng bộ hóa đơn!", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(ResultActivity.this, HomeActivity.class));
                                    finish();
                                });
                            }
                            @Override
                            public void onError(String msg) {
                                runOnUiThread(() -> {
                                    btnSave.setEnabled(true);
                                    btnSave.setText("Lưu");
                                    Toast.makeText(ResultActivity.this, "Lỗi lưu DB: " + msg, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            btnSave.setEnabled(true);
                            btnSave.setText("Lưu");
                            Toast.makeText(ResultActivity.this, "Lỗi tải ảnh: " + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } else {
                // Đã lưu rồi
                startActivity(new Intent(this, HomeActivity.class));
                finish();
            }
        });

        // Nút Xóa (hoặc Hủy nếu chưa lưu)
        Button btnDelete = findViewById(R.id.btnDelete);
        btnDelete.setText(invoiceId == -1 ? "Hủy" : "Xóa");
        btnDelete.setOnClickListener(v -> {
            if (invoiceId == -1) {
                finish(); // Chỉ là hủy xem kết quả
            } else {
                confirmDelete();
            }
        });

        // Nút Scan lại
        Button btnRescan = findViewById(R.id.btnRescan);
        btnRescan.setOnClickListener(v -> {
            startActivity(new Intent(this, CameraActivity.class));
            finish();
        });

        // Nút Back ở góc trên màn hình
        android.view.View btnBack = findViewById(R.id.btnBackFromResult);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (invoiceId != -1) {
            loadInvoice();
        } else if (currentInvoice != null) {
            displayInvoice(currentInvoice);
        }
    }

    private void loadInvoice() {
        supabase.getInvoiceById(invoiceId, new SupabaseManager.InvoiceCallback() {
            @Override
            public void onSuccess(Invoice inv) {
                runOnUiThread(() -> {
                    currentInvoice = inv;
                    displayInvoice(inv);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ResultActivity.this, "Lỗi: " + message, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void displayInvoice(Invoice inv) {
        // Hiện ảnh hóa đơn
        ImageView ivInvoice = findViewById(R.id.ivInvoice);
        if (inv.imagePath != null && !inv.imagePath.isEmpty()) {
            Glide.with(this).load(inv.imagePath).into(ivInvoice);
        }

        ((TextView) findViewById(R.id.tvSeller)).setText(
                inv.seller.isEmpty() ? "Không rõ" : inv.seller);
        ((TextView) findViewById(R.id.tvTimestamp)).setText(
                inv.timestamp.isEmpty() ? inv.createdAt : inv.timestamp);
        ((TextView) findViewById(R.id.tvCategory)).setText(
                inv.category == null || inv.category.isEmpty() ? "Khác" : inv.category);
        ((TextView) findViewById(R.id.tvInvoiceNo)).setText(
                inv.invoiceNo == null || inv.invoiceNo.isEmpty() ? "—" : inv.invoiceNo);
        ((TextView) findViewById(R.id.tvTotal)).setText(
                String.format("%,d VND", inv.totalCost));

        RecyclerView rv = findViewById(R.id.rvProducts);
        rv.setLayoutManager(new LinearLayoutManager(ResultActivity.this));
        rv.setAdapter(new ProductAdapter(inv.products));
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa hóa đơn")
                .setMessage("Bạn có chắc muốn xóa không?")
                .setPositiveButton("Xóa", (d, w) -> {
                    supabase.archiveInvoice(invoiceId, new SupabaseManager.AuthCallback() {
                        @Override
                        public void onSuccess(String id, String email) {
                            runOnUiThread(() -> {
                                Toast.makeText(ResultActivity.this, "Hóa đơn đã được ẩn!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(ResultActivity.this, HomeActivity.class));
                                finish();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                Toast.makeText(ResultActivity.this, "Lỗi ẩn: " + message, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}