package com.example.bill_ai;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bill_ai.adapter.InvoiceAdapter;
import com.example.bill_ai.model.Invoice;
import com.example.bill_ai.network.SupabaseManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import androidx.core.content.FileProvider;
import java.util.ArrayList;
import java.util.List;
import android.Manifest;
import android.os.Build;
import com.example.bill_ai.utils.NotificationHelper;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import androidx.core.util.Pair;

public class HomeActivity extends AppCompatActivity {

    // Biến tĩnh dùng cho hiệu ứng chuyển đổi theme
    private static android.graphics.Bitmap transitionBitmap = null;
    private static int toggleX = 0;
    private static int toggleY = 0;
    private static boolean isTransitioning = false;

    private RecyclerView rvRecentInvoices, rvInvoicesTab;
    private TextView tvEmptyRecent, tvEmptyInvoiceTab, tvGreeting, tvAiHint;
    private TextView tvSummarySpent, tvSummaryCount, tvSummarySpentDelta, tvSummaryCountDelta;
    private ImageButton btnCapture;
    private InvoiceAdapter adapter;
    private SupabaseManager supabase;

    private View nsvHome, nsvInvoice, nsvSetting, nsvReport;

    // Thêm các biến mới cho bộ lọc Invoice Tab
    private TextView tvFilterDateAll, tvFilterDateThisMonth, tvFilterDateLastMonth;
    private List<Invoice> allInvoices = new ArrayList<>(); // Lưu danh sách gốc chưa lọc

    // Biến lọc
    private String currentSearchText = "";
    private String selectedCategory = "Tất cả";
    private String selectedDateFilter = "All";
    private android.widget.EditText etSearchInvoicesTab;
    private android.view.View btnCategorySelect;
    private TextView tvCategorySelect;

