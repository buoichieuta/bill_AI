package com.example.bill_ai.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class PolygonView extends View {

    private Paint linePaint;
    private Paint pointPaint;
    private Paint fillPaint;

    private List<PointF> points = new ArrayList<>();
    private int selectedPointIndex = -1;
    private float touchThreshold = 60f; // Khoảng cách tối đa để chọn điểm

    public PolygonView(Context context) {
        super(context);
        init();
    }

    public PolygonView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#00FFCC"));
        linePaint.setStrokeWidth(6f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        pointPaint = new Paint();
        pointPaint.setColor(Color.parseColor("#00FFCC"));
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setColor(Color.parseColor("#3300FFCC"));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);
    }

    public void setPoints(List<PointF> newPoints) {
        this.points = newPoints;
        invalidate();
    }

    public List<PointF> getPoints() {
        return points;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.size() < 4) return;

        // Vẽ vùng phủ
        Path path = new Path();
        path.moveTo(points.get(0).x, points.get(0).y);
        for (int i = 1; i < 4; i++) {
            path.lineTo(points.get(i).x, points.get(i).y);
        }
        path.close();
        canvas.drawPath(path, fillPaint);

        // Vẽ các đường nối
        for (int i = 0; i < 4; i++) {
            PointF start = points.get(i);
            PointF end = points.get((i + 1) % 4);
            canvas.drawLine(start.x, start.y, end.x, end.y, linePaint);
        }

        // Vẽ các điểm nút
        for (PointF point : points) {
            canvas.drawCircle(point.x, point.y, 25f, pointPaint);
            // Vẽ thêm viền trắng cho nút
            Paint white = new Paint();
            white.setColor(Color.WHITE);
            white.setStyle(Paint.Style.STROKE);
            white.setStrokeWidth(3f);
            canvas.drawCircle(point.x, point.y, 25f, white);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                selectedPointIndex = findNearestPoint(x, y);
                if (selectedPointIndex != -1) return true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (selectedPointIndex != -1) {
                    points.get(selectedPointIndex).set(x, y);
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                selectedPointIndex = -1;
                break;
        }
        return super.onTouchEvent(event);
    }

    private int findNearestPoint(float x, float y) {
        for (int i = 0; i < points.size(); i++) {
            double dist = Math.sqrt(Math.pow(x - points.get(i).x, 2) + Math.pow(y - points.get(i).y, 2));
            if (dist < touchThreshold) return i;
        }
        return -1;
    }
}
