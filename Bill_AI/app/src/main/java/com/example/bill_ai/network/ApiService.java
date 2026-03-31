package com.example.bill_ai.network;

import com.example.bill_ai.model.Invoice;
import com.example.bill_ai.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiService {

    // ⚠️ Đổi IP này thành IP máy tính của bạn (chạy ipconfig)
    private static final String BASE_URL = "http://192.168.1.98:8000";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public interface Callback {
        void onSuccess(Invoice invoice);
        void onError(String message);
    }

    public void extractInvoice(File imageFile, Callback callback) {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "file",
                        imageFile.getName(),
                        RequestBody.create(
                                MediaType.parse("image/jpeg"), imageFile
                        )
                )
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/api/extract")
                .post(body)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Không kết nối được server: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        Invoice invoice = parseInvoice(json);
                        callback.onSuccess(invoice);
                    } catch (Exception e) {
                        callback.onError("Lỗi đọc dữ liệu: " + e.getMessage());
                    }
                } else {
                    callback.onError("Server lỗi: " + response.code());
                }
            }
        });
    }

    private Invoice parseInvoice(String json) throws Exception {
        JSONObject root     = new JSONObject(json);
        // Trích xuất cấu trúc "data" từ phản hồi của api.py
        JSONObject obj      = root.optJSONObject("data");
        if (obj == null) {
            obj = root; // Đề phòng cấu trúc trả thẳng về obj
        }
        
        Invoice    invoice = new Invoice();

        invoice.seller       = obj.optString("SELLER", "");
        invoice.address      = obj.optString("ADDRESS", "");
        invoice.timestamp    = obj.optString("TIMESTAMP", "");
        invoice.invoiceNo    = obj.optString("INVOICE_NO", "");
        invoice.totalCost    = obj.optLong("TOTAL_COST", 0);
        invoice.cashReceived = obj.optLong("CASH_RECEIVED", 0);
        invoice.change       = obj.optLong("CHANGE", 0);
        invoice.createdAt    = new java.util.Date().toString();
        invoice.products     = new ArrayList<>();

        JSONArray arr = obj.optJSONArray("PRODUCTS");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                Product product  = new Product();
                product.name      = p.optString("PRODUCT", "");
                product.quantity  = p.optInt("NUM", 1);
                product.unitPrice = p.optLong("UNIT_PRICE", 0);
                product.value     = p.optLong("VALUE", 0);
                invoice.products.add(product);
            }
        }
        return invoice;
    }
}