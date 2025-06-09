package com.delete.aiReply;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.ai4j.openai4j.chat.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
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

        if ("com.delete.aiReply.RECEIVE_PROMPT".equals(intent.getAction())) {
            String prompt = intent.getStringExtra("prompt");
            String modelName = intent.getStringExtra(com.delete.aiReply.MainActivity.KEY_MODEL_NAME);
            String endpoint = intent.getStringExtra(MainActivity.KEY_BASE_URL);
            String apiKey = intent.getStringExtra(com.delete.aiReply.MainActivity.KEY_API_KEY);

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
            String baseUrl = params[2];  // baseUrl，例如 "https://api.openai.com/v1/"
            String apiKey = params[3];

            try {
                // 创建 OpenAI 聊天模型
                OpenAiChatModel chatModel = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    chatModel = OpenAiChatModel.builder()
                            .apiKey(apiKey)
                            .baseUrl(baseUrl)
                            .modelName(modelName)
                            .timeout(Duration.ofSeconds(30))
                            .maxRetries(3)
                            .build();
                    // 发送聊天请求
                    String response = chatModel.generate(String.valueOf(UserMessage.from(prompt)));

                    // 提取回复内容
                    if (response != null && !response.trim().isEmpty()) {
                        Log.d("LangChain4j", "Response: " + response);
                        return response;
                    } else {
                        Log.d("LangChain4j", "Empty response");
                        return "";
                    }
                }

            } catch (Exception e) {
                Log.e("NetworkRequestTask", "LangChain4j API call failed: " + e.getMessage());

                // 打印完整的异常栈信息
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                Log.e("NetworkRequestTask", "StackTrace: " + sw.toString());

                return "";
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            // 将结果回传给Hooker.java
            try {
                Intent responseIntent = new Intent("com.delete.aiReply.RECEIVE_RESPONSE");
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