package com.example.inventorypda;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class AppUpdateChecker {

    private static final String GITHUB_RELEASE_URL = "https://api.github.com/repos/liumengmeng2025/MyApplication/releases/latest";
    private static final String TAG = "AppUpdateChecker";

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
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "MyInventoryApp-Update-Checker");

                Log.d(TAG, "Connecting to: " + GITHUB_RELEASE_URL);
                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    Log.w(TAG, "GitHub API rate limit may be exceeded, will retry with browser_download_url");
                    return "RATE_LIMIT";
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP error response: " + responseCode);
                    return null;
                }

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }

                String response = stringBuilder.toString();
                Log.d(TAG, "API Response received, length: " + response.length());
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
            if ("RATE_LIMIT".equals(result)) {
                String downloadUrl = "https://github.com/liumengmeng2025/MyApplication/releases/latest/download/app-release.apk";
                Log.w(TAG, "GitHub API limited, using direct download: " + downloadUrl);
                showUpdateDialog(context, "最新版本", "检测到新版本，请更新", downloadUrl);
                return;
            }

            if (result != null) {
                try {
                    JSONObject release = new JSONObject(result);
                    String latestVersion = release.getString("tag_name").replace("v", "");

                    String downloadUrl = null;
                    try {
                        downloadUrl = release.getJSONArray("assets")
                                .getJSONObject(0)
                                .getString("browser_download_url");
                    } catch (Exception e) {
                        Log.w(TAG, "No assets found, using tag download URL");
                        downloadUrl = "https://github.com/liumengmeng2025/MyApplication/releases/latest/download/app-release.apk";
                    }

                    String releaseNotes = release.optString("body", "有新版本可用，请更新以获取最新功能。");

                    PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    String currentVersion = pInfo.versionName;

                    Log.d(TAG, "Current version: " + currentVersion + ", Latest version: " + latestVersion);

                    if (isNewVersionAvailable(currentVersion, latestVersion)) {
                        showUpdateDialog(context, latestVersion, releaseNotes, downloadUrl);
                    } else if (showToast) {
                        Toast.makeText(context, "已经是最新版本", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing response: ", e);
                    if (showToast) {
                        showErrorToast("解析更新信息失败");
                    }
                }
            } else {
                Log.e(TAG, "API response is null");
                if (showToast) {
                    showErrorToast("网络连接失败，请检查网络");
                }
            }
        }

        private void showErrorToast(String message) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        }
    }

    private static boolean isNewVersionAvailable(String currentVersion, String latestVersion) {
        try {
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
                .setPositiveButton("立即更新", (dialog, which) -> downloadApkDirectly(context, downloadUrl))
                .setNegativeButton("取消", null)
                .setCancelable(false)
                .show();
    }

    private static void downloadApkDirectly(Context context, String downloadUrl) {
        // 针对PDA设备的特殊处理
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            Toast.makeText(context, "正在下载更新包，请稍候...", Toast.LENGTH_LONG).show();
        }
        new DirectDownloadTask(context).execute(downloadUrl);
    }

    private static class DirectDownloadTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        private File apkFile;

        DirectDownloadTask(Context context) {
            this.context = context;
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            HttpURLConnection connection = null;
            FileOutputStream outputStream = null;

            try {
                URL url = new URL(urls[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "MyInventoryApp-Downloader");

                Log.d(TAG, "Starting direct download from: " + urls[0]);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Download failed, response code: " + responseCode);
                    return false;
                }

                File downloadsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyAppUpdates");
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }

                apkFile = new File(downloadsDir, "MyApplication_v" + System.currentTimeMillis() + ".apk");

                InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                outputStream = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                int contentLength = connection.getContentLength();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    if (contentLength > 0) {
                        int progress = (int) (totalRead * 100 / contentLength);
                        publishProgress(progress);
                    }
                }

                outputStream.flush();
                Log.d(TAG, "Download completed, file size: " + apkFile.length() + " bytes");
                return true;

            } catch (Exception e) {
                Log.e(TAG, "Direct download error: ", e);
                if (apkFile != null && apkFile.exists()) {
                    apkFile.delete();
                }
                return false;
            } finally {
                try {
                    if (outputStream != null) outputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing resources: ", e);
                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            Log.d(TAG, "Download progress: " + values[0] + "%");
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success && apkFile != null) {
                installApk(context, apkFile);
            } else {
                Toast.makeText(context, "下载失败，请检查网络连接", Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void installApk(Context context, File apkFile) {
        try {
            Log.d(TAG, "Installing APK from: " + apkFile.getAbsolutePath());

            if (!apkFile.exists() || !apkFile.canRead()) {
                Toast.makeText(context, "APK文件不存在或不可读", Toast.LENGTH_LONG).show();
                return;
            }

            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            List<ResolveInfo> resInfoList = context.getPackageManager()
                    .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, apkUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            context.startActivity(intent);
            Log.d(TAG, "Install intent started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error installing APK: ", e);
            Toast.makeText(context, "安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}