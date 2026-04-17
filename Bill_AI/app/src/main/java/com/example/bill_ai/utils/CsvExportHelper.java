package com.example.bill_ai.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.bill_ai.db.InvoiceDao;
import com.example.bill_ai.model.Invoice;
import com.example.bill_ai.model.Product;

import java.io.OutputStream;
import java.util.List;

public class CsvExportHelper {

    public static void exportInvoiceToCsv(Context context, long invoiceId) {
        new AlertDialog.Builder(context)
                .setTitle("Xuất Hóa đơn")
                .setMessage("Bạn có muốn xuất dữ liệu hóa đơn này ra file CSV lưu về máy không?")
                .setPositiveButton("Xuất CSV", (dialog, which) -> {
                    performExport(context, invoiceId);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private static void performExport(Context context, long invoiceId) {
        android.app.ProgressDialog pd = new android.app.ProgressDialog(context);
        pd.setMessage("Đang chuẩn bị dữ liệu...");
        pd.setCancelable(false);
        pd.show();

        com.example.bill_ai.network.SupabaseManager supabase = new com.example.bill_ai.network.SupabaseManager(context);
        supabase.getInvoiceById(invoiceId, new com.example.bill_ai.network.SupabaseManager.InvoiceCallback() {
            @Override
            public void onSuccess(Invoice inv) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        pd.dismiss();
                        generateCsvString(context, inv);
                    });
                }
            }

            @Override
            public void onError(String message) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        pd.dismiss();
                        Toast.makeText(context, "Lỗi mạng: " + message, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private static void generateCsvString(Context context, Invoice inv) {
        if (inv == null) {
            Toast.makeText(context, "Lỗi: Không có dữ liệu hóa đơn!", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csv = new StringBuilder();
        // UTF-8 BOM helps Excel recognize the encoding correctly
        csv.append("\uFEFF");
        csv.append("Thông tin chung\n");
        csv.append("Nha cung cap:,").append(escapeCsv(inv.seller)).append("\n");
        csv.append("Ngay gio:,").append(escapeCsv(inv.timestamp.isEmpty() ? inv.createdAt : inv.timestamp)).append("\n");
        csv.append("Phan loai:,").append(escapeCsv(inv.category == null ? "Khac" : inv.category)).append("\n");
        csv.append("Tong tien:,").append(inv.totalCost).append(" VNĐ\n");
        csv.append("\nDanh sach san pham\n");
        csv.append("Ten mon,So luong,Don gia,Thanh tien\n");

        if (inv.products != null) {
            for (Product p : inv.products) {
                csv.append(escapeCsv(p.name)).append(",")
                   .append(p.quantity).append(",")
                   .append(p.unitPrice).append(",")
                   .append(p.value).append("\n");
            }
        }

        saveToDownloads(context, "HoaDon_" + inv.id + "_" + System.currentTimeMillis() + ".csv", csv.toString());
    }

    private static String escapeCsv(String input) {
        if (input == null) return "Unknown";
        String s = input.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\n") || s.contains("\"")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    private static void saveToDownloads(Context context, String filename, String content) {
        try {
            OutputStream fos;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    fos = context.getContentResolver().openOutputStream(uri);
                } else {
                    Toast.makeText(context, "Lỗi hệ thống tập tin!", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File file = new java.io.File(downloadsDir, filename);
                fos = new java.io.FileOutputStream(file);
            }

            fos.write(content.getBytes("UTF-8"));
            fos.close();
            Toast.makeText(context, "Đã lưu file CSV vào thư mục Downloads!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Lỗi xuất file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
