package com.example.aiReply;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiRequestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("AiRequestReceiver", "onReceive action: " + intent.getAction());

        if ("com.example.aiReply.RECEIVE_PROMPT".equals(intent.getAction())) {
            String prompt = intent.getStringExtra("prompt");
            String modelName = intent.getStringExtra(MainActivity.KEY_MODEL_NAME);
            String endpoint = intent.getStringExtra(MainActivity.KEY_END_POINT);
            String apiKey = intent.getStringExtra(MainActivity.KEY_API_KEY);

            String requestId = intent.getStringExtra("request_id");
            String packageName = intent.getStringExtra("package_name");
            Log.d("BootReceiver", "Received prompt: " + prompt);

            new NetworkRequestTask(context, packageName, requestId).execute(prompt, modelName, endpoint, apiKey);
        }
    }
    private class NetworkRequestTask extends AsyncTask<String, Void, String> {
        private Context context;
        private String packageName;
        private String requestId;

        public NetworkRequestTask(Context context, String packageName, String requestId) {
            this.context = context;
            this.packageName = packageName;
            this.requestId = requestId;
        }

        @Override
        protected String doInBackground(String... params) {
            String prompt = params[0];
            String modelName = params[1];
            String endpoint = params[2];
            String apiKey = params[3];
            final String[] result = {""};
            final CountDownLatch latch = new CountDownLatch(1);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)  // 连接超时时间
                    .readTimeout(30, TimeUnit.SECONDS)     // 读取超时时间
                    .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时时间
                    .build();
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            JSONObject jsonBody = new JSONObject();
            try {
                jsonBody.put("model", modelName);
                jsonBody.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
                jsonBody.put("stream", false);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }


            String jsonString = jsonBody.toString();

            RequestBody body = RequestBody.create(jsonString, JSON);
            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.d("http","call failed: " + e.getMessage());

                    // 打印完整的异常栈信息
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    Log.d("http","StackTrace: " + sw.toString());
                    latch.countDown();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    Log.d("http","response: " + response);
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    }
                    if(response.body() != null){
                        String responseBody = response.body().string();
                        Log.d("http","Response: "+responseBody);
                        try {
                            // 解析 JSON 对象
                            JSONObject jsonObject = new JSONObject(responseBody);

                            // 获取 "choices" 数组
                            JSONArray choicesArray = jsonObject.getJSONArray("choices");

                            // 确保数组至少有一个元素
                            if (choicesArray.length() > 0) {
                                JSONObject firstChoice = choicesArray.getJSONObject(0);  // 取第一个元素

                                // 获取 "message" 对象
                                JSONObject messageObject = firstChoice.getJSONObject("message");

                                // 获取 "content" 字段
                                String content = messageObject.getString("content");

                                // 打印 content
                                Log.d("http","Content: " + content);
                                result[0] = content;
                            } else {
                                Log.d("http","Choices array is empty!");
                            }
                        } catch (JSONException e) {
                            Log.d("http","JSON Parsing Error: " + e.getMessage());
                        }
                    }
                    latch.countDown();
                }
            });

            try {
                // 等待请求完成
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e("NetworkRequestTask", "Interrupted: " + e.getMessage());
            }

            return result[0];
        }

        @Override
        protected void onPostExecute(String result) {
            // 3. 将结果回传给Hooker.java
            try {
                Intent responseIntent = new Intent("com.example.aiReply.RECEIVE_RESPONSE");
                responseIntent.setPackage(packageName);
                responseIntent.putExtra("response", result);
                responseIntent.putExtra("package_name", packageName);
                responseIntent.putExtra("request_id", requestId);

                context.sendBroadcast(responseIntent);
                Log.d("onPostExecute", "Response sent back to Hooker: " + result);
            } catch (Exception e) {
                Log.e("onPostExecute", "Error sending response: " + e.getMessage(), e);
            }
        }
    }
}