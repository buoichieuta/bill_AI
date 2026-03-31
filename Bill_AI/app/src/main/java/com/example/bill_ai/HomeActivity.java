package com.example.bill_ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bill_ai.adapter.InvoiceAdapter;
import com.example.bill_ai.db.InvoiceDao;
import com.example.bill_ai.model.Invoice;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private RecyclerView rvInvoices;
    private TextView tvEmpty, tvLogout, tvGreeting;
    private ImageButton btnCapture;
    private InvoiceDao invoiceDao;
    private InvoiceAdapter adapter;

    // Thêm các biến mới cho bộ lọc
    private TextView tvFilterAll, tvFilterEating, tvFilterTransport;
    private List<Invoice> allInvoices = new ArrayList<>(); // Lưu danh sách gốc chưa lọc

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        invoiceDao = new InvoiceDao(this);
        rvInvoices = findViewById(R.id.rvInvoices);
        tvEmpty    = findViewById(R.id.tvEmpty);
        tvLogout   = findViewById(R.id.tvLogout);
        tvGreeting = findViewById(R.id.tvGreeting);
        btnCapture = findViewById(R.id.btnCapture);

        // Ánh xạ các nút lọc
        tvFilterAll = findViewById(R.id.tvFilterAll);
        tvFilterEating = findViewById(R.id.tvFilterEating);
        tvFilterTransport = findViewById(R.id.tvFilterTransport);

        rvInvoices.setLayoutManager(new LinearLayoutManager(this));

        // Hiển thị tên user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            String name = user.getEmail().split("@")[0];
            tvGreeting.setText("Xin chao, " + name + "!");
        }

        // Nút scan
        btnCapture.setOnClickListener(v ->
                startActivity(new Intent(this, CameraActivity.class))
        );

        // Đăng xuất
        tvLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // ---------------- SỰ KIỆN BẤM NÚT LỌC ---------------- //
        tvFilterAll.setOnClickListener(v -> filterInvoices("All"));
        tvFilterEating.setOnClickListener(v -> filterInvoices("Eating"));
        tvFilterTransport.setOnClickListener(v -> filterInvoices("Transport"));
        // ----------------------------------------------------- //

        // Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                loadInvoices();
            } else if (id == R.id.nav_report) {
                // Màn hình báo cáo
            }
            return true;
        });

        loadInvoices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInvoices();
    }

    // Hàm load dữ liệu từ Database lên
    private void loadInvoices() {
        allInvoices = invoiceDao.getAllInvoices();
        filterInvoices("All"); // Lần đầu mở app lên thì hiển thị Tất cả
    }

    // Hàm Lọc dữ liệu
    private void filterInvoices(String category) {
        List<Invoice> filteredList = new ArrayList<>();

        if (category.equals("All")) {
            // Nếu chọn Tất cả thì lấy nguyên danh sách gốc
            filteredList.addAll(allInvoices);
        } else {
            // Nếu chọn Ăn uống/Di chuyển... thì tìm trong danh sách gốc xem cái nào khớp category
            for (Invoice inv : allInvoices) {
                if (category.equals(inv.category)) {
                    filteredList.add(inv);
                }
            }
        }

        // Cập nhật lên Giao diện
        if (filteredList.isEmpty()) {
            rvInvoices.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            rvInvoices.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            adapter = new InvoiceAdapter(filteredList, invoice ->
                    startActivity(new Intent(this, ResultActivity.class)
                            .putExtra("invoice_id", invoice.id))
            );
            rvInvoices.setAdapter(adapter);
        }
    }
}