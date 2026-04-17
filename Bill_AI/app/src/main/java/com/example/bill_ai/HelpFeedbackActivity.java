package com.example.bill_ai;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bill_ai.network.SupabaseManager;

public class HelpFeedbackActivity extends AppCompatActivity {

    private SupabaseManager supabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_feedback);

        supabase = new SupabaseManager(this);

        setupHeader();
        setupContactActions();
        setupFeedbackForm();
    }

    private void setupHeader() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }


    private void setupContactActions() {
        // Email
        findViewById(R.id.btnEmail).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:ngochieuvan123@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Hỗ trợ ứng dụng Bill AI");
            try {
                startActivity(Intent.createChooser(intent, "Gửi email hỗ trợ..."));
            } catch (Exception e) {
                Toast.makeText(this, "Không tìm thấy ứng dụng Email", Toast.LENGTH_SHORT).show();
            }
        });

        // Phone
        findViewById(R.id.btnPhone).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:0396032433"));
            startActivity(intent);
        });

        // Zalo
        findViewById(R.id.btnZalo).setOnClickListener(v -> {
            String zaloUrl = "https://zalo.me/0396032433"; 
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(zaloUrl));
            startActivity(intent);
        });

        // Facebook
        findViewById(R.id.btnFacebook).setOnClickListener(v -> {
            String fbUrl = "https://www.facebook.com/share/1GtVndFEfw/"; 
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(fbUrl));
            startActivity(intent);
        });
    }

    private void setupFeedbackForm() {
        Spinner spinner = findViewById(R.id.spinnerCategory);
        String[] internalCategories = {"Lỗi nhận diện", "Góp ý tính năng", "Báo lỗi ứng dụng", "Yêu cầu khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, internalCategories);
        spinner.setAdapter(adapter);

        EditText etFeedback = findViewById(R.id.etFeedback);
        Button btnSend = findViewById(R.id.btnSendFeedback);

        btnSend.setOnClickListener(v -> {
            String category = spinner.getSelectedItem().toString();
            String description = etFeedback.getText().toString().trim();

            if (description.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập nội dung phản hồi", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSend.setEnabled(false);
            btnSend.setText("Đang gửi...");

            supabase.sendFeedback(category, description, new SupabaseManager.AuthCallback() {
                @Override
                public void onSuccess(String userId, String email) {
                    runOnUiThread(() -> {
                        Toast.makeText(HelpFeedbackActivity.this, "Cảm ơn bạn đã gửi phản hồi!", Toast.LENGTH_LONG).show();
                        etFeedback.setText("");
                        btnSend.setEnabled(true);
                        btnSend.setText("Gửi phản hồi");
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(HelpFeedbackActivity.this, "Lỗi: " + message, Toast.LENGTH_SHORT).show();
                        btnSend.setEnabled(true);
                        btnSend.setText("Gửi phản hồi");
                    });
                }
            });
        });
    }
}
