package com.example.bill_ai.network;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.bill_ai.model.Invoice;
import com.example.bill_ai.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.webkit.MimeTypeMap;
import okhttp3.*;
import java.io.File;

public class SupabaseManager {

    private static final String SUPABASE_URL = "https://oypyxgzmlwdabcwahobl.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im95cHl4Z3ptbHdkYWJjd2Fob2JsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzUxMDk1NzEsImV4cCI6MjA5MDY4NTU3MX0.wb3u4DklOjMm8KZ_fO98zsNpoV1LQ8xxI104Uh2H0jk";

    private final OkHttpClient client;
    private final Context context;
    private final SharedPreferences prefs;

    public SupabaseManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("supabase_prefs", Context.MODE_PRIVATE);
        
        this.client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request originalRequest = chain.request();
                    
                    // Đừng intercept các request liên quan đến Auth (đăng nhập, refresh token)
                    if (originalRequest.url().toString().contains("/auth/v1/")) {
                        return chain.proceed(originalRequest);
                    }

                    Response response = chain.proceed(originalRequest);

                    // Nếu gặp lỗi 401 (Unauthorized), hoặc 400/403 từ Storage do token hết hạn
                    boolean isTokenExpired = false;
                    if (response.code() == 401) {
                        isTokenExpired = true;
                    } else if (response.code() == 400 || response.code() == 403) {
                        try {
                            String bodyString = response.peekBody(2048).string();
                            if (bodyString.contains("Unauthorized") && bodyString.contains("\"exp\"")) {
                                isTokenExpired = true;
                            }
                        } catch (Exception e) {}
                    }

