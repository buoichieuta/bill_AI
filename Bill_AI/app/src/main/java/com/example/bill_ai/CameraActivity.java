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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.ListenableFuture;

public class CameraActivity extends AppCompatActivity {

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private PreviewView viewFinder;
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

        viewFinder = findViewById(R.id.viewFinder);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Nút chụp ảnh
        findViewById(R.id.btnCamera).setOnClickListener(v -> takePhoto());

        // Nút thư viện
        findViewById(R.id.btnGallery).setOnClickListener(v -> openGallery());

        // Nút quay lại
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Bottom Nav (Chỉ để hiển thị cho khớp mẫu)
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_invoice);

        // Tự động mở camera khi vào activity
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (Exception e) {
                Toast.makeText(this, "Lỗi Camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        File photoFile = new File(getCacheDir(), "scan_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        goToCrop(photoFile.getAbsolutePath());
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(CameraActivity.this, "Lỗi chụp ảnh: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToCrop(String imagePath) {
        Intent intent = new Intent(this, CropActivity.class);
        intent.putExtra("image_path", imagePath);
        startActivity(intent);
        // Không finish CameraActivity ở đây để user có thể quay lại nếu muốn chụp lại
    }

    // ── Mở camera cũ (giữ lại để tham chiếu nếu cần hoặc xóa) ────────────────────────────────────
    private void openCamera() {
        // ... (Logic cũ dùng system intent)
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
        // Đổi thành goToCrop
        goToCrop(imagePath);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
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
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 100) startCamera();
            if (requestCode == 101) openGallery();
        } else {
            Toast.makeText(this, "Cần cấp quyền để tiếp tục!",
                    Toast.LENGTH_SHORT).show();
        }
    }
}