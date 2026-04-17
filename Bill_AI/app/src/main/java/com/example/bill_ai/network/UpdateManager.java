package com.example.bill_ai.network;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateManager {

    // Đường dẫn tới bảng version.json trên Supabase Storage
    // Thay đổi link này thành URL thực tế của file version.json trên bucket app-updates
    private static final String UPDATE_JSON_URL = "https://oypyxgzmlwdabcwahobl.supabase.co/storage/v1/object/public/app-updates/version.json";
    
    public interface UpdateCheckListener {
        void onResult(boolean hasUpdate, String versionName, String releaseNotes, String apkUrl);
    }
    
    public static void checkForUpdatesSilent(Context context, UpdateCheckListener listener) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(UPDATE_JSON_URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Lỗi mạng hoặc file không tồn tại, bỏ qua
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonStr = response.body().string();
                        JSONObject json = new JSONObject(jsonStr);
                        
                        int latestVersionCode = json.getInt("version_code");
                        String latestVersionName = json.getString("version_name");
                        String releaseNotes = json.getString("release_notes");
                        String apkUrl = json.getString("apk_url");

                        int currentVersionCode = 1;
                        try {
                            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                            currentVersionCode = pInfo.versionCode;
                        } catch (PackageManager.NameNotFoundException e) {
                            e.printStackTrace();
                        }

                        if (latestVersionCode > currentVersionCode) {
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> {
                                    if (listener != null) listener.onResult(true, latestVersionName, releaseNotes, apkUrl);
                                });
                            }
                        } else {
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> {
                                    if (listener != null) listener.onResult(false, null, null, null);
                                });
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public static void showUpdateDialog(Context context, String versionName, String releaseNotes, String apkUrl) {
        new AlertDialog.Builder(context)
                .setTitle("Có bản cập nhật mới!")
                .setMessage("Phiên bản " + versionName + " đã sẵn sàng.\n\nChi tiết:\n" + releaseNotes)
                .setPositiveButton("Cập nhật ngay", (dialog, which) -> {
                    downloadAndInstallApk(context, apkUrl);
                })
                .setNegativeButton("Bỏ qua", null)
                .setCancelable(false) // Gợi ý: Nếu làm bắt buộc thì không có nút Negative, Cancelable = false
                .show();
    }

    private static void downloadAndInstallApk(Context context, String apkUrl) {
        Toast.makeText(context, "Đang tải bản cập nhật trong nền...", Toast.LENGTH_LONG).show();

        File destinationUrl = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "app-release.apk");
        if (destinationUrl.exists()) {
            destinationUrl.delete(); // Xóa file cũ nếu có
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setTitle("Cập nhật Bill AI");
        request.setDescription("Đang tải tệp cài đặt...");
        request.setDestinationUri(Uri.fromFile(destinationUrl));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = downloadManager.enqueue(request);

        // Lắng nghe lúc tải xong để tự động mở cài đặt
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == id) {
                    installApk(context, destinationUrl);
                    context.unregisterReceiver(this);
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private static void installApk(Context context, File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri downloadedApk = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
                intent.setDataAndType(downloadedApk, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Mở trình cài đặt thất bại. Vui lòng mở tệp APK từ thư mục Download.", Toast.LENGTH_LONG).show();
        }
    }
}
