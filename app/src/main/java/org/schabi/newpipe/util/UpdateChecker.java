package org.schabi.newpipe.util;

import android.app.Activity;
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
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final String UPDATE_JSON_URL = "https://drive.ytbkids.online/api/public/dl/RBiwBh1A";
    public static final String APK_FILE_NAME = "EnglishMeow.apk";
    public static final int REQUEST_INSTALL_UNKNOWN_APP = 1001;

    private static File apkFile;
    private static Uri apkUri;

    public static void checkForUpdate(final Activity activity) {
        Log.i(TAG, "开始检查更新: " + UPDATE_JSON_URL);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(UPDATE_JSON_URL).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "请求更新 JSON 失败", e);
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "检查更新失败", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Log.d(TAG, "服务器返回: " + body);

                try {
                    JSONObject json = new JSONObject(body);
                    String latestVersion = json.getString("latest_version");
                    String downloadUrl = json.getString("download_url");
                    boolean forceUpdate = json.optBoolean("force_update", false);
                    String changelog = json.optString("changelog", "");

                    String currentVersion = activity.getPackageManager()
                            .getPackageInfo(activity.getPackageName(), 0).versionName;

                    if (!latestVersion.equals(currentVersion)) {
                        activity.runOnUiThread(() ->
                                showUpdateDialog(activity, latestVersion, changelog, downloadUrl, forceUpdate)
                        );
                    } else {
                        Log.i(TAG, "已是最新版本，无需更新");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "解析更新 JSON 出错", e);
                }
            }
        });
    }

    private static void showUpdateDialog(Activity activity, String latestVersion, String changelog, String downloadUrl, boolean forceUpdate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("发现新版本 v" + latestVersion)
                .setMessage(changelog)
                .setCancelable(!forceUpdate)
                .setPositiveButton("立即更新", (dialog, which) -> downloadAndInstallApk(activity, downloadUrl, forceUpdate));

        if (!forceUpdate) builder.setNegativeButton("稍后再说", null);

        AlertDialog dialog = builder.show();
        if (forceUpdate) dialog.setCancelable(false);
    }

    private static void downloadAndInstallApk(final Context context, String url, boolean forceUpdate) {
        try {
            // 使用外部应用专属目录兼容 Android 8~16+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+：使用公共下载目录
                apkFile = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        APK_FILE_NAME
                );
            } else {
                // Android 8–9：仍然可写公共下载目录
                apkFile = new File(context.getExternalFilesDir(null), APK_FILE_NAME);
            }
            if (apkFile.exists()) {
                apkFile.delete();
                Log.w(TAG, "旧 APK 已删除");
            }

            Log.i(TAG, "准备下载到路径: " + apkFile.getAbsolutePath());

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("下载更新");
            request.setDescription("正在下载最新版本...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationUri(Uri.fromFile(apkFile));
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            final DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            final long downloadId = manager.enqueue(request);

            Toast.makeText(context, "开始下载更新...", Toast.LENGTH_SHORT).show();

            final Handler handler = new Handler();
            Runnable progressCheck = new Runnable() {
                @Override
                public void run() {
                    DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                    Cursor cursor = manager.query(query);
                    if (cursor != null && cursor.moveToFirst()) {
                        int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        int bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        int bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                        if (bytesTotal > 0) {
                            int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);
                            Log.d(TAG, "下载进度: " + progress + "%");
                        }

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            cursor.close();
                            installApk(context);
                            return;
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                            if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) {
                                Toast.makeText(context, "设备存储空间不足，无法下载更新", Toast.LENGTH_LONG).show();
                                Log.e(TAG, "下载失败，原因：空间不足");
                            } else {
                                Toast.makeText(context, "下载失败，请重试", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "下载失败，原因 code=" + reason);
                            }
                            cursor.close();
                            return;
                        }
                        cursor.close();
                        handler.postDelayed(this, 1000);
                    }
                }
            };
            handler.post(progressCheck);

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        apkUri = manager.getUriForDownloadedFile(downloadId); // <-- 真实 URI
                        if (apkUri != null) {
                            installApk(context);
                        }
                        try {
                            context.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "注销广播监听器出错", e);
                        }
                    }
                }
            };


            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }

        } catch (Exception e) {
            Log.e(TAG, "下载或安装过程出错", e);
            Toast.makeText(context, "下载失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    public static void installApk(Context context) {
        if (apkUri == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(apkUri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.getPackageManager().canRequestPackageInstalls()) {
                Intent permIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName()));
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ((Activity) context).startActivityForResult(permIntent, REQUEST_INSTALL_UNKNOWN_APP);
                return;
            }

            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "安装 APK 出错", e);
            Toast.makeText(context, "安装失败，请手动安装", Toast.LENGTH_SHORT).show();
        }
    }

}
