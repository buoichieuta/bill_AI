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
import com.example.bill_ai.db.InvoiceDao;
import com.example.bill_ai.model.Invoice;

public class ResultActivity extends AppCompatActivity {

    private InvoiceDao invoiceDao;
    private long invoiceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        invoiceDao = new InvoiceDao(this);
        invoiceId  = getIntent().getLongExtra("invoice_id", -1);

        // Nút Lưu
        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            Toast.makeText(this, "Da luu!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });

        // Nút Xóa
        Button btnDelete = findViewById(R.id.btnDelete);
        btnDelete.setOnClickListener(v -> confirmDelete());

        // Nút Scan lại
        Button btnRescan = findViewById(R.id.btnRescan);
        btnRescan.setOnClickListener(v -> {
            startActivity(new Intent(this, CameraActivity.class));
            finish();
        });

        if (invoiceId != -1) loadInvoice();
    }

    private void loadInvoice() {
        Invoice inv = invoiceDao.getInvoiceById(invoiceId);
        if (inv == null) { finish(); return; }

        // Hiện ảnh hóa đơn
        ImageView ivInvoice = findViewById(R.id.ivInvoice);
        if (inv.imagePath != null && !inv.imagePath.isEmpty()) {
            android.graphics.Bitmap bm = BitmapFactory.decodeFile(inv.imagePath);
            if (bm != null) ivInvoice.setImageBitmap(bm);
        }

        ((TextView) findViewById(R.id.tvSeller)).setText(
                inv.seller.isEmpty() ? "Khong ro" : inv.seller);
        ((TextView) findViewById(R.id.tvTimestamp)).setText(
                inv.timestamp.isEmpty() ? inv.createdAt : inv.timestamp);
        ((TextView) findViewById(R.id.tvTotal)).setText(
                String.format("%,.0f VND", (double) inv.totalCost));

        RecyclerView rv = findViewById(R.id.rvProducts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new ProductAdapter(inv.products));
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xoa hoa don")
                .setMessage("Ban co chac muon xoa khong?")
                .setPositiveButton("Xoa", (d, w) -> {
                    invoiceDao.deleteInvoice(invoiceId);
                    Toast.makeText(this, "Da xoa!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                })
                .setNegativeButton("Huy", null)
                .show();
    }
}