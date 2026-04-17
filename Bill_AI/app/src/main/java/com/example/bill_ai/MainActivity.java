package com.example.bill_ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bill_ai.network.SupabaseManager;

public class MainActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private SupabaseManager supabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        supabase = new SupabaseManager(this);

        if (supabase.getCurrentUserId() != null) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        }

        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin   = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(v -> doLogin());

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        findViewById(R.id.tvForgotPassword).setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void showForgotPasswordDialog() {
        android.widget.EditText etEmail = new android.widget.EditText(this);
        etEmail.setHint("Nhập email đã đăng ký");
        etEmail.setPadding(50, 40, 50, 40);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Quên mật khẩu")
                .setView(etEmail)
                .setPositiveButton("Gửi mã OTP", (dialog, which) -> {
                    String email = etEmail.getText().toString().trim();
                    if (TextUtils.isEmpty(email)) return;

                    supabase.recoverPassword(email, new SupabaseManager.AuthCallback() {
                        @Override
                        public void onSuccess(String id, String e) {
                            runOnUiThread(() -> showOtpDialog(email));
                        }
                        @Override
                        public void onError(String msg) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Lỗi: " + msg, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showOtpDialog(String email) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        android.widget.TextView tvInfo = new android.widget.TextView(this);
        tvInfo.setText("Mã OTP đã được gửi đến " + email);
        tvInfo.setPadding(0, 0, 0, 20);
        layout.addView(tvInfo);

        android.widget.EditText etOtp = new android.widget.EditText(this);
        etOtp.setHint("Nhập mã OTP (6 số)");
        etOtp.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etOtp);

        android.widget.EditText etNewPass = new android.widget.EditText(this);
        etNewPass.setHint("Mật khẩu mới");
        etNewPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etNewPass);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xác thực OTP")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Đổi mật khẩu", (dialog, which) -> {
                    String otp = etOtp.getText().toString().trim();
                    String newPass = etNewPass.getText().toString().trim();

                    if (otp.length() < 6 || newPass.length() < 6) {
                        Toast.makeText(this, "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Bước 1: Xác thực mã OTP
                    supabase.verifyRecoveryOtp(email, otp, new SupabaseManager.AuthCallback() {
                        @Override
                        public void onSuccess(String id, String e) {
                            // Bước 2: Cập nhật mật khẩu mới (lúc này đã có session tạm thời)
                            supabase.updateUserPassword(newPass, new SupabaseManager.AuthCallback() {
                                @Override
                                public void onSuccess(String id, String m) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(MainActivity.this, "Đặt lại mật khẩu thành công! Hãy đăng nhập lại.", Toast.LENGTH_LONG).show();
                                    });
                                }
                                @Override
                                public void onError(String msg) {
                                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Lỗi cập nhật: " + msg, Toast.LENGTH_SHORT).show());
                                }
                            });
                        }
                        @Override
                        public void onError(String msg) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Mã OTP không đúng hoặc hết hạn", Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Nhập email"); return;
        }
        if (TextUtils.isEmpty(pass)) {
            etPassword.setError("Nhập mật khẩu"); return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Đang đăng nhập...");

        supabase.signIn(email, pass, new SupabaseManager.AuthCallback() {
            @Override
            public void onSuccess(String userId, String email) {
                try {
                    org.json.JSONObject meta = new org.json.JSONObject();
                    meta.put("device_id", supabase.getLocalDeviceId());
                    supabase.updateUserMetadata(meta, new SupabaseManager.AuthCallback() {
                        @Override
                        public void onSuccess(String id, String e) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                                finish();
                            });
                        }
                        @Override
                        public void onError(String msg) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                                finish();
                            });
                        }
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(MainActivity.this, HomeActivity.class));
                        finish();
                    });
                }
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Đăng nhập");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}