    // Report Tab components
    private PieChart pieChart;
    private BarChart barChart;
    private android.widget.LinearLayout pieLegend;
    private TextView tvReportDay, tvReportWeek, tvReportMonth, tvReportAll, tvCustomRangeText;
    private String currentReportFilter = "Month";
    private long customStart = 0, customEnd = 0;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        Locale locale = new Locale("vi", "VN");
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        supabase = new SupabaseManager(this);
        if (supabase.getCurrentUserId() == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        rvRecentInvoices = findViewById(R.id.rvRecentInvoices);
        tvEmptyRecent    = findViewById(R.id.tvEmptyRecent);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvAiHint   = findViewById(R.id.tvAiHint);
        btnCapture = findViewById(R.id.btnCapture);
        
        nsvHome = findViewById(R.id.nsvHome);
        nsvInvoice = findViewById(R.id.nsvInvoice);
        nsvSetting = findViewById(R.id.nsvSetting);
        nsvReport = findViewById(R.id.nsvReport);



        rvInvoicesTab = findViewById(R.id.rvInvoicesTab);
        tvEmptyInvoiceTab = findViewById(R.id.tvEmptyInvoiceTab);

        // Summary Cards
        tvSummarySpent = findViewById(R.id.tvSummarySpent);
        tvSummaryCount = findViewById(R.id.tvSummaryCount);
        tvSummarySpentDelta = findViewById(R.id.tvSummarySpentDelta);
        tvSummaryCountDelta = findViewById(R.id.tvSummaryCountDelta);

        // Nút lọc Invoices Tab
        tvFilterDateAll = findViewById(R.id.tvFilterDateAll);
        tvFilterDateThisMonth = findViewById(R.id.tvFilterDateThisMonth);
        tvFilterDateLastMonth = findViewById(R.id.tvFilterDateLastMonth);

        etSearchInvoicesTab = findViewById(R.id.etSearchInvoicesTab);
        btnCategorySelect = findViewById(R.id.btnCategorySelect);
        tvCategorySelect = findViewById(R.id.tvCategorySelect);

        // Click vào avatar để vào cài đặt
        findViewById(R.id.ivAvatarHome).setOnClickListener(v -> {
            BottomNavigationView bNav = findViewById(R.id.bottomNav);
            bNav.setSelectedItemId(R.id.nav_setting);
        });

        // Report Tab Initialization
        pieChart = findViewById(R.id.pieChart);
        barChart = findViewById(R.id.barChart);
        pieLegend = findViewById(R.id.pieLegend);
        tvReportDay = findViewById(R.id.tvReportDay);
        tvReportWeek = findViewById(R.id.tvReportWeek);
        tvReportMonth = findViewById(R.id.tvReportMonth);
        tvReportAll = findViewById(R.id.tvReportAll);
        tvCustomRangeText = findViewById(R.id.tvCustomRangeText);

        if (tvReportDay != null) {
            tvReportDay.setOnClickListener(v -> { currentReportFilter = "Day"; setupReportTab(); });
            tvReportWeek.setOnClickListener(v -> { currentReportFilter = "Week"; setupReportTab(); });
            tvReportMonth.setOnClickListener(v -> { currentReportFilter = "Month"; setupReportTab(); });
            tvReportAll.setOnClickListener(v -> { currentReportFilter = "All"; setupReportTab(); });
        }
        
        View btnCustomRange = findViewById(R.id.btnCustomRange);
        if (btnCustomRange != null) {
            btnCustomRange.setOnClickListener(v -> openDateRangePicker());
        }

        View btnExportExcel = findViewById(R.id.btnExportExcel);
        if (btnExportExcel != null) {
            btnExportExcel.setOnClickListener(v -> exportToExcel());
        }

        rvRecentInvoices.setLayoutManager(new LinearLayoutManager(this));
        rvInvoicesTab.setLayoutManager(new LinearLayoutManager(this));

        // Hiển thị tên user
        String email = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getString("email", "User");
        String fullName = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getString("full_name", "");
        String displayName = (fullName != null && !fullName.isEmpty()) ? fullName : email.split("@")[0];
        tvGreeting.setText(displayName + "! 👋");

        // Nút scan
        btnCapture.setOnClickListener(v ->
                startActivity(new Intent(this, CameraActivity.class))
        );

        // ---------------- SỰ KIỆN BẤM NÚT LỌC INVOICES TAB ---------------- //
        tvFilterDateAll.setOnClickListener(v -> filterInvoicesTab("All"));
        tvFilterDateThisMonth.setOnClickListener(v -> filterInvoicesTab("ThisMonth"));
        tvFilterDateLastMonth.setOnClickListener(v -> filterInvoicesTab("LastMonth"));

        // Tìm kiếm
        etSearchInvoicesTab.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchText = s.toString().toLowerCase().trim();
                applyFilters();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // Chọn phân loại
        btnCategorySelect.setOnClickListener(v -> showCategoryDialog());

        // ------------------------------------------------------------------ //

        // Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                nsvHome.setVisibility(View.VISIBLE);
                nsvInvoice.setVisibility(View.GONE);
                nsvSetting.setVisibility(View.GONE);
                nsvReport.setVisibility(View.GONE);
                loadInvoices(); // Refresh
            } else if (id == R.id.nav_invoice) {
                nsvHome.setVisibility(View.GONE);
                nsvInvoice.setVisibility(View.VISIBLE);
                nsvSetting.setVisibility(View.GONE);
                nsvReport.setVisibility(View.GONE);
                filterInvoicesTab("All");
            } else if (id == R.id.nav_report) {
                nsvHome.setVisibility(View.GONE);
                nsvInvoice.setVisibility(View.GONE);
                nsvSetting.setVisibility(View.GONE);
                nsvReport.setVisibility(View.VISIBLE);
                setupReportTab();
            } else if (id == R.id.nav_setting) {
                nsvHome.setVisibility(View.GONE);
                nsvInvoice.setVisibility(View.GONE);
                nsvSetting.setVisibility(View.VISIBLE);
                nsvReport.setVisibility(View.GONE);
                setupSettingsView();
            }
            return true;
        });

        loadInvoices();
        loadSavedAvatar();

        setupAiBubble();


        // Khôi phục trạng thái tab nếu có
        if (savedInstanceState != null) {
            int selectedItemId = savedInstanceState.getInt("selected_nav_id", R.id.nav_home);
            bottomNav.setSelectedItemId(selectedItemId);
            
            nsvHome.setVisibility(selectedItemId == R.id.nav_home ? View.VISIBLE : View.GONE);
            nsvInvoice.setVisibility(selectedItemId == R.id.nav_invoice ? View.VISIBLE : View.GONE);
            nsvSetting.setVisibility(selectedItemId == R.id.nav_setting ? View.VISIBLE : View.GONE);
            nsvReport.setVisibility(selectedItemId == R.id.nav_report ? View.VISIBLE : View.GONE);
            
            if (selectedItemId == R.id.nav_setting) setupSettingsView();
            if (selectedItemId == R.id.nav_invoice) filterInvoicesTab("All");
        }

        // Kiểm tra và chạy hiệu ứng chuyển đổi theme
        if (isTransitioning && transitionBitmap != null) {
            runThemeTransition();
        }

        // Yêu cầu quyền thông báo (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102);
            }
        }
        
        // Gọi hàm kiểm tra cập nhật ngầm
        com.example.bill_ai.network.UpdateManager.checkForUpdatesSilent(this, (hasUpdate, versionName, releaseNotes, apkUrl) -> {
            if (hasUpdate) {
                // Hiển thị chấm đỏ trên Bottom Nav
                BottomNavigationView bNav = findViewById(R.id.bottomNav);
                com.google.android.material.badge.BadgeDrawable badge = bNav.getOrCreateBadge(R.id.nav_setting);
                badge.setVisible(true);
                badge.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));

                // Bắn 1 Notification vào Lịch sử nếu là version mới chưa từng báo
                android.content.SharedPreferences prefs = getSharedPreferences("supabase_prefs", MODE_PRIVATE);
                String lastNotified = prefs.getString("last_notified_update", "");
                if (!lastNotified.equals(versionName)) {
                    prefs.edit().putString("last_notified_update", versionName).apply();
                    
                    String uid = supabase.getCurrentUserId();
                    if (uid != null) {
                        com.example.bill_ai.db.InvoiceDao dao = new com.example.bill_ai.db.InvoiceDao(this);
                        com.example.bill_ai.model.Notification notif = new com.example.bill_ai.model.Notification(
                                uid,
                                "Có bản cập nhật mới: " + versionName,
                                "Phiên bản mới mang đến:\n" + releaseNotes,
                                new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date())
                        );
                        dao.addNotification(notif);
                    }
                    
                    // Lần đầu tiên phát hiện phiên bản này trong phiên làm việc, tự động pop up cho lịch sự
                    com.example.bill_ai.network.UpdateManager.showUpdateDialog(this, versionName, releaseNotes, apkUrl);
                }

                // Lưu trạng thái để màn hình Setting tiện dùng
                prefs.edit()
                     .putBoolean("has_update", true)
                     .putString("update_version_name", versionName)
                     .putString("update_release_notes", releaseNotes)
                     .putString("update_apk_url", apkUrl)
                     .apply();
                     
                // Cập nhật lại UI Cài đặt ngay lập tức nếu người dùng đang ở tab Cài đặt
                View settingsLayout = findViewById(R.id.nsvSetting);
                if (settingsLayout != null && settingsLayout.getVisibility() == View.VISIBLE) {
                    runOnUiThread(this::setupSettingsView);
                }
            } else {
                getSharedPreferences("supabase_prefs", MODE_PRIVATE).edit().putBoolean("has_update", false).apply();
                BottomNavigationView bNav = findViewById(R.id.bottomNav);
                bNav.removeBadge(R.id.nav_setting);
            }
        });
    }

    private void runThemeTransition() {
        ImageView ivOverlayOld = findViewById(R.id.ivThemeTransitionOld);
        ImageView ivOverlayNew = findViewById(R.id.ivThemeTransition);
        if (ivOverlayOld == null || ivOverlayNew == null || transitionBitmap == null) return;

        // 1. Hiện hình ảnh CŨ làm nền
        ivOverlayOld.setImageBitmap(transitionBitmap);
        ivOverlayOld.setVisibility(View.VISIBLE);

        // 2. Chờ một nhịp để đảm bảo giao diện mới đã sẵn sàng
        ivOverlayNew.post(() -> {
            View rootView = getWindow().getDecorView().getRootView();
            int width = rootView.getWidth();
            int height = rootView.getHeight();
            if (width <= 0 || height <= 0) return;

            // Tạm ẩn overlay để chụp giao diện "mới" sạch sẽ bên dưới
            ivOverlayOld.setVisibility(View.INVISIBLE);
            ivOverlayNew.setVisibility(View.INVISIBLE);

            // Chụp màn hình bằng Canvas (ổn định hơn DrawingCache)
            android.graphics.Bitmap newBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(newBitmap);
            rootView.draw(canvas);

            // Hiện lại lớp cũ làm nền, đặt lớp mới lên trên để chuẩn bị bung ra
            ivOverlayOld.setVisibility(View.VISIBLE);
            ivOverlayNew.setImageBitmap(newBitmap);
            ivOverlayNew.setVisibility(View.VISIBLE);

            float finalRadius = (float) Math.hypot(width, height);

            // Hiệu ứng "BUNG RA" từ vị trí nút bấm
            android.animation.Animator anim = android.view.ViewAnimationUtils.createCircularReveal(
                    ivOverlayNew, toggleX, toggleY, 0, finalRadius);
            
            anim.setDuration(850); // Tăng thời gian một chút để thấy rõ hiệu ứng bung
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    ivOverlayOld.setVisibility(View.GONE);
                    ivOverlayNew.setVisibility(View.GONE);
                    ivOverlayOld.setImageBitmap(null);
                    ivOverlayNew.setImageBitmap(null);
                    transitionBitmap = null;
                    isTransitioning = false;
                }
            });
            anim.start();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            outState.putInt("selected_nav_id", bottomNav.getSelectedItemId());
        }
    }

    private void loadSavedAvatar() {
        String userId = supabase.getCurrentUserId();
        if (userId != null) {
            String avatarUrl = getSharedPreferences("supabase_prefs", MODE_PRIVATE)
                    .getString("avatar_url", null);
            long lastUpdated = getSharedPreferences("prefs_" + userId, MODE_PRIVATE)
                    .getLong("avatar_last_updated", 0);
            
            ImageView ivHome = findViewById(R.id.ivAvatarHome);
            ImageView ivSettings = findViewById(R.id.ivAvatarSettings);

            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                // Thêm signature có chứa timestamp để buộc Glide tải lại khi ảnh đổi
                if (ivHome != null) Glide.with(this).load(avatarUrl)
                        .signature(new ObjectKey(lastUpdated))
                        .transform(new CircleCrop()).into(ivHome);
                if (ivSettings != null) Glide.with(this).load(avatarUrl)
                        .signature(new ObjectKey(lastUpdated))
                        .transform(new CircleCrop()).into(ivSettings);
            } else {
                // Thử load từ local path cũ nếu còn
                String savedPath = getSharedPreferences("prefs_" + userId, MODE_PRIVATE)
                        .getString("avatar_path", null);
                if (savedPath != null) {
                    File file = new File(savedPath);
                    if (file.exists()) {
                        if (ivHome != null) Glide.with(this).load(file).transform(new CircleCrop()).into(ivHome);
                        if (ivSettings != null) Glide.with(this).load(file).transform(new CircleCrop()).into(ivSettings);
                    }
                }
            }
        }
    }

    // Thiết lập view cài đặt
    private void setupSettingsView() {
        View settingsLayout = findViewById(R.id.nsvSetting);
        String fullName = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getString("full_name", "Phan Nhật Hào");
        String email = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getString("email", "Email");

        ((android.widget.TextView)settingsLayout.findViewById(R.id.tvNameSettings)).setText(fullName);
        ((android.widget.TextView)settingsLayout.findViewById(R.id.tvEmailSettings)).setText(email);

        settingsLayout.findViewById(R.id.btnChangePassword).setOnClickListener(v -> showChangePasswordDialog());
        settingsLayout.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            supabase.signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        settingsLayout.findViewById(R.id.btnBackFromSettings).setOnClickListener(v -> {
            BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
            bottomNav.setSelectedItemId(R.id.nav_home);
        });

        // Nút đổi ảnh đại diện
        findViewById(R.id.ivAvatarSettings).setOnClickListener(v -> openGalleryForAvatar());

        // Chế độ tối (Switch)
        com.google.android.material.materialswitch.MaterialSwitch swDarkMode = findViewById(R.id.swDarkMode);
        int currentMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        swDarkMode.setChecked(currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        
        // Bấm vào cả dòng hoặc Switch đều kích hoạt
        findViewById(R.id.layoutDarkModeToggle).setOnClickListener(v -> toggleDarkMode());
        swDarkMode.setOnClickListener(v -> toggleDarkMode());

        // Ngôn ngữ
        findViewById(R.id.btnLanguage).setOnClickListener(v -> showLanguageDialog());


        // Lịch sử thông báo
        findViewById(R.id.btnNotifications).setOnClickListener(v -> startActivity(new Intent(this, NotificationHistoryActivity.class)));

        // Cập nhật giao diện Bảo mật tài khoản thành Phiên bản
        boolean hasUpdate = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getBoolean("has_update", false);
        TextView tvBadge = settingsLayout.findViewById(R.id.tvUpdateBadgeSettings);
        if (tvBadge != null) {
            tvBadge.setVisibility(hasUpdate ? View.VISIBLE : View.GONE);
        }

        settingsLayout.findViewById(R.id.btnSecurity).setOnClickListener(v -> {
            boolean currentHasUpdate = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getBoolean("has_update", false);
            if (currentHasUpdate) {
                String ver = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getString("update_version_name", "");
                String notes = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getString("update_release_notes", "");
                String url = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getString("update_apk_url", "");
                com.example.bill_ai.network.UpdateManager.showUpdateDialog(this, ver, notes, url);
            } else {
                android.widget.Toast.makeText(this, "Bạn đang sử dụng phiên bản mới nhất!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnHelp).setOnClickListener(v -> {
            Intent intent = new Intent(this, HelpFeedbackActivity.class);
            startActivity(intent);
        });

        // AI Theme Selection
        findViewById(R.id.btnAiTheme).setOnClickListener(v -> showAiThemeDialog());
        updateAiThemeStatus();
    }

    private void showAiThemeDialog() {
        String[] themes = {"Mặc định (Kính mờ)", "Sáng", "Tối"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chọn chủ đề AI gợi ý")
                .setItems(themes, (dialog, which) -> {
                    String theme = (which == 0) ? "glass" : (which == 1 ? "light" : "dark");
                    getSharedPreferences("supabase_prefs", MODE_PRIVATE).edit().putString("ai_theme", theme).apply();
                    applyAiTheme(theme);
                    updateAiThemeStatus();
                }).show();
    }

    private void updateAiThemeStatus() {
        String theme = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getString("ai_theme", "glass");
        TextView tvStatus = findViewById(R.id.tvAiThemeStatus);
        if (tvStatus != null) {
            String status = "Mặc định";
            if (theme.equals("light")) status = "Sáng";
            else if (theme.equals("dark")) status = "Tối";
            tvStatus.setText(status);
        }
        applyAiTheme(theme);
    }

    private void applyAiTheme(String theme) {
        androidx.cardview.widget.CardView cvAi = findViewById(R.id.cvAiSuggestion);
        TextView tvHint = findViewById(R.id.tvAiHint);
        if (cvAi == null || tvHint == null) return;

        switch (theme) {
            case "light":
                cvAi.setCardBackgroundColor(getResources().getColor(R.color.ai_card_light));
                tvHint.setTextColor(getResources().getColor(R.color.ai_card_light_text));
                break;
            case "dark":
                cvAi.setCardBackgroundColor(getResources().getColor(R.color.ai_card_dark));
                tvHint.setTextColor(getResources().getColor(R.color.ai_card_dark_text));
                break;
            default: // glass (themed)
                cvAi.setCardBackgroundColor(getResources().getColor(R.color.app_surface));
                tvHint.setTextColor(getResources().getColor(R.color.app_text_sub));
                break;
        }
    }




    private void toggleDarkMode() {
        // 1. Chụp ảnh màn hình hiện tại
        View rootView = getWindow().getDecorView().getRootView();
        rootView.setDrawingCacheEnabled(true);
        transitionBitmap = android.graphics.Bitmap.createBitmap(rootView.getDrawingCache());
        rootView.setDrawingCacheEnabled(false);

        // 2. Lấy vị trí nút bấm (Switch) để bung hiệu ứng
        View btn = findViewById(R.id.swDarkMode);
        if (btn != null) {
            int[] location = new int[2];
            btn.getLocationOnScreen(location);
            toggleX = location[0] + btn.getWidth() / 2;
            toggleY = location[1] + btn.getHeight() / 2;
        } else {
            toggleX = rootView.getWidth() / 2;
            toggleY = rootView.getHeight() / 2;
        }
        
        isTransitioning = true;

        // 3. Đổi theme
        int currentMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        int newMode = (currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) 
                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO 
                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(newMode);
    }

    private void showLanguageDialog() {
        String[] languages = {"Tiếng Việt", "English"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chọn ngôn ngữ")
                .setItems(languages, (dialog, which) -> {
                    // Logic đổi locale ở đây (cần ContextWrapper hoặc restart Activity)
                    android.widget.Toast.makeText(this, "Đã chọn: " + languages[which], android.widget.Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showChangePasswordDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        android.widget.EditText etOld = new android.widget.EditText(this);
        etOld.setHint("Mật khẩu cũ");
        etOld.setPadding(20, 20, 20, 20);
        etOld.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etOld);

        android.widget.EditText etNew = new android.widget.EditText(this);
        etNew.setHint("Mật khẩu mới");
        etNew.setPadding(20, 40, 20, 20);
        etNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etNew);

        android.widget.EditText etConfirm = new android.widget.EditText(this);
        etConfirm.setHint("Xác nhận mật khẩu mới");
        etConfirm.setPadding(20, 40, 20, 20);
        etConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etConfirm);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Đổi mật khẩu")
                .setView(layout)
                .setPositiveButton("Cập nhật", (dialog, which) -> {
                    String oldPass = etOld.getText().toString();
                    String newPass = etNew.getText().toString();
                    String confirm = etConfirm.getText().toString();

                    if (newPass.length() < 6) {
                        android.widget.Toast.makeText(this, "Mật khẩu mới phải ít nhất 6 ký tự", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPass.equals(confirm)) {
                        android.widget.Toast.makeText(this, "Mật khẩu xác nhận không khớp", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Bước 1: Xác minh mật khẩu cũ bằng cách đăng nhập thử
                    String email = getSharedPreferences("supabase_prefs", MODE_PRIVATE).getString("email", "");
                    supabase.signIn(email, oldPass, new SupabaseManager.AuthCallback() {
                        @Override
                        public void onSuccess(String userId, String e) {
                            // Bước 2: Nếu đúng mật khẩu cũ, tiến hành cập nhật mật khẩu mới
                            supabase.updateUserPassword(newPass, new SupabaseManager.AuthCallback() {
                                @Override
                                public void onSuccess(String id, String mail) {
                                    runOnUiThread(() -> android.widget.Toast.makeText(HomeActivity.this, "Đổi mật khẩu thành công!", android.widget.Toast.LENGTH_SHORT).show());
                                }
                                @Override
                                public void onError(String msg) {
                                    runOnUiThread(() -> android.widget.Toast.makeText(HomeActivity.this, "Lỗi: " + msg, android.widget.Toast.LENGTH_SHORT).show());
                                }
                            });
                        }
                        @Override
                        public void onError(String msg) {
                            runOnUiThread(() -> android.widget.Toast.makeText(HomeActivity.this, "Mật khẩu cũ không chính xác!", android.widget.Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Mở thư viện chọn avatar
    private void openGalleryForAvatar() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        avatarLauncher.launch(intent);
    }

    // Launcher cho avatar
    private final ActivityResultLauncher<Intent> avatarLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri == null) return;

                            String userId = supabase.getCurrentUserId();
                            if (userId == null) return;

                            // Mở hộp thoại chờ
                            android.app.ProgressDialog pd = new android.app.ProgressDialog(HomeActivity.this);
                            pd.setMessage("Đang tải ảnh lên...");
                            pd.setCancelable(false);
                            pd.show();

                            File internalFile = new File(getFilesDir(), "profile_avatar.jpg");
                            try (InputStream is = getContentResolver().openInputStream(uri);
                                 OutputStream os = new FileOutputStream(internalFile)) {
                                byte[] buf = new byte[1024];
                                int len;
                                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                            } catch (Exception e) { 
                                pd.dismiss();
                                e.printStackTrace(); 
                                return; 
                            }

                            // Upload lên Supabase Storage với tên file duy nhất (timestamp)
                            long timestamp = System.currentTimeMillis();
                            String fileName = "avatar_" + userId + "_" + timestamp + ".jpg";
                            
                            supabase.uploadFile(internalFile, "avatars", fileName, new SupabaseManager.AuthCallback() {
                                @Override
                                public void onSuccess(String url, String email) {
                                    // BƯỚC 2: Cập nhật URL mới vào User Metadata trên Supabase Auth để đồng bộ đám mây
                                    org.json.JSONObject metadata = new org.json.JSONObject();
                                    try { metadata.put("avatar_url", url); } catch (Exception e) {}
                                    
                                    supabase.updateUserMetadata(metadata, new SupabaseManager.AuthCallback() {
                                        @Override
                                        public void onSuccess(String id, String m) {
                                            runOnUiThread(() -> {
                                                pd.dismiss();
                                                // Lưu timestamp và UI local (prefs đã được update bên trong supabase.updateUserMetadata)
                                                getSharedPreferences("prefs_" + userId, MODE_PRIVATE)
                                                        .edit()
                                                        .putLong("avatar_last_updated", timestamp)
                                                        .apply();
                                                
                                                // Load lên giao diện bằng Glide
                                                ImageView ivHome = findViewById(R.id.ivAvatarHome);
                                                ImageView ivSettings = findViewById(R.id.ivAvatarSettings);
                                                if (ivHome != null) Glide.with(HomeActivity.this).load(url)
                                                        .transform(new CircleCrop()).into(ivHome);
                                                if (ivSettings != null) Glide.with(HomeActivity.this).load(url)
                                                        .transform(new CircleCrop()).into(ivSettings);
                                                
                                                android.widget.Toast.makeText(HomeActivity.this, "Đã cập nhật và đồng bộ ảnh đại diện!", android.widget.Toast.LENGTH_SHORT).show();
                                            });
                                        }

                                        @Override
                                        public void onError(String msg) {
                                            runOnUiThread(() -> {
                                                pd.dismiss();
                                                android.widget.Toast.makeText(HomeActivity.this, "Lưu URL thất bại: " + msg, android.widget.Toast.LENGTH_SHORT).show();
                                            });
                                        }
                                    });
                                }

                                @Override
                                public void onError(String message) {
                                    runOnUiThread(() -> {
                                        pd.dismiss();
                                        new androidx.appcompat.app.AlertDialog.Builder(HomeActivity.this)
                                                .setTitle("Lỗi tải ảnh lên")
                                                .setMessage(message + "\n\nLưu ý: Bạn hãy kiểm tra lại kết nối mạng hoặc thử lại sau.")
                                                .setPositiveButton("Đã hiểu", null)
                                                .show();
                                    });
                                }
                            });
                        }
                    });

    private final android.os.Handler sessionHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sessionChecker = new Runnable() {
        @Override
        public void run() {
            supabase.fetchCurrentUser(new SupabaseManager.AuthCallback() {
                @Override
                public void onSuccess(String serverDeviceId, String avatarUrl) {
                    runOnUiThread(() -> {
                        String localDeviceId = supabase.getLocalDeviceId();
                        if (serverDeviceId == null || serverDeviceId.isEmpty()) {
                            // Tài khoản cũ, cập nhật device_id để chiếm phiên
                            try {
                                org.json.JSONObject meta = new org.json.JSONObject();
                                meta.put("device_id", localDeviceId);
                                supabase.updateUserMetadata(meta, new SupabaseManager.AuthCallback() {
                                    @Override public void onSuccess(String id, String m) {}
                                    @Override public void onError(String msg) {}
                                });
                            } catch (Exception e) {}
                        } else if (!serverDeviceId.equals(localDeviceId)) {
                            android.widget.Toast.makeText(HomeActivity.this, "Tài khoản của bạn đã được đăng nhập trên một thiết bị khác!", android.widget.Toast.LENGTH_LONG).show();
                            supabase.signOut();
                            startActivity(new Intent(HomeActivity.this, MainActivity.class));
                            finish();
                        } else if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            loadSavedAvatar();
                        }
                    });
                }
                @Override
                public void onError(String message) {
                    if ("BLOCKED".equals(message)) {
                        runOnUiThread(() -> {
                            android.widget.Toast.makeText(HomeActivity.this, "Tài khoản của bạn đã bị khóa!", android.widget.Toast.LENGTH_LONG).show();
                            supabase.signOut();
                            startActivity(new Intent(HomeActivity.this, MainActivity.class));
                            finish();
                        });
                    }
                }
            });
            sessionHandler.postDelayed(this, 10000); // lặp lại mỗi 10s
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        loadInvoices();
        sessionHandler.post(sessionChecker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sessionHandler.removeCallbacks(sessionChecker);
    }

    // Hàm load dữ liệu từ Database lên
    private void loadInvoices() {
        supabase.getAllInvoices(new SupabaseManager.InvoiceListCallback() {
            @Override
            public void onSuccess(List<Invoice> list) {
                runOnUiThread(() -> {
            allInvoices = list;
            // Update Recent Bills in Home Tab (Max 3)
            List<Invoice> recentList = list.size() > 3 ? list.subList(0, 3) : list;
            if (recentList.isEmpty()) {
                rvRecentInvoices.setVisibility(View.GONE);
                tvEmptyRecent.setVisibility(View.VISIBLE);
            } else {
                rvRecentInvoices.setVisibility(View.VISIBLE);
                tvEmptyRecent.setVisibility(View.GONE);
                rvRecentInvoices.setAdapter(new InvoiceAdapter(recentList, invoice ->
                        startActivity(new Intent(HomeActivity.this, ResultActivity.class)
                                .putExtra("invoice_id", invoice.id))
                ));
            }
            
            // Update Invoices Tab
            filterInvoicesTab("All");
            
            updateSummary(list);
        });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(HomeActivity.this, "Lỗi tải hóa đơn: " + message, android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateSummary(List<Invoice> list) {
        double totalSpentMonth = 0;
        int countMonth = 0;
        double totalSpentLastMonth = 0;
        double spentToday = 0;
        double spentYesterday = 0;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int currentMonth = cal.get(java.util.Calendar.MONTH);
        int currentYear = cal.get(java.util.Calendar.YEAR);
        int currentDay = cal.get(java.util.Calendar.DAY_OF_MONTH);

        java.util.Calendar calLast = (java.util.Calendar) cal.clone();
        calLast.add(java.util.Calendar.MONTH, -1);
        int lastMonth = calLast.get(java.util.Calendar.MONTH);
        int lastMonthYear = calLast.get(java.util.Calendar.YEAR);

        java.util.Calendar calYesterday = (java.util.Calendar) cal.clone();
        calYesterday.add(java.util.Calendar.DATE, -1);
        int yesterdayDay = calYesterday.get(java.util.Calendar.DAY_OF_MONTH);
        int yesterdayMonth = calYesterday.get(java.util.Calendar.MONTH);
        int yesterdayYear = calYesterday.get(java.util.Calendar.YEAR);

        for (Invoice inv : list) {
            if (inv.timestamp == null || inv.timestamp.isEmpty()) continue;
            try {
                // Định dạng dd/MM/yyyy HH:mm
                String datePart = inv.timestamp.split(" ")[0];
                String[] parts = datePart.split("/");
                int d = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]) - 1; // Calendar month is 0-based
                int y = Integer.parseInt(parts[2]);

                if (m == currentMonth && y == currentYear) {
                    totalSpentMonth += inv.totalCost;
                    countMonth++;
                    if (d == currentDay) spentToday += inv.totalCost;
                } else if (m == lastMonth && y == lastMonthYear) {
                    totalSpentLastMonth += inv.totalCost;
                }

                if (d == yesterdayDay && m == yesterdayMonth && y == yesterdayYear) {
                    spentYesterday += inv.totalCost;
                }
            } catch (Exception e) {}
        }

        tvSummarySpent.setText(String.format("%,.0f đ", totalSpentMonth).replace(",", "."));
        tvSummaryCount.setText(String.valueOf(countMonth));

        // AI Hint logic
        if (tvAiHint != null) {
            double percentChange = spentYesterday > 0 ? ((spentToday - spentYesterday) / spentYesterday) * 100 : 0;
            String trend = percentChange >= 0 ? "cao hơn" : "thấp hơn";
            tvAiHint.setText(String.format("Bạn đã chi %,.0f đ hôm nay — %s %.0f%% so với hôm qua.", 
                spentToday, trend, Math.abs(percentChange)).replace(",", "."));
        }

        // Cập nhật phần trăm so với tháng trước
        if (tvSummarySpentDelta != null) {
            if (totalSpentLastMonth > 0) {
                double delta = ((totalSpentMonth - totalSpentLastMonth) / totalSpentLastMonth) * 100;
                String sign = delta >= 0 ? "+" : "";
                tvSummarySpentDelta.setText(String.format("%s%.0f%% so với tháng trước", sign, delta));
                if (delta >= 0) {
                    tvSummarySpentDelta.setTextColor(getResources().getColor(R.color.spent_red));
                } else {
                    tvSummarySpentDelta.setTextColor(android.graphics.Color.parseColor("#4CAF50")); // Xanh lá nếu chi tiêu giảm
                }
            } else {
                tvSummarySpentDelta.setText("N/A so với tháng trước");
            }
        }
    }



    private void filterInvoicesTab(String dateFilter) {
        selectedDateFilter = dateFilter;
        applyFilters();
    }

    private void applyFilters() {
        if (tvFilterDateAll == null) return;

        // 1. Cập nhật UI nút Ngày
        int colorMain = getResources().getColor(R.color.app_text_main);
        int colorSelected = android.graphics.Color.WHITE;
        android.content.res.ColorStateList bgTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#195585"));

        tvFilterDateAll.setBackgroundResource(R.drawable.bg_filter_chip);
        tvFilterDateAll.setBackgroundTintList(null);
        tvFilterDateAll.setTextColor(colorMain);
        tvFilterDateThisMonth.setBackgroundResource(R.drawable.bg_filter_chip);
        tvFilterDateThisMonth.setBackgroundTintList(null);
        tvFilterDateThisMonth.setTextColor(colorMain);
        tvFilterDateLastMonth.setBackgroundResource(R.drawable.bg_filter_chip);
        tvFilterDateLastMonth.setBackgroundTintList(null);
        tvFilterDateLastMonth.setTextColor(colorMain);

        if (selectedDateFilter.equals("All")) {
            tvFilterDateAll.setBackgroundResource(R.drawable.bg_search_rounded);
            tvFilterDateAll.setBackgroundTintList(bgTint);
            tvFilterDateAll.setTextColor(colorSelected);
        } else if (selectedDateFilter.equals("ThisMonth")) {
            tvFilterDateThisMonth.setBackgroundResource(R.drawable.bg_search_rounded);
            tvFilterDateThisMonth.setBackgroundTintList(bgTint);
            tvFilterDateThisMonth.setTextColor(colorSelected);
        } else if (selectedDateFilter.equals("LastMonth")) {
            tvFilterDateLastMonth.setBackgroundResource(R.drawable.bg_search_rounded);
            tvFilterDateLastMonth.setBackgroundTintList(bgTint);
            tvFilterDateLastMonth.setTextColor(colorSelected);
        }

        // 2. Lọc danh sách
        List<Invoice> filteredList = new ArrayList<>();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int thisMonth = cal.get(java.util.Calendar.MONTH);
        int thisYear = cal.get(java.util.Calendar.YEAR);
        
        cal.add(java.util.Calendar.MONTH, -1);
        int lastMonth = cal.get(java.util.Calendar.MONTH);
        int lastMonthYear = cal.get(java.util.Calendar.YEAR);

        for (Invoice inv : allInvoices) {
            // Lọc theo Ngày
            boolean dateMatch = true;
            if (!selectedDateFilter.equals("All")) {
                dateMatch = isDateInFilter(inv.timestamp, selectedDateFilter, thisMonth, thisYear, lastMonth, lastMonthYear);
            }
            if (!dateMatch) continue;

            // Lọc theo Phân loại
            if (!selectedCategory.equals("Tất cả")) {
                if (!inv.category.equalsIgnoreCase(selectedCategory)) continue;
            }

            // Lọc theo Tìm kiếm (Tên seller hoặc địa chỉ)
            if (!currentSearchText.isEmpty()) {
                boolean searchMatch = inv.seller.toLowerCase().contains(currentSearchText) 
                                   || inv.address.toLowerCase().contains(currentSearchText);
                if (!searchMatch) continue;
            }

            filteredList.add(inv);
        }

        // 3. Hiển thị
        if (filteredList.isEmpty()) {
            rvInvoicesTab.setVisibility(View.GONE);
            tvEmptyInvoiceTab.setVisibility(View.VISIBLE);
        } else {
            rvInvoicesTab.setVisibility(View.VISIBLE);
            tvEmptyInvoiceTab.setVisibility(View.GONE);
            adapter = new InvoiceAdapter(filteredList, invoice ->
                    startActivity(new Intent(this, ResultActivity.class)
                            .putExtra("invoice_id", invoice.id))
            );
            rvInvoicesTab.setAdapter(adapter);
        }
    }

    private boolean isDateInFilter(String timestamp, String filter, int thisMonth, int thisYear, int lastMonth, int lastMonthYear) {
        if (timestamp == null || timestamp.isEmpty()) return false;
        try {
            // Định dạng dd/MM/yyyy HH:mm
            String datePart = timestamp.split(" ")[0];
            String[] parts = datePart.split("/");
            int d = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1; // Calendar month is 0-based
            int y = Integer.parseInt(parts[2]);

            if (filter.equals("ThisMonth")) {
                return (m == thisMonth && y == thisYear);
            } else if (filter.equals("LastMonth")) {
                return (m == lastMonth && y == lastMonthYear);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    private void showCategoryDialog() {
        String[] categories = {"Tất cả", "Ăn uống", "Mua sắm", "Di chuyển", "Y tế", "Giải trí", "Khác"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chọn phân loại")
                .setItems(categories, (dialog, which) -> {
                    selectedCategory = categories[which];
                    tvCategorySelect.setText(selectedCategory);
                    applyFilters();
                })
                .show();
    }



    private float dX, dY;
    private static final int CLICK_DRAG_TOLERANCE = 10; // pixels

    private void setupAiBubble() {
        View fbAi = findViewById(R.id.fbAiAssistant);
        if (fbAi == null) return;

        fbAi.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;

            @Override
            public boolean onTouch(View view, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        dX = view.getX() - startX;
                        dY = view.getY() - startY;
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        view.setX(event.getRawX() + dX);
                        view.setY(event.getRawY() + dY);
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        float endX = event.getRawX();
                        float endY = event.getRawY();
                        if (Math.abs(endX - startX) < CLICK_DRAG_TOLERANCE && Math.abs(endY - startY) < CLICK_DRAG_TOLERANCE) {
                            // Clicks
                            startActivity(new android.content.Intent(HomeActivity.this, ChatActivity.class));
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });
    }
    private void setupReportTab() {
        if (tvReportDay == null) return;

        // 1. Update Chips UI
        int colorMain = getResources().getColor(R.color.app_text_main);
        int colorSelected = android.graphics.Color.WHITE;
        android.content.res.ColorStateList bgTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#195585"));

        tvReportDay.setBackgroundResource(R.drawable.bg_filter_chip);
        tvReportDay.setBackgroundTintList(null);
        tvReportDay.setTextColor(colorMain);
        tvReportWeek.setBackgroundResource(R.drawable.bg_filter_chip);
        tvReportWeek.setBackgroundTintList(null);
        tvReportWeek.setTextColor(colorMain);
        tvReportMonth.setBackgroundResource(R.drawable.bg_filter_chip);
        tvReportMonth.setBackgroundTintList(null);
        tvReportMonth.setTextColor(colorMain);
        tvReportAll.setBackgroundResource(R.drawable.bg_filter_chip);
        tvReportAll.setBackgroundTintList(null);
        tvReportAll.setTextColor(colorMain);

        if (currentReportFilter.equals("Day")) {
            tvReportDay.setBackgroundResource(R.drawable.bg_search_rounded);
            tvReportDay.setBackgroundTintList(bgTint);
            tvReportDay.setTextColor(colorSelected);
        } else if (currentReportFilter.equals("Week")) {
            tvReportWeek.setBackgroundResource(R.drawable.bg_search_rounded);
            tvReportWeek.setBackgroundTintList(bgTint);
            tvReportWeek.setTextColor(colorSelected);
        } else if (currentReportFilter.equals("Month")) {
            tvReportMonth.setBackgroundResource(R.drawable.bg_search_rounded);
            tvReportMonth.setBackgroundTintList(bgTint);
            tvReportMonth.setTextColor(colorSelected);
        } else if (currentReportFilter.equals("All")) {
            tvReportAll.setBackgroundResource(R.drawable.bg_search_rounded);
            tvReportAll.setBackgroundTintList(bgTint);
            tvReportAll.setTextColor(colorSelected);
        }

        // 2. Filter data
        List<Invoice> filtered = filterInvoicesByPeriod(currentReportFilter);

        // 3. Setup Charts
        setupPieChart(filtered);
        setupBarChart(filtered);
    }

    private List<Invoice> filterInvoicesByPeriod(String period) {
        List<Invoice> filtered = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        
        for (Invoice inv : allInvoices) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                Date date = sdf.parse(inv.timestamp);
                if (date == null) continue;

                Calendar itemCal = Calendar.getInstance();
                itemCal.setTime(date);

                if (period.equals("Day")) {
                    if (itemCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) &&
                        itemCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) filtered.add(inv);
                } else if (period.equals("Week")) {
                    if (itemCal.get(Calendar.WEEK_OF_YEAR) == cal.get(Calendar.WEEK_OF_YEAR) &&
                        itemCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) filtered.add(inv);
                } else if (period.equals("Month")) {
                    if (itemCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH) &&
                        itemCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) filtered.add(inv);
                } else if (period.equals("Custom") && customStart > 0 && customEnd > 0) {
                    long time = date.getTime();
                    // customStart/End are usually in UTC, might need adjustment but usually MaterialPicker handles it
                    if (time >= customStart && time <= customEnd + 86400000L) { // +1 day to include the end date
                        filtered.add(inv);
                    }
                } else if (period.equals("All")) {
                    filtered.add(inv);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        return filtered;
    }

    private void setupPieChart(List<Invoice> list) {
        if (pieChart == null) return;

        Map<String, Double> categoryMap = new HashMap<>();
        for (Invoice inv : list) {
            String cat = inv.category != null ? inv.category : "Khác";
            categoryMap.put(cat, categoryMap.getOrDefault(cat, 0.0) + inv.totalCost);
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : categoryMap.entrySet()) {
            entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(com.github.mikephil.charting.utils.ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(android.graphics.Color.WHITE);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setHoleRadius(60f);
        pieChart.setTransparentCircleRadius(65f);
        pieChart.setDrawEntryLabels(false);
        pieChart.animateY(1000);
        pieChart.invalidate();

        // Custom Legend update
        if (pieLegend != null) {
            pieLegend.removeAllViews();
            for (int i = 0; i < entries.size(); i++) {
                PieEntry entry = entries.get(i);
                View row = getLayoutInflater().inflate(R.layout.item_pie_legend, pieLegend, false);
                View colorBox = row.findViewById(R.id.viewColor);
                TextView tvData = row.findViewById(R.id.tvLegendText);
                
                colorBox.setBackgroundColor(dataSet.getColors().get(i % dataSet.getColors().size()));
                tvData.setText(String.format("%s: %,.0f đ", entry.getLabel(), entry.getValue()));
                pieLegend.addView(row);
            }
        }
    }

    private void setupBarChart(List<Invoice> list) {
        if (barChart == null) return;

        Map<String, Double> dailyMap = new HashMap<>();
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
        
        for (Invoice inv : list) {
            try {
                Date date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).parse(inv.timestamp);
                String day = dayFormat.format(date);
                dailyMap.put(day, dailyMap.getOrDefault(day, 0.0) + inv.totalCost);
            } catch (Exception e) {}
        }

        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        int index = 0;
        
        // Sort keys to show in order
        List<String> sortedDays = new ArrayList<>(dailyMap.keySet());
        java.util.Collections.sort(sortedDays);

        for (String day : sortedDays) {
            entries.add(new BarEntry(index++, dailyMap.get(day).floatValue()));
            labels.add(day);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Chi tiêu ngày");
        dataSet.setColor(android.graphics.Color.parseColor("#3D6EF5"));
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        barChart.setData(data);
        barChart.getDescription().setEnabled(false);
        barChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setDrawGridLines(false);
        barChart.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels));
        barChart.getAxisLeft().setDrawGridLines(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.animateY(1000);
        barChart.invalidate();
    }

    private void openDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Chọn khoảng ngày")
                .setSelection(new Pair<>(
                        MaterialDatePicker.todayInUtcMilliseconds(),
                        MaterialDatePicker.todayInUtcMilliseconds()))
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            customStart = selection.first;
            customEnd = selection.second;

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String rangeStr = sdf.format(new Date(customStart)) + " - " + sdf.format(new Date(customEnd));
            if (tvCustomRangeText != null) tvCustomRangeText.setText(rangeStr);

            currentReportFilter = "Custom";
            setupReportTab();
        });

        picker.show(getSupportFragmentManager(), "REPORT_DATE_PICKER");
    }

    private void exportToExcel() {
        List<Invoice> filtered = filterInvoicesByPeriod(currentReportFilter);
        if (filtered == null || filtered.isEmpty()) {
            android.widget.Toast.makeText(this, "Không có dữ liệu để xuất", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Thay đổi sang thư mục lưu trữ lâu dài của app thay vì Cache
            File exportDir = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "exports");
            if (!exportDir.exists()) exportDir.mkdirs();
            
            File file = new File(exportDir, "ThongKeChiTieu.csv");
            boolean fileExists = file.exists() && file.length() > 0;
            
            // Đọc file để tránh ghi trùng lặp dữ liệu của tháng trước
            java.util.Set<String> existingIds = new java.util.HashSet<>();
            if (fileExists) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts.length > 0) existingIds.add(parts[0].trim());
                    }
                }
            }
            
            // Mở luồng ghi ở chế độ APPEND (nối tiếp)
            FileOutputStream fos = new FileOutputStream(file, true);
            
            if (!fileExists) {
                // Ghi UTF-8 BOM
                fos.write(0xef);
                fos.write(0xbb);
                fos.write(0xbf);
            }
            
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            
            if (!fileExists) {
                writer.append("ID HT,Mã Hóa Đơn,Ngày giờ,Tên cửa hàng,Loại,Tham khảo,Tổng tiền (VND)\n");
            }
            
            int addedCount = 0;
            for (Invoice inv : filtered) {
                String dbId = String.valueOf(inv.id);
                // Bỏ qua nếu hóa đơn này đã được ghi vào file từ trước
                if (existingIds.contains(dbId)) continue;

                String id = inv.invoiceNo != null ? inv.invoiceNo.replace(",", " ") : "";
                String time = inv.timestamp != null ? inv.timestamp.replace(",", " ") : "";
                String shop = inv.seller != null ? inv.seller.replace(",", " ") : "";
                String category = inv.category != null ? inv.category.replace(",", " ") : "";
                String addr = inv.address != null ? inv.address.replace(",", " ") : "";
                double total = inv.totalCost;
                
                writer.append(dbId).append(",")
                      .append(id).append(",")
                      .append(time).append(",")
                      .append(shop).append(",")
                      .append(category).append(",")
                      .append(addr).append(",")
                      .append(String.format(Locale.US, "%.0f", total)).append("\n");
                
                addedCount++;
            }
            
            writer.close();
            fos.close();
            
            if (addedCount > 0) {
                android.widget.Toast.makeText(this, "Đã ghi thêm " + addedCount + " hóa đơn mới", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(this, "Không có hóa đơn mới nào để ghi thêm", android.widget.Toast.LENGTH_SHORT).show();
            }
            
            Uri uri = FileProvider.getUriForFile(this, "com.example.bill_ai.fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Thống kê chi tiêu");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Chia sẻ file thống kê"));
            
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(this, "Lỗi khi xuất file: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}