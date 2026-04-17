package com.example.bill_ai.model;

import java.io.Serializable;

public class Notification implements Serializable {
    public int id;
    public String userId;
    public String title;
    public String message;
    public String timestamp;
    public boolean isRead;

    public Notification() {}

    public Notification(String userId, String title, String message, String timestamp) {
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.isRead = false;
    }
}
