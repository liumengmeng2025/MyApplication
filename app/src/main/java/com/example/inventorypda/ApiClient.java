package com.example.inventorypda;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ApiClient {
    private static ApiClient instance;
    private RequestQueue requestQueue;
    private static Context ctx;
    // 服务器IP和端口（根据实际情况修改）
    private static final String BASE_URL = "http://121.12.156.222:5000";

    // 回调接口
    public interface ApiResponseListener {
        void onSuccess(JSONObject response);
        void onError(String error);
    }

    private ApiClient(Context context) {
        ctx = context.getApplicationContext();
        requestQueue = getRequestQueue();
    }

    // 单例模式获取实例
    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    // 获取Volley请求队列
    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
        }
        return requestQueue;
    }

    // 添加请求到队列
    private void addToRequestQueue(Request<?> request) {
        getRequestQueue().add(request);
    }

    // 获取错误消息
    private String getErrorMessage(VolleyError error) {
        String errorMsg = "网络错误";
        if (error.networkResponse != null && error.networkResponse.data != null) {
            errorMsg = new String(error.networkResponse.data);
        } else if (error.getMessage() != null) {
            errorMsg = error.getMessage();
        }
        return errorMsg;
    }

    public void saveXiangma(String xiangma, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/save_xiangma";
        Map<String, String> params = new HashMap<>();
        params.put("xiangma", xiangma);
        makePostRequest(url, params, listener);
    }
    // 更换板标接口
    public void changePlate(String oldPlate, String newPlate, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/change_plate";

        JSONObject params = new JSONObject();
        try {
            params.put("old_plate", oldPlate);
            params.put("new_plate", newPlate);
        } catch (JSONException e) {
            Log.e("ApiClient", "创建参数失败", e);
            listener.onError("参数错误");
            return;
        }

        makeJsonPostRequest(url, params, listener);
    }

    // 清空板标字段接口
    public void clearPlateFields(String plate, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/clear_plate_fields";

        JSONObject params = new JSONObject();
        try {
            params.put("plate", plate);
        } catch (JSONException e) {
            Log.e("ApiClient", "创建参数失败", e);
            listener.onError("参数错误");
            return;
        }

        makeJsonPostRequest(url, params, listener);
    }
    public void checkXiangmaDuplicate(String xiangma, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/check_xiangma_duplicate?xiangma=" + xiangma;
        makeGetRequest(url, listener);
    }
    private void makeGetRequest(String url, final ApiResponseListener listener) {
        Log.d("API Request", "GET: " + url);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("API Response", "GET Success: " + response);
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            listener.onSuccess(jsonResponse);
                        } catch (JSONException e) {
                            String errorMsg = "JSON解析错误: " + e.getMessage();
                            Log.e("API Error", errorMsg);
                            listener.onError(errorMsg);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = "网络错误: " + (error.getMessage() != null ? error.getMessage() : "未知错误");
                        Log.e("API Error", errorMsg);
                        listener.onError(errorMsg);
                    }
                });

        // 设置超时和重试策略
        stringRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000, // 超时时间（毫秒）
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        getRequestQueue().add(stringRequest);
    }

    /**
     * 通用表单POST请求（内部方法）
     */
    private void makePostRequest(String url, final Map<String, String> params, final ApiResponseListener listener) {
        Log.d("API Request", "POST: " + url + ", Params: " + params);
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("API Response", "POST Success: " + response);
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            listener.onSuccess(jsonResponse);
                        } catch (JSONException e) {
                            String errorMsg = "JSON解析错误: " + e.getMessage();
                            Log.e("API Error", errorMsg);
                            listener.onError(errorMsg);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = "网络错误: " + (error.getMessage() != null ? error.getMessage() : "未知错误");
                        Log.e("API Error", errorMsg);
                        listener.onError(errorMsg);
                    }
                }) {
            @Override
            protected Map<String, String> getParams() {
                return params;
            }
        };

        // 设置超时和重试策略
        stringRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000, // 超时时间（毫秒）
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        getRequestQueue().add(stringRequest);
    }

    /**
     * 通用JSON POST请求（内部方法）
     */
    private void makeJsonPostRequest(String url, final JSONObject jsonBody, final ApiResponseListener listener) {
        Log.d("API Request", "POST: " + url + ", JSON Body: " + jsonBody.toString());

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, jsonBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("API Response", "POST Success: " + response.toString());
                        listener.onSuccess(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = "网络错误: " + (error.getMessage() != null ? error.getMessage() : "未知错误");
                        Log.e("API Error", errorMsg);
                        listener.onError(errorMsg);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                return headers;
            }
        };

        // 设置超时和重试策略
        jsonObjectRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000, // 超时时间（毫秒）
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        getRequestQueue().add(jsonObjectRequest);
    }

    // ======================== DO单复核相关API ========================
    /**
     * 根据单据号查询DO单数据
     */
    public void queryDoByBill(String billNumber, final ApiResponseListener listener) {
        String url = BASE_URL + "/do/query_by_bill?bill_number=" + billNumber;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        System.out.println("=== ApiClient 收到响应 ===");
                        System.out.println(response.toString());
                        listener.onSuccess(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = "网络请求失败";
                        if (error.networkResponse != null) {
                            errorMsg = new String(error.networkResponse.data);
                        }
                        System.out.println("=== ApiClient 请求错误 ===");
                        System.out.println(errorMsg);
                        listener.onError(errorMsg);
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 查询DO单中指定商品数据
     */
    public void queryDoProduct(String billNumber, String code, ApiResponseListener listener) {
        String url = BASE_URL + "/do/query_product?bill_number=" + billNumber + "&code=" + code;
        makeGetRequest(url, listener);
    }

    /**
     * 更新DO单商品复核数量 - 修改为使用JSON格式
     */
    public void updateDoReviewedQty(String billNumber, String productCode, int reviewedQty, ApiResponseListener listener) {
        String url = BASE_URL + "/do/update_reviewed_qty";

        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("bill_number", billNumber);
            jsonBody.put("product_code", productCode);
            jsonBody.put("reviewed_qty", reviewedQty);

            makeJsonPostRequest(url, jsonBody, listener);

        } catch (JSONException e) {
            e.printStackTrace();
            listener.onError("参数错误: " + e.getMessage());
        }
    }

    /**
     * 获取DO单复核差异数据
     */
    public void getDoReviewDifferences(String billNumber, ApiResponseListener listener) {
        String url = BASE_URL + "/do/get_differences?bill_number=" + billNumber;
        makeGetRequest(url, listener);
    }

    /**
     * 完成DO单复核
     */
    public void finishDoReview(String billNumber, ApiResponseListener listener) {
        String url = BASE_URL + "/do/finish_review";
        Map<String, String> params = new HashMap<>();
        params.put("bill_number", billNumber);
        makePostRequest(url, params, listener);
    }

    /**
     * 重置DO单复核（清空复核数量）
     */
    public void resetDoReview(String billNumber, ApiResponseListener listener) {
        String url = BASE_URL + "/do/reset_review";
        Map<String, String> params = new HashMap<>();
        params.put("bill_number", billNumber);
        makePostRequest(url, params, listener);
    }

    /**
     * 临时保存DO单复核数据
     */
    public void tempSaveDoReview(String billNumber, ApiResponseListener listener) {
        String url = BASE_URL + "/do/temp_save";
        Map<String, String> params = new HashMap<>();
        params.put("bill_number", billNumber);
        makePostRequest(url, params, listener);
    }

    // ======================== 原有收货相关API（保留） ========================
    /**
     * 添加商品到临时表
     */
    public void addToTemp(String plate, String barcode, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/temp/add";
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("barcode", barcode);
        makePostRequest(url, params, listener);
    }

    /**
     * 删除临时表商品
     */
    public void deleteTempItem(String plate, String barcode, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/temp/delete_single";
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("barcode", barcode);
        makePostRequest(url, params, listener);
    }

    /**
     * 获取临时表商品列表
     */
    public void getTempItems(String plate, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/temp/items?plate=" + plate;
        makeGetRequest(url, listener);
    }

    /**
     * 保存当前临时表到主表（带强制保存参数）
     */
    public void saveTempToMain(String plate, boolean force, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/temp/save";
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("force", force ? "1" : "0"); // 后端接收字符串"1"/"0"
        makePostRequest(url, params, listener);
    }

    /**
     * 全部保存临时表到主表（带强制保存参数）
     */
    public void saveAllTempToMain(String plate, boolean force, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/temp/save_all";
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("force", force ? "1" : "0");
        makePostRequest(url, params, listener);
    }

    /**
     * 检查临时表重复商品
     */
    public void checkDuplicates(String plate, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/check_duplicates";
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        makePostRequest(url, params, listener);
    }

    /**
     * 清空当前临时表
     */
    public void clearTemp(String plate, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/temp/clear";
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        makePostRequest(url, params, listener);
    }

    /**
     * 绑定库位
     */
    public void bindArea(String plate, String area, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/bind";
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("area", area);
        makePostRequest(url, params, listener);
    }

    /**
     * 查询收货数据
     */
    public void queryReceiving(String type, String value, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/query?type=" + type + "&value=" + value;
        makeGetRequest(url, listener);
    }

    /**
     * 删除收货数据
     */
    public void deleteReceivingItem(String id, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/delete";
        Map<String, String> params = new HashMap<>();
        params.put("id", id);
        makePostRequest(url, params, listener);
    }

    /**
     * 根据单据号查询收货数据
     */
    public void queryByBillNumber(String billNumber, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/query_by_bill_number?bill_number=" + billNumber;
        makeGetRequest(url, listener);
    }

    // ======================== 原有库存/分货相关API（保留） ========================
    /**
     * 加载所有库存
     */
    public void loadAllInventory(ApiResponseListener listener) {
        String url = BASE_URL + "/all";
        makeGetRequest(url, listener);
    }

    /**
     * 查询商品
     */
    public void queryProduct(String barcode, ApiResponseListener listener) {
        String url = BASE_URL + "/query?id=" + barcode;
        makeGetRequest(url, listener);
    }

    /**
     * 删除商品（备货）
     */
    public void deleteProduct(String productId, ApiResponseListener listener) {
        String url = BASE_URL + "/stockup/delete";
        Map<String, String> params = new HashMap<>();
        params.put("product_id", productId);
        makePostRequest(url, params, listener);
    }

    /**
     * 查询分货数据
     */
    public void queryDistribution(String barcode, ApiResponseListener listener) {
        String url = BASE_URL + "/distribution/query?product_id=" + barcode;
        makeGetRequest(url, listener);
    }

    /**
     * 删除分货数据
     */
    public void deleteDistribution(String barcode, ApiResponseListener listener) {
        String url = BASE_URL + "/distribution/delete";
        Map<String, String> params = new HashMap<>();
        params.put("product_id", barcode);
        makePostRequest(url, params, listener);
    }

    /**
     * 单独删除分货数据（带序号）
     */
    public void deleteSingleDistribution(String barcode, String sequence, ApiResponseListener listener) {
        String url = BASE_URL + "/distribution/delete_single";
        Map<String, String> params = new HashMap<>();
        params.put("product_id", barcode);
        params.put("sequence", sequence);
        makePostRequest(url, params, listener);
    }

    // ======================== 分公司相关API ========================
    /**
     * 检查分公司商品存在性
     */
    public void checkBranchBarcodes(String plate, String branch, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/check_branch_barcodes?plate=" + Uri.encode(plate) + "&branch=" + Uri.encode(branch);
        makeGetRequest(url, listener);
    }

    /**
     * 带分公司参数的保存
     */
    public void saveTempToMainWithBranch(String plate, String branch, boolean force, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/temp/save";
        Map<String, String> params = new HashMap<>();
        params.put("plate", plate);
        params.put("branch", branch);
        if (force) {
            params.put("force", "true");
        }
        makePostRequest(url, params, listener);
    }
    /**
     * 检查箱唛在指定分公司是否重复
     */
    public void checkXiangmaDuplicateWithBranch(String xiangma, String branch, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/check_xiangma_duplicate_with_branch?xiangma=" +
                Uri.encode(xiangma) + "&branch=" + Uri.encode(branch);
        makeGetRequest(url, listener);
    }

    /**
     * 保存箱唛（带分公司参数）
     */
    public void saveXiangmaWithBranch(String xiangma, String branch, ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/save_xiangma";
        Map<String, String> params = new HashMap<>();
        params.put("xiangma", xiangma);
        params.put("branch", branch);
        makePostRequest(url, params, listener);
    }
    /**
     * 通用GET请求
     */
    public void getRequest(String url, ApiResponseListener listener) {
        makeGetRequest(BASE_URL + url, listener);
    }

    /**
     * 通用POST请求
     */
    public void postRequest(String url, Map<String, String> params, ApiResponseListener listener) {
        makePostRequest(BASE_URL + url, params, listener);
    }

    /**
     * 检查条码是否存在
     */
    public void checkBarcodeExistence(ApiResponseListener listener) {
        String url = BASE_URL + "/receiving/check_barcode_existence";
        makeGetRequest(url, listener);
    }
}