                    if (isTokenExpired) {
                        // Thử refresh token
                        String newToken = refreshTokenSync();
                        if (newToken != null) {
                            // Đóng response cũ trước khi tạo request mới
                            response.close();
                            
                            // Tạo request mới với token mới
                            Request newRequest = originalRequest.newBuilder()
                                    .header("Authorization", "Bearer " + newToken)
                                    .build();
                            return chain.proceed(newRequest);
                        }
                    }
                    return response;
                })
                .build();
    }

    public interface AuthCallback {
        void onSuccess(String userId, String email);
        void onError(String message);
    }

    public void signUp(String email, String password, String fullName, AuthCallback callback) {
        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("password", password);
            
            JSONObject metadata = new JSONObject();
            metadata.put("full_name", fullName);
            metadata.put("password", password); // THÊM DÒNG NÀY ĐỂ LƯU MẬT KHẨU VÀO TRANG ADMIN
            json.put("data", metadata);
        } catch (Exception e) { e.printStackTrace(); }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/signup")
                .header("apikey", SUPABASE_KEY)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Đăng ký thất bại: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body().string();
                if (response.isSuccessful()) {
                    callback.onSuccess("", email);
                } else {
                    callback.onError("Lỗi: " + parseError(resp));
                }
            }
        });
    }

    public void signIn(String email, String password, AuthCallback callback) {
        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("password", password);
        } catch (Exception e) { e.printStackTrace(); }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/token?grant_type=password")
                .header("apikey", SUPABASE_KEY)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Đăng nhập thất bại: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONObject obj = new JSONObject(resp);
                        String token = obj.getString("access_token");
                        String refreshToken = obj.optString("refresh_token", "");
                        JSONObject user = obj.getJSONObject("user");
                        String userId = user.getString("id");
                        
                        // Lấy full_name và avatar_url từ metadata
                        JSONObject metadata = user.optJSONObject("user_metadata");
                        
                        // KIỂM TRA TÀI KHOẢN BỊ KHÓA
                        if (metadata != null && metadata.optBoolean("is_blocked", false)) {
                            callback.onError("Tài khoản của bạn đã bị khóa bởi quản trị viên.");
                            return;
                        }

                        String fullName = (metadata != null) ? metadata.optString("full_name", "") : "";
                        String avatarUrl = (metadata != null) ? metadata.optString("avatar_url", "") : "";
                        
                        prefs.edit()
                            .putString("access_token", token)
                            .putString("refresh_token", refreshToken)
                            .putString("user_id", userId)
                            .putString("email", email)
                            .putString("full_name", fullName)
                            .putString("avatar_url", avatarUrl) // Sync avatar URL
                            .apply();
                        
                        callback.onSuccess(userId, email);
                    } catch (Exception e) { callback.onError("Lỗi Parse: " + e.getMessage()); }
                } else {
                    String serverError = parseError(resp);
                    if (serverError.contains("Invalid login credentials") || serverError.contains("invalid_credentials")) {
                        callback.onError("Email hoặc mật khẩu không chính xác.");
                    } else if (serverError.contains("Email not confirmed")) {
                        callback.onError("Email này chưa được xác nhận. Vui lòng kiểm tra hộp thư.");
                    } else {
                        callback.onError("Lỗi đăng nhập: " + serverError);
                    }
                }
            }
        });
    }

    public void recoverPassword(String email, AuthCallback callback) {
        JSONObject json = new JSONObject();
        try { json.put("email", email); } catch (Exception e) {}

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/recover")
                .header("apikey", SUPABASE_KEY)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("", email);
                else callback.onError("Lỗi gửi mail: " + response.body().string());
            }
        });
    }

    public void verifyRecoveryOtp(String email, String token, AuthCallback callback) {
        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("token", token);
            json.put("type", "recovery");
        } catch (Exception e) {}

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/verify")
                .header("apikey", SUPABASE_KEY)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONObject obj = new JSONObject(resp);
                        String accessToken = obj.getString("access_token");
                        String refreshToken = obj.optString("refresh_token", "");
                        prefs.edit()
                            .putString("access_token", accessToken)
                            .putString("refresh_token", refreshToken)
                            .apply();
                        callback.onSuccess("", email);
                    } catch (Exception e) { callback.onError(e.getMessage()); }
                } else callback.onError("Mã OTP không chính xác");
            }
        });
    }

    public void updateUserPassword(String newPassword, AuthCallback callback) {
        JSONObject json = new JSONObject();
        try { json.put("password", newPassword); } catch (Exception e) {}
        updateUser(json, callback);
    }

    public void updateUserMetadata(JSONObject data, AuthCallback callback) {
        JSONObject json = new JSONObject();
        try { json.put("data", data); } catch (Exception e) {}
        updateUser(json, callback);
    }

    private void updateUser(JSONObject json, AuthCallback callback) {
        String token = prefs.getString("access_token", "");
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/user")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Update local context if it was metadata
                    if (json.has("data")) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            SharedPreferences.Editor editor = prefs.edit();
                            if (data.has("full_name")) editor.putString("full_name", data.optString("full_name"));
                            if (data.has("avatar_url")) editor.putString("avatar_url", data.optString("avatar_url"));
                            editor.apply();
                        }
                    }
                    callback.onSuccess("", "");
                } else callback.onError("Lỗi cập nhật: " + response.body().string());
            }
        });
    }

    public void saveInvoice(Invoice invoice, AuthCallback callback) {
        String token = prefs.getString("access_token", "");
        String userId = prefs.getString("user_id", "");
        String userEmail = prefs.getString("email", ""); // Lấy email để lưu kèm cho dễ quản lý

        JSONObject json = new JSONObject();
        try {
            json.put("seller", invoice.seller);
            json.put("address", invoice.address);
            json.put("timestamp", invoice.timestamp);
            json.put("total_cost", invoice.totalCost);
            json.put("image_path", invoice.imagePath);
            json.put("invoice_no", invoice.invoiceNo);
            json.put("category", invoice.category);
            json.put("user_id", userId);
            json.put("user_email", userEmail); // Lưu email vào bảng invoices
        } catch (Exception e) { e.printStackTrace(); }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/invoices?select=id")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .header("Prefer", "return=representation")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String respStr = response.body().string();
                        JSONArray arr = new JSONArray(respStr);
                        long newInvoiceId = arr.getJSONObject(0).getLong("id");

                        // Lưu sản phẩm
                        for (Product p : invoice.products) {
                            JSONObject pJson = new JSONObject();
                            pJson.put("invoice_id", newInvoiceId);
                            pJson.put("name", p.name);
                            pJson.put("quantity", p.quantity);
                            pJson.put("unit_price", p.unitPrice);
                            pJson.put("value", p.value);
                            pJson.put("user_email", userEmail); // Lưu email vào từng sản phẩm cho dễ quản lý
                            
                            RequestBody pBody = RequestBody.create(pJson.toString(), MediaType.get("application/json; charset=utf-8"));
                            Request pReq = new Request.Builder()
                                    .url(SUPABASE_URL + "/rest/v1/products")
                                    .header("apikey", SUPABASE_KEY)
                                    .header("Authorization", "Bearer " + token)
                                    .post(pBody)
                                    .build();
                            client.newCall(pReq).execute();
                        }
                        callback.onSuccess(String.valueOf(newInvoiceId), userEmail);
                    } catch (Exception e) { e.printStackTrace(); }
                } else {
                    callback.onError("Lỗi lưu: " + response.body().string());
                }
            }
        });
    }

    private void saveProducts(long invoiceId, List<Product> products) {
        String token = prefs.getString("access_token", "");
        JSONArray arr = new JSONArray();
        try {
            for (Product p : products) {
                JSONObject obj = new JSONObject();
                obj.put("invoice_id", invoiceId);
                obj.put("name", p.name);
                obj.put("quantity", p.quantity);
                obj.put("unit_price", p.unitPrice);
                obj.put("value", p.value);
                arr.put(obj);
            }
        } catch (Exception e) { e.printStackTrace(); }

        RequestBody body = RequestBody.create(arr.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/products")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}
            @Override
            public void onResponse(Call call, Response response) throws IOException {}
        });
    }

    public void getAllInvoices(InvoiceListCallback callback) {
        String token = prefs.getString("access_token", "");
        String userId = prefs.getString("user_id", "");
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/invoices?user_id=eq." + userId + "&is_archived=eq.false&select=*&order=id.desc")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body().string();
                if (response.isSuccessful()) {
                    List<Invoice> list = parseInvoices(resp);
                    callback.onSuccess(list);
                } else { callback.onError("Lỗi tải: " + resp); }
            }
        });
    }

    private List<Invoice> parseInvoices(String json) {
        List<Invoice> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Invoice inv = new Invoice();
                inv.id = obj.getLong("id");
                inv.seller = obj.optString("seller", "");
                inv.address = obj.optString("address", "");
                inv.timestamp = obj.optString("timestamp", "");
                inv.invoiceNo = obj.optString("invoice_no", "");
                inv.totalCost = obj.optLong("total_cost", 0);
                inv.cashReceived = obj.optLong("cash_received", 0);
                inv.change = obj.optLong("change_amount", 0);
                inv.imagePath = obj.optString("image_path", "");
                inv.category = obj.optString("category", "Other");
                list.add(inv);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public void getInvoiceById(long id, InvoiceCallback callback) {
        String token = prefs.getString("access_token", "");
        String userId = prefs.getString("user_id", "");
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/invoices?id=eq." + id + "&user_id=eq." + userId + "&select=*,products(*)")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONArray arr = new JSONArray(resp);
                        if (arr.length() > 0) {
                            Invoice inv = parseSingleInvoice(arr.getJSONObject(0));
                            callback.onSuccess(inv);
                        } else { callback.onError("Không tìm thấy bill"); }
                    } catch (Exception e) { callback.onError(e.getMessage()); }
                } else { callback.onError("Lỗi tải bill: " + resp); }
            }
        });
    }

    private Invoice parseSingleInvoice(JSONObject obj) throws Exception {
        Invoice inv = new Invoice();
        inv.id = obj.getLong("id");
        inv.seller = obj.optString("seller", "");
        inv.address = obj.optString("address", "");
        inv.timestamp = obj.optString("timestamp", "");
        inv.invoiceNo = obj.optString("invoice_no", "");
        inv.totalCost = obj.optLong("total_cost", 0);
        inv.cashReceived = obj.optLong("cash_received", 0);
        inv.change = obj.optLong("change_amount", 0);
        inv.imagePath = obj.optString("image_path", "");
        inv.category = obj.optString("category", "Other");

        JSONArray productsArr = obj.optJSONArray("products");
        if (productsArr != null) {
            for (int i = 0; i < productsArr.length(); i++) {
                JSONObject pObj = productsArr.getJSONObject(i);
                Product p = new Product();
                p.name = pObj.optString("name", "");
                p.quantity = pObj.optInt("quantity", 1);
                p.unitPrice = pObj.optLong("unit_price", 0);
                p.value = pObj.optLong("value", 0);
                inv.products.add(p);
            }
        }
        return inv;
    }

    public void archiveInvoice(long id, AuthCallback callback) {
        String token = prefs.getString("access_token", "");
        String userId = prefs.getString("user_id", "");
        
        JSONObject json = new JSONObject();
        try {
            json.put("is_archived", true);
        } catch (Exception e) { e.printStackTrace(); }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/invoices?id=eq." + id + "&user_id=eq." + userId)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .patch(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(String.valueOf(id), "");
                } else {
                    callback.onError("Lỗi ẩn hóa đơn: " + response.body().string());
                }
            }
        });
    }

    public void deleteInvoice(long id, AuthCallback callback) {
        String token = prefs.getString("access_token", "");
        String userId = prefs.getString("user_id", "");
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/invoices?id=eq." + id + "&user_id=eq." + userId)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(String.valueOf(id), "");
                } else {
                    callback.onError("Lỗi xóa: " + response.body().string());
                }
            }
        });
    }

    public interface InvoiceCallback {
        void onSuccess(Invoice invoice);
        void onError(String message);
    }

    public interface InvoiceListCallback {
        void onSuccess(List<Invoice> list);
        void onError(String message);
    }

    public void sendFeedback(String category, String description, AuthCallback callback) {
        String token = prefs.getString("access_token", "");
        String userId = prefs.getString("user_id", "");
        String userEmail = prefs.getString("email", "");

        JSONObject json = new JSONObject();
        try {
            json.put("user_id", userId);
            json.put("user_email", userEmail);
            json.put("category", category);
            json.put("description", description);
        } catch (Exception e) { e.printStackTrace(); }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/feedbacks")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Lỗi gửi feedback: " + e.getMessage()); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess("", "");
                } else {
                    callback.onError("Lỗi server: " + response.code());
                }
            }
        });
    }

    public synchronized String refreshTokenSync() {
        String refreshToken = prefs.getString("refresh_token", "");
        if (refreshToken.isEmpty()) return null;

        JSONObject json = new JSONObject();
        try { json.put("refresh_token", refreshToken); } catch (Exception e) {}

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token")
                .header("apikey", SUPABASE_KEY)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String resp = response.body().string();
                JSONObject obj = new JSONObject(resp);
                String newAccessToken = obj.getString("access_token");
                String newRefreshToken = obj.optString("refresh_token", "");
                prefs.edit()
                    .putString("access_token", newAccessToken)
                    .putString("refresh_token", newRefreshToken)
                    .apply();
                return newAccessToken;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String parseError(String json) {
        try {
            return new JSONObject(json).optString("msg", "Lỗi không xác định");
        } catch (Exception e) { return json; }
    }

    public String getCurrentUserId() {
        return prefs.getString("user_id", null);
    }

    public void signOut() {
        prefs.edit().clear().apply();
    }

    public String getLocalDeviceId() {
        String deviceId = prefs.getString("local_device_id", null);
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("local_device_id", deviceId).apply();
        }
        return deviceId;
    }

    public void fetchCurrentUser(AuthCallback callback) {
        String token = prefs.getString("access_token", "");
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/user")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONObject user = new JSONObject(resp);
                        JSONObject metadata = user.optJSONObject("user_metadata");
                        if (metadata != null) {
                            // KIỂM TRA TÀI KHOẢN BỊ KHÓA
                            if (metadata.optBoolean("is_blocked", false)) {
                                if (callback != null) callback.onError("BLOCKED");
                                return;
                            }

                            String serverDeviceId = metadata.optString("device_id", "");
                            String avatarUrl = metadata.optString("avatar_url", "");
                            
                            prefs.edit()
                                 .putString("server_device_id", serverDeviceId)
                                 .putString("avatar_url", avatarUrl)
                                 .apply();

                            if (callback != null) callback.onSuccess(serverDeviceId, avatarUrl);
                        } else {
                            if (callback != null) callback.onSuccess("", "");
                        }
                    } catch (Exception e) {
                        if (callback != null) callback.onError("Parse error: " + e.getMessage());
                    }
                } else {
                    if (callback != null) callback.onError("Fetch error: " + resp);
                }
            }
        });
    }

    /**
     * Tải file lên Supabase Storage
     * @param file File cần tải
     * @param bucket Tên bucket (đã tạo trên Supabase)
     * @param fileName Tên file trên server
     * @param callback Callback kết quả
     */
    public void uploadFile(File file, String bucket, String fileName, AuthCallback callback) {
        String token = prefs.getString("access_token", "");
        
        // Lấy MIME type động từ phần mở rộng file
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        if (mimeType == null) mimeType = "application/octet-stream";
        
        RequestBody body = RequestBody.create(file, MediaType.get(mimeType));
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + fileName)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .header("x-upsert", "true") 
                .header("cache-control", "3600")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Upload thất bại: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(getPublicUrl(bucket, fileName), "");
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.e("SupabaseStorage", "Upload failed: " + response.code() + " - " + errorBody);
                    callback.onError("Lỗi " + response.code() + ": " + errorBody);
                }
            }
        });
    }

    public String getPublicUrl(String bucket, String fileName) {
        return SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/" + fileName;
    }

    /**
     * Xóa file khỏi Supabase Storage
     */
    public void deleteFile(String bucket, String fileName, AuthCallback callback) {
        String token = prefs.getString("access_token", "");
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + fileName)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    if (callback != null) callback.onSuccess("", "");
                } else {
                    if (callback != null) callback.onError("Delete failed: " + response.code());
                }
            }
        });
    }
}
