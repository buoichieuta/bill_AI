package com.example.bill_ai.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "invoice_ai.db";
    private static final int    DB_VERSION = 2; // SỬA: Tăng version từ 1 lên 2

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE invoices (" +
                        "  id             INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "  seller         TEXT," +
                        "  address        TEXT," +
                        "  timestamp      TEXT," +
                        "  invoice_no     TEXT," +
                        "  total_cost     INTEGER," +
                        "  cash_received  INTEGER," +
                        "  change_amount  INTEGER," +
                        "  image_path     TEXT," +
                        "  created_at     TEXT," +
                        "  category       TEXT" + // THÊM DÒNG NÀY
                        ")"
        );
        db.execSQL(
                "CREATE TABLE products (" +
                        "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "  invoice_id  INTEGER," +
                        "  name        TEXT," +
                        "  quantity    INTEGER," +
                        "  unit_price  INTEGER," +
                        "  value       INTEGER," +
                        "  FOREIGN KEY (invoice_id) REFERENCES invoices(id)" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Cách đơn giản nhất: Xóa bảng cũ và tạo lại (sẽ mất dữ liệu cũ đang có)
        db.execSQL("DROP TABLE IF EXISTS products");
        db.execSQL("DROP TABLE IF EXISTS invoices");
        onCreate(db);

        // CÁCH 2 (Nâng cao): Dùng nếu bạn không muốn mất dữ liệu hóa đơn cũ
        // if (oldVersion < 2) {
        //     db.execSQL("ALTER TABLE invoices ADD COLUMN category TEXT DEFAULT 'Other'");
        // }
    }
}