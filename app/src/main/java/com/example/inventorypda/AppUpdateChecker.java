package example.inventorypda;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppUpdateChecker {

    private static final String GITHUB_RELEASE_URL = "https://api.github.com/repos/liumengmeng2025/MyApplication/releases/latest";
    private static final String TAG = "AppUpdateChecker"; // 添加日志标签

    public static void checkForUpdate(Context context, boolean showToast) {
        new UpdateCheckTask(context, showToast).execute();
    }

    private static class UpdateCheckTask extends AsyncTask<Void, Void, String> {
        private Context context;
        private boolean showToast;

        UpdateCheckTask(Context context, boolean showToast) {
            this.context = context;
            this.showToast = showToast;
        }

        @Override
        protected String doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(GITHUB_RELEASE_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000); // 15秒超时
                connection.setReadTimeout(15000);

                Log.d(TAG, "Connecting to: " + GITHUB_RELEASE_URL);
                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP error response: " + responseCode);
                    return null;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }

                String response = stringBuilder.toString();
                Log.d(TAG, "API Response: " + response); // 记录完整响应
                return response;
            } catch (Exception e) {
                Log.e(TAG, "Error in doInBackground: ", e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    JSONObject release = new JSONObject(result);
                    String latestVersion = release.getString("tag_name").replace("v", "");
                    String downloadUrl = release.getJSONArray("assets")
                            .getJSONObject(0)
                            .getString("browser_download_url");
                    String releaseNotes = release.getString("body");

                    PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    String currentVersion = pInfo.versionName;

                    // 添加详细日志
                    Log.d(TAG, "Current version: " + currentVersion);
                    Log.d(TAG, "Latest version from server: " + latestVersion);
                    Log.d(TAG, "Download URL: " + downloadUrl);

                    if (isNewVersionAvailable(currentVersion, latestVersion)) {
                        Log.d(TAG, "New version available, showing dialog");
                        showUpdateDialog(context, latestVersion, releaseNotes, downloadUrl);
                    } else {
                        Log.d(TAG, "No new version available");
                        if (showToast) {
                            Toast.makeText(context, "已经是最新版本", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing response: ", e);
                    if (showToast) {
                        Toast.makeText(context, "检查更新失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Log.e(TAG, "API response is null");
                if (showToast) {
                    Toast.makeText(context, "检查更新失败: 无法获取更新信息", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static boolean isNewVersionAvailable(String currentVersion, String latestVersion) {
        try {
            // 移除可能存在的"v"前缀
            currentVersion = currentVersion.replace("v", "");
            latestVersion = latestVersion.replace("v", "");

            String[] currentParts = currentVersion.split("\\.");
            String[] latestParts = latestVersion.split("\\.");

            for (int i = 0; i < Math.max(currentParts.length, latestParts.length); i++) {
                int current = (i < currentParts.length) ? Integer.parseInt(currentParts[i]) : 0;
                int latest = (i < latestParts.length) ? Integer.parseInt(latestParts[i]) : 0;

                if (latest > current) return true;
                if (latest < current) return false;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error comparing versions: ", e);
            return false;
        }
    }

    private static void showUpdateDialog(Context context, String version, String notes, String downloadUrl) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setTitle("发现新版本 " + version)
                .setMessage(notes.isEmpty() ? "有新版本可用，请更新以获取最新功能。" : notes)
                .setPositiveButton("立即更新", (dialog, which) -> downloadAndInstallApk(context, downloadUrl))
                .setNegativeButton("取消", null)
                .setCancelable(false)
                .show();
    }

    private static void downloadAndInstallApk(Context context, String downloadUrl) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MyApplication.apk");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            long downloadId = downloadManager.enqueue(request);
            Log.d(TAG, "Download started with ID: " + downloadId);

            // 监听下载完成
            new Thread(() -> {
                boolean downloading = true;
                while (downloading) {
                    try {
                        Thread.sleep(1000); // 每秒检查一次
                        DownloadManager.Query query = new DownloadManager.Query();
                        query.setFilterById(downloadId);
                        android.database.Cursor cursor = downloadManager.query(query);
                        if (cursor.moveToFirst()) {
                            int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                downloading = false;
                                String apkUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                                Log.d(TAG, "Download completed: " + apkUri);
                                installApk(context, apkUri);
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                downloading = false;
                                Log.e(TAG, "Download failed");
                                // 在主线程中显示Toast
                                new android.os.Handler(context.getMainLooper()).post(() -> {
                                    Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                        cursor.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error monitoring download: ", e);
                        downloading = false;
                    }
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error starting download: ", e);
            Toast.makeText(context, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void installApk(Context context, String apkPath) {
        try {
            File file = new File(Uri.parse(apkPath).getPath());
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(apkUri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);

            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error installing APK: ", e);
            Toast.makeText(context, "安装失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}