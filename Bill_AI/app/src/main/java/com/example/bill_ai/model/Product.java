package com.example.bill_ai.model;

public class Product implements java.io.Serializable {
    public long   id;
    public long   invoiceId;
    public String name      = "";
    public int    quantity;
    public long   unitPrice;
    public long   value;

    public Product() {}
}