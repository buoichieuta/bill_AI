package com.example.bill_ai.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.bill_ai.model.Invoice;
import com.example.bill_ai.model.Product;
import com.example.bill_ai.model.Notification;
import java.util.ArrayList;
import java.util.List;

public class InvoiceDao {

    private final SQLiteDatabase db;

    public InvoiceDao(Context context) {
        db = new DatabaseHelper(context).getWritableDatabase();
    }

    public long saveInvoice(Invoice invoice) {
        ContentValues cv = new ContentValues();
        cv.put("seller",        invoice.seller);
        cv.put("address",       invoice.address);
        cv.put("timestamp",     invoice.timestamp);
        cv.put("invoice_no",    invoice.invoiceNo);
        cv.put("total_cost",    invoice.totalCost);
        cv.put("cash_received", invoice.cashReceived);
        cv.put("change_amount", invoice.change);
        cv.put("created_at",    invoice.createdAt);
        cv.put("category",      invoice.category); 
        cv.put("user_id",       invoice.userId);  // THÊM DÒNG NÀY
        long invoiceId = db.insert("invoices", null, cv);

        for (Product p : invoice.products) {
            ContentValues pcv = new ContentValues();
            pcv.put("invoice_id", invoiceId);
            pcv.put("name",       p.name);
            pcv.put("quantity",   p.quantity);
            pcv.put("unit_price", p.unitPrice);
            pcv.put("value",      p.value);
            db.insert("products", null, pcv);
        }
        return invoiceId;
    }

    public List<Invoice> getAllInvoices(String userId) {
        List<Invoice> list = new ArrayList<>();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM invoices WHERE user_id = ? ORDER BY id DESC", 
                new String[]{userId});
        while (cursor.moveToNext()) {
            Invoice inv      = new Invoice();
            inv.id           = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            inv.seller       = cursor.getString(cursor.getColumnIndexOrThrow("seller"));
            inv.address      = cursor.getString(cursor.getColumnIndexOrThrow("address"));
            inv.timestamp    = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"));
            inv.invoiceNo    = cursor.getString(cursor.getColumnIndexOrThrow("invoice_no"));
            inv.totalCost    = cursor.getLong(cursor.getColumnIndexOrThrow("total_cost"));
            inv.cashReceived = cursor.getLong(cursor.getColumnIndexOrThrow("cash_received"));
            inv.change       = cursor.getLong(cursor.getColumnIndexOrThrow("change_amount"));
            inv.imagePath    = cursor.getString(cursor.getColumnIndexOrThrow("image_path"));
            inv.createdAt    = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));

            int categoryIndex = cursor.getColumnIndex("category");
            if(categoryIndex != -1) {
                inv.category = cursor.getString(categoryIndex);
            }
            inv.userId       = cursor.getString(cursor.getColumnIndexOrThrow("user_id"));

            list.add(inv);
        }
        cursor.close();
        return list;
    }

    // (Giữ nguyên getProducts)
    public List<Product> getProducts(long invoiceId) {
        List<Product> list = new ArrayList<>();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM products WHERE invoice_id = ?",
                new String[]{String.valueOf(invoiceId)});
        while (cursor.moveToNext()) {
            Product p   = new Product();
            p.id        = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            p.invoiceId = invoiceId;
            p.name      = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            p.quantity  = cursor.getInt(cursor.getColumnIndexOrThrow("quantity"));
            p.unitPrice = cursor.getLong(cursor.getColumnIndexOrThrow("unit_price"));
            p.value     = cursor.getLong(cursor.getColumnIndexOrThrow("value"));
            list.add(p);
        }
        cursor.close();
        return list;
    }

    public Invoice getInvoiceById(long id) {
        Invoice inv = null;
        Cursor cursor = db.rawQuery(
                "SELECT * FROM invoices WHERE id = ?",
                new String[]{String.valueOf(id)});
        if (cursor.moveToFirst()) {
            inv              = new Invoice();
            inv.id           = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            inv.seller       = cursor.getString(cursor.getColumnIndexOrThrow("seller"));
            inv.address      = cursor.getString(cursor.getColumnIndexOrThrow("address"));
            inv.timestamp    = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"));
            inv.invoiceNo    = cursor.getString(cursor.getColumnIndexOrThrow("invoice_no"));
            inv.totalCost    = cursor.getLong(cursor.getColumnIndexOrThrow("total_cost"));
            inv.cashReceived = cursor.getLong(cursor.getColumnIndexOrThrow("cash_received"));
            inv.change       = cursor.getLong(cursor.getColumnIndexOrThrow("change_amount"));
            inv.imagePath    = cursor.getString(cursor.getColumnIndexOrThrow("image_path"));
            inv.createdAt    = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));

            // THÊM ĐOẠN NÀY
            int categoryIndex = cursor.getColumnIndex("category");
            if(categoryIndex != -1) {
                inv.category = cursor.getString(categoryIndex);
            }

            inv.products     = getProducts(id);
        }
        cursor.close();
        return inv;
    }

    public void deleteInvoice(long invoiceId) {
        db.delete("products", "invoice_id = ?",
                new String[]{String.valueOf(invoiceId)});
        db.delete("invoices", "id = ?",
                new String[]{String.valueOf(invoiceId)});
    }

    // ── NOTIFICATIONS ──────────────────────────────

    public long addNotification(Notification n) {
        ContentValues cv = new ContentValues();
        cv.put("user_id", n.userId);
        cv.put("title", n.title);
        cv.put("message", n.message);
        cv.put("timestamp", n.timestamp);
        cv.put("is_read", n.isRead ? 1 : 0);
        return db.insert("notifications", null, cv);
    }

    public List<Notification> getNotificationsByUserId(String userId) {
        List<Notification> list = new ArrayList<>();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM notifications WHERE user_id = ? ORDER BY id DESC",
                new String[]{userId});
        while (cursor.moveToNext()) {
            Notification n = new Notification();
            n.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            n.userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id"));
            n.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
            n.message = cursor.getString(cursor.getColumnIndexOrThrow("message"));
            n.timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"));
            n.isRead = cursor.getInt(cursor.getColumnIndexOrThrow("is_read")) == 1;
            list.add(n);
        }
        cursor.close();
        return list;
    }

    public void deleteNotification(int id) {
        db.delete("notifications", "id = ?", new String[]{String.valueOf(id)});
    }

    public void clearAllNotifications(String userId) {
        db.delete("notifications", "user_id = ?", new String[]{userId});
    }
}