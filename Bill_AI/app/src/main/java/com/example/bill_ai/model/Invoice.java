package com.example.bill_ai.model;

import java.util.ArrayList;
import java.util.List;

public class Invoice implements java.io.Serializable {
    public long   id;
    public String seller       = "";
    public String address      = "";
    public String timestamp    = "";
    public String invoiceNo    = "";
    public long   totalCost;
    public long   cashReceived;
    public long   change;
    public String imagePath    = "";
    public String createdAt    = "";
    public String category     = "Other"; // THÊM DÒNG NÀY: Mặc định là "Other"
    public String userId;    // THÊM: ID của người quét hóa đơn
    public List<Product> products = new ArrayList<>();

    public Invoice() {}
}