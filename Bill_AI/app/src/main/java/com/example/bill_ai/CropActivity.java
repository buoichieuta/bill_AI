package com.example.bill_ai;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bill_ai.view.PolygonView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class CropActivity extends AppCompatActivity {

    private String imagePath;
    private Bitmap originalBitmap;
    private ImageView ivCropImage;
    private PolygonView polygonView;
    private float scaleFactor = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        imagePath = getIntent().getStringExtra("image_path");
        if (imagePath == null) {
            finish();
            return;
        }

        ivCropImage = findViewById(R.id.ivCropImage);
        polygonView = findViewById(R.id.polygonView);

        loadBitmap();

        findViewById(R.id.btnCancelCrop).setOnClickListener(v -> finish());
        findViewById(R.id.btnConfirmCrop).setOnClickListener(v -> performCrop());
        findViewById(R.id.btnRotateCrop).setOnClickListener(v -> rotateImage());
    }

    private void loadBitmap() {
        File file = new File(imagePath);
        if (!file.exists()) return;

        originalBitmap = BitmapFactory.decodeFile(imagePath);
        ivCropImage.setImageBitmap(originalBitmap);

        // Đợi ImageView layout xong để tính các điểm mặc định
        ivCropImage.post(() -> {
            int viewWidth = ivCropImage.getWidth();
            int viewHeight = ivCropImage.getHeight();
            int imgWidth = originalBitmap.getWidth();
            int imgHeight = originalBitmap.getHeight();

            // Tính scale factor giữa ảnh gốc và view hiển thị
            float widthRatio = (float) viewWidth / imgWidth;
            float heightRatio = (float) viewHeight / imgHeight;
            scaleFactor = Math.min(widthRatio, heightRatio);

            float displayedWidth = imgWidth * scaleFactor;
            float displayedHeight = imgHeight * scaleFactor;

            float left = (viewWidth - displayedWidth) / 2;
            float top = (viewHeight - displayedHeight) / 2;
            float right = left + displayedWidth;
            float bottom = top + displayedHeight;

            // Đặt 4 điểm mặc định (hình chữ nhật thụt lùi 10%)
            float insetX = displayedWidth * 0.1f;
            float insetY = displayedHeight * 0.1f;

            List<PointF> points = new ArrayList<>();
            points.add(new PointF(left + insetX, top + insetY));
            points.add(new PointF(right - insetX, top + insetY));
            points.add(new PointF(right - insetX, bottom - insetY));
            points.add(new PointF(left + insetX, bottom - insetY));

            polygonView.setPoints(points);
        });
    }

    private void rotateImage() {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        originalBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, true);
        ivCropImage.setImageBitmap(originalBitmap);
        loadBitmap(); // Recalculate points
    }

    private void performCrop() {
        List<PointF> points = polygonView.getPoints();
        
        // Chuyển tọa độ view về tọa độ ảnh gốc
        int viewWidth = ivCropImage.getWidth();
        int viewHeight = ivCropImage.getHeight();
        float displayedWidth = originalBitmap.getWidth() * scaleFactor;
        float displayedHeight = originalBitmap.getHeight() * scaleFactor;
        float leftOffset = (viewWidth - displayedWidth) / 2;
        float topOffset = (viewHeight - displayedHeight) / 2;

        float[] src = new float[8];
        for (int i = 0; i < 4; i++) {
            src[i * 2] = (points.get(i).x - leftOffset) / scaleFactor;
            src[i * 2 + 1] = (points.get(i).y - topOffset) / scaleFactor;
        }

        // Định nghĩa kích thước ảnh đích (dựa trên cạnh dài nhất của vùng chọn)
        double w1 = Math.sqrt(Math.pow(src[2] - src[0], 2) + Math.pow(src[3] - src[1], 2));
        double w2 = Math.sqrt(Math.pow(src[6] - src[4], 2) + Math.pow(src[7] - src[5], 2));
        int maxWidth = (int) Math.max(w1, w2);

        double h1 = Math.sqrt(Math.pow(src[4] - src[2], 2) + Math.pow(src[5] - src[3], 2));
        double h2 = Math.sqrt(Math.pow(src[0] - src[6], 2) + Math.pow(src[1] - src[7], 2));
        int maxHeight = (int) Math.max(h1, h2);

        float[] dst = {
            0, 0,
            maxWidth, 0,
            maxWidth, maxHeight,
            0, maxHeight
        };

        try {
            Bitmap result = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            Matrix matrix = new Matrix();
            matrix.setPolyToPoly(src, 0, dst, 0, 4);
            canvas.drawBitmap(originalBitmap, matrix, null);

            // Lưu kết quả đè lên file cũ hoặc file mới
            File resultFile = new File(getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(resultFile);
            result.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();

            // Chuyển sang LoadingActivity
            Intent intent = new Intent(this, LoadingActivity.class);
            intent.putExtra("image_path", resultFile.getAbsolutePath());
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
