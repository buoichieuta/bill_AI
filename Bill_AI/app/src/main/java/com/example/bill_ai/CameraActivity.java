package com.example.bill_ai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CameraActivity extends AppCompatActivity {

    private File imageFile;

    // ── Camera launcher ──────────────────────────────
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && imageFile != null
                                && imageFile.exists()
                                && imageFile.length() > 0) {
                            goToLoading(imageFile.getAbsolutePath());
                        } else {
                            Toast.makeText(this, "Khong lay duoc anh, thu lai",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    // ── Gallery launcher ─────────────────────────────
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            try {
                                InputStream inputStream = getContentResolver().openInputStream(uri);
                                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                if (bitmap != null) {
                                    imageFile = saveBitmap(bitmap);
                                    if (imageFile != null) {
                                        goToLoading(imageFile.getAbsolutePath());
                                    }
                                }
                            } catch (Exception e) {
                                Toast.makeText(this, "Loi doc anh: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Nút chụp ảnh
        ImageButton btnCamera = findViewById(R.id.btnCamera);
        btnCamera.setOnClickListener(v -> openCamera());

        // Nút thư viện — dùng LinearLayout
        LinearLayout btnGallery = findViewById(R.id.btnGallery);
        btnGallery.setOnClickListener(v -> openGallery());
    }

    // ── Mở camera ────────────────────────────────────
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 100);
            return;
        }

        imageFile = new File(getCacheDir(),
                "invoice_" + System.currentTimeMillis() + ".jpg");

        Uri photoUri = FileProvider.getUriForFile(
                this,
                "com.example.bill_ai.fileprovider",
                imageFile
        );

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        cameraLauncher.launch(intent);
    }

    // ── Mở thư viện ──────────────────────────────────
    private void openGallery() {
        // Kiểm tra quyền đọc storage
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ dùng READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 101);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 101);
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryLauncher.launch(intent);
    }

    // ── Chuyển sang Loading ───────────────────────────
    private void goToLoading(String imagePath) {
        Intent intent = new Intent(this, LoadingActivity.class);
        intent.putExtra("image_path", imagePath);
        startActivity(intent);
        finish();
    }

    // ── Lưu bitmap ra file ───────────────────────────
    private File saveBitmap(Bitmap bitmap) {
        try {
            File file = new File(getCacheDir(),
                    "invoice_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 100) openCamera();
            if (requestCode == 101) openGallery();
        } else {
            Toast.makeText(this, "Can cap quyen de tiep tuc!",
                    Toast.LENGTH_SHORT).show();
        }
    }
}