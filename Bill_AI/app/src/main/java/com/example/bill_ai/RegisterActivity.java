package com.example.bill_ai;

import static android.widget.Toast.LENGTH_LONG;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bill_ai.network.SupabaseManager;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etPassword, etConfirmPassword, etFullName;
    private Button btnRegister;
    private TextView tvLogin;
    private SupabaseManager supabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        supabase = new SupabaseManager(this);

        etFullName        = findViewById(R.id.etFullName);
        etEmail           = findViewById(R.id.etEmail);
        etPassword        = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister       = findViewById(R.id.btnRegister);
        tvLogin           = findViewById(R.id.tvLogin);

        btnRegister.setOnClickListener(v -> doRegister());
        tvLogin.setOnClickListener(v -> finish());
    }


    private void doRegister() {
        String name    = etFullName.getText().toString().trim();
        String email   = etEmail.getText().toString().trim();
        String pass    = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etFullName.setError("Nhập họ tên"); return;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Nhập email"); return;
        }
        if (TextUtils.isEmpty(pass)) {
            etPassword.setError("Nhập mật khẩu"); return;
        }
        if (pass.length() < 6) {
            etPassword.setError("Mật khẩu tối thiểu 6 ký tự"); return;
        }
        if (!pass.equals(confirm)) {
            etConfirmPassword.setError("Mật khẩu không khớp"); return;
        }

        btnRegister.setEnabled(false);
        btnRegister.setText("Đang đăng ký...");

        supabase.signUp(email, pass, name, new SupabaseManager.AuthCallback() {
            @Override
            public void onSuccess(String userId, String email) {
                runOnUiThread(() -> {
                    Toast.makeText(RegisterActivity.this, "Đăng ký thành công!", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnRegister.setEnabled(true);
                    btnRegister.setText("Đăng ký");
                    Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}