package com.example.bill_ai.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "invoice_ai.db";
    private static final int    DB_VERSION = 4; // SỬA: Tăng version từ 3 lên 4

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
                        "  category       TEXT," +
                        "  user_id        TEXT" + 
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
        db.execSQL(
                "CREATE TABLE notifications (" +
                        "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "  user_id     TEXT," + 
                        "  title       TEXT," +
                        "  message     TEXT," +
                        "  timestamp   TEXT," +
                        "  is_read     INTEGER DEFAULT 0" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE invoices ADD COLUMN category TEXT DEFAULT 'Other'");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE invoices ADD COLUMN user_id TEXT");
        }
        if (oldVersion < 4) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS notifications (" +
                        "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "  user_id     TEXT," + 
                        "  title       TEXT," +
                        "  message     TEXT," +
                        "  timestamp   TEXT," +
                        "  is_read     INTEGER DEFAULT 0" +
                        ")"
            );
        }
    }
}