package com.example.inventorypda;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ApiClient {
    private static ApiClient instance;
    private RequestQueue requestQueue;
    private static Context ctx;
    private static final String BASE_URL = "http://121.12.156.222:5000"; // 确保IP和端口正确

    private ApiClient(Context context) {
        ctx = context.getApplicationContext();
        requestQueue = getRequestQueue();
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
        }
        return requestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

    // GET请求方法
    public void getRequest(String url, final ApiResponseListener listener) {
        String fullUrl = BASE_URL + url;
        Log.d("API Request", "GET: " + fullUrl);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, fullUrl, null,
                response -> {
                    Log.d("API Response", response.toString());
                    listener.onSuccess(response);
                },
                error -> {
                    String errorMessage = handleVolleyError(error);
                    Log.e("API Error", errorMessage);
                    listener.onError(errorMessage);
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000,
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        addToRequestQueue(request);
    }

    // POST请求方法
    public void postRequest(String url, Map<String, String> params, final ApiResponseListener listener) {
        String fullUrl = BASE_URL + url;
        Log.d("API Request", "POST: " + fullUrl + ", Params: " + params.toString());

        JSONObject jsonParams = new JSONObject(params);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, fullUrl, jsonParams,
                response -> {
                    Log.d("API Response", response.toString());
                    listener.onSuccess(response);
                },
                error -> {
                    String errorMessage = handleVolleyError(error);
                    Log.e("API Error", errorMessage);
                    listener.onError(errorMessage);
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000,
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        addToRequestQueue(request);
    }

    // 处理Volley错误
    private String handleVolleyError(VolleyError error) {
        String errorMessage = "网络请求失败";
        String responseBody = "";
        if (error != null) {
            if (error.networkResponse != null && error.networkResponse.data != null) {
                try {
                    responseBody = new String(error.networkResponse.data, "utf-8");
                    Log.d("API Error Body", responseBody);
                    if (responseBody.startsWith("{") && responseBody.endsWith("}")) {
                        JSONObject errorObj = new JSONObject(responseBody);
                        errorMessage = errorObj.optString("error",  errorObj.optString("message", responseBody));
                    } else {
                        errorMessage = responseBody;
                    }
                } catch (Exception e) {
                    errorMessage = "请求失败：" + responseBody;
                }
            } else {
                errorMessage = error.getMessage() != null ? error.getMessage() : "网络连接超时或断开";
            }
        }
        return errorMessage;
    }

    // 1. 添加商品到临时表（已实现，保留）
    public void addToTemp(String plate, String barcode, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("barcode", barcode);
        postRequest("/receiving/temp/add", params, listener);
    }

    // 2. 删除临时表商品（修复：传递板标+商品货号）
    public void deleteTempItem(String plate, String barcode, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("barcode", barcode);
        postRequest("/receiving/temp/delete_single", params, listener);
    }

    // 3. 获取临时表商品列表（已实现，保留）
    public void getTempItems(String plate, ApiResponseListener listener) {
        getRequest("/receiving/temp/items?plate=" + plate, listener);
    }

    // 4. 保存当前（带force参数，新增实现）
    public void saveTempToMain(String plate, boolean force, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("force", force ? "1" : "0"); // 后端接收字符串"1"/"0"
        postRequest("/receiving/temp/save", params, listener);
    }

    // 5. 全部保存（带force参数，新增实现）
    public void saveAllTempToMain(String plate, boolean force, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("force", force ? "1" : "0");
        postRequest("/receiving/temp/save_all", params, listener);
    }

    // 6. 检查重复商品（新增实现）
    public void checkDuplicates(String plate, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        postRequest("/receiving/check_duplicates", params, listener);
    }

    // 7. 清空当前临时表（新增实现）
    public void clearTemp(String plate, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        postRequest("/receiving/temp/clear", params, listener);
    }

    // 以下为原有方法（保留）
    public void bindArea(String plate, String area, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("area", area);
        postRequest("/receiving/bind", params, listener);
    }

    public void queryReceiving(String type, String value, ApiResponseListener listener) {
        getRequest("/receiving/query?type=" + type + "&value=" + value, listener);
    }

    public void deleteReceivingItem(String id, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("id", id);
        postRequest("/receiving/delete", params, listener);
    }

    public void loadAllInventory(ApiResponseListener listener) {
        getRequest("/all", listener);
    }

    public void queryProduct(String barcode, ApiResponseListener listener) {
        getRequest("/query?id=" + barcode, listener);
    }

    public void deleteProduct(String productId, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("product_id", productId);
        postRequest("/stockup/delete", params, listener);
    }

    public void queryDistribution(String barcode, ApiResponseListener listener) {
        getRequest("/distribution/query?product_id=" + barcode, listener);
    }

    public void deleteDistribution(String barcode, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("product_id", barcode);
        postRequest("/distribution/delete", params, listener);
    }

    public void deleteSingleDistribution(String barcode, String sequence, ApiResponseListener listener) {
        Map<String, String> params = new HashMap<>();
        params.put("product_id", barcode);
        params.put("sequence", sequence);
        postRequest("/distribution/delete_single", params, listener);
    }

    public interface ApiResponseListener {
        void onSuccess(JSONObject response);
        void onError(String error);
    }
}