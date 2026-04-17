package com.example.bill_ai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bill_ai.network.SupabaseManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView ivLogo = findViewById(R.id.ivSplashLogo);
        TextView tvName = findViewById(R.id.tvSplashName);

        // Animation
        ivLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(1200)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        tvName.animate()
                .alpha(1f)
                .translationYBy(-10f)
                .setDuration(1000)
                .setStartDelay(500)
                .start();

        // Delay 2.5s rồi chuyển hướng
        new Handler().postDelayed(() -> {
            if (new SupabaseManager(this).getCurrentUserId() != null) {
                startActivity(new Intent(SplashActivity.this, HomeActivity.class));
            } else {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            }
            finish();
        }, 2500);
    }
}
