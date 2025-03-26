package com.example.aiReply;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


public class Hooker implements IXposedHookLoadPackage {
    private static View lastClickedView = null;

    private boolean isCommentViewVisible = false;
    private CommentView commentViewLayout;
    private WindowManager commentWindowManager;
    private WindowManager.LayoutParams commentWindowManagerParams;
    private EditText editTextView;
    private String noteDetailText;

    CharSequence commentText;
    private Activity detailActivity;
    private View findTargetView(ViewGroup parent, String className) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            String cn = child.getClass().getSimpleName();
            XposedBridge.log("simpleName: " + cn);

            if (cn.equals(className)) {
                // 找到 EditText 直接返回
                return child;
            }

            if (child instanceof ViewGroup) {
                // 重要修改：检查递归结果是否为 null
                View found = findTargetView((ViewGroup) child, className);
                if (found != null) {
                    // 只有找到了才返回
                    return found;
                }
                // 如果递归没找到，继续检查下一个子视图
            }
        }
        // 所有子视图都检查完毕，没找到则返回 null
        return null;
    }
    private List<View> findTargetViews(ViewGroup parent, String className) {
        List<View> result = new ArrayList<>();

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            String cn = child.getClass().getSimpleName();
            XposedBridge.log("simpleName: " + cn);

            if (cn.equals(className)) {
                // 找到匹配的视图，添加到结果列表
                result.add(child);
            }

            if (child instanceof ViewGroup) {
                // 递归寻找子视图中的匹配项
                List<View> foundInChildren = findTargetViews((ViewGroup) child, className);
                if (!foundInChildren.isEmpty()) {
                    // 添加所有在子视图中找到的匹配项
                    result.addAll(foundInChildren);
                }
            }
        }

        return result;
    }
    private void hideCommentView() {
        if (commentViewLayout != null && commentWindowManager != null) {
            try {
                commentWindowManager.removeView(commentViewLayout);
            } catch (Exception e) {
                XposedBridge.log("移除覆盖层失败: " + e.getMessage());
            }
            commentViewLayout = null;
            isCommentViewVisible = false;
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void showCommentView(Context context, CharSequence text){
        if(commentViewLayout == null){
            commentViewLayout = new CommentView(context, this);
        }
        if(commentWindowManager == null){
            commentWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            commentWindowManagerParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // 悬浮窗类型
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // 不获取焦点
                    PixelFormat.TRANSLUCENT
            );

            // 获取屏幕高度
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int screenHeight = size.y;
            int screenWidth = size.x;

            commentWindowManagerParams.gravity = Gravity.TOP | Gravity.START; // 让窗口相对左上角对齐
            commentViewLayout.post(() -> {
                commentWindowManagerParams.x = screenWidth - commentViewLayout.getWidth(); // 紧贴右边
                commentWindowManagerParams.y = (int) (screenHeight * 0.3); // Y 轴位于屏幕高度的 30%
                commentWindowManager.updateViewLayout(commentViewLayout, commentWindowManagerParams);
            });
        }
        if (commentViewLayout.getParent() == null) {
            commentWindowManager.addView(commentViewLayout, commentWindowManagerParams);
        }
        isCommentViewVisible = true;
    }
    private static class CommentView extends LinearLayout {
        Hooker hooker;
        private boolean loading = false;

        public CommentView(Context context, Hooker hooker) {
            super(context);
            this.hooker = hooker;
            init(context);
        }
        void askAi(String prompt) throws JSONException {
            XposedBridge.log("askAi：" + prompt);
            String API_URL = "https://api.lkeap.cloud.tencent.com/v1/chat/completions";
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)  // 连接超时时间
                    .readTimeout(30, TimeUnit.SECONDS)     // 读取超时时间
                    .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时时间
                    .build();
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "deepseek-v3");
            jsonBody.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
            jsonBody.put("stream", false);

            String jsonString = jsonBody.toString();

            RequestBody body = RequestBody.create(jsonString, JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + "sk-IkxvpPjHxJmHLyqRa46LsHVNIYahxuR9QU2aoMYNF5aEPAGf")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    XposedBridge.log("call failed: " + e.getMessage());

                    // 打印完整的异常栈信息
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    XposedBridge.log("StackTrace: " + sw.toString());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    XposedBridge.log(String.valueOf(response));
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    }
                    assert response.body() != null;
                    String responseBody = response.body().string();
                    XposedBridge.log("Response: "+responseBody);
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
                            XposedBridge.log("Content: " + content);
                            XposedBridge.log(String.valueOf(hooker.editTextView));
                            new Handler(Looper.getMainLooper()).post(() -> {
                                hooker.editTextView.setText(content);
                            });
                        } else {
                            XposedBridge.log("Choices array is empty!");
                        }
                    } catch (JSONException e) {
                        XposedBridge.log("JSON Parsing Error: " + e.getMessage());
                    }
                }
            });
        }
        private void init(Context context){
            Button button1 = new Button(context);
            button1.setText("同意");
            button1.setOnClickListener(v -> {
                XposedBridge.log("Button click 1" + loading);
                if(loading) return;
                loading = true;
                StringBuilder textContext = new StringBuilder();
                textContext.append("文章内容: ").append(hooker.noteDetailText).append("\n原评论: ").append(hooker.commentText);
                StringBuilder prompt = new StringBuilder("站在第三者的角度上根据以下要求生成评论回复\n1. ").append(textContext).append("\n2. 生成的回复将直接作为回复文字使用，请勿说其他不必要的话").append("\n3.生成的回复不少于30个字，不多于50个字，且文字必须连贯").append("\n4. 回复语气是积极的，可以是肯定、鼓励、感激");
                try {
                    askAi(String.valueOf(prompt));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } finally {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        loading = false;
                    });
                }
            });
            Button button2 = new Button(context);
            button2.setText("不同意");
            button2.setOnClickListener(v -> {
                XposedBridge.log("Button click 2" + loading);
                if(loading) return;
                loading = true;
                StringBuilder textContext = new StringBuilder();
                textContext.append("文章内容: ").append(hooker.noteDetailText).append("\n原评论: ").append(hooker.commentText);
                StringBuilder prompt = new StringBuilder("站在第三者的角度上根据以下要求生成评论回复\n1. ").append(textContext).append("\n2. 生成的回复将直接作为回复文字使用，请勿说其他不必要的话").append("\n3.生成的回复不少于10个字，不多于30个字，且文字必须连贯").append("\n4. 回复语气是负面的，通常表示讽刺挖苦、冷嘲热讽");

                try {
                    askAi(String.valueOf(prompt));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } finally {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        loading = false;
                    });
                }
            });
            addView(button1);
            addView(button2);
            setOrientation(VERTICAL);
        }
    }
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 记录当前加载的应用包名，方便调试
        XposedBridge.log("Xposed模块已加载到应用: " + lpparam.packageName);


        if (!lpparam.packageName.equals("com.xingin.xhs")) {
            return;
        }

        ViewHierarchyOverlay.init(lpparam);
        XposedHelpers.findAndHookMethod("android.view.View", lpparam.classLoader,
                "dispatchTouchEvent", MotionEvent.class, new XC_MethodHook() {
                    private float downX, downY;
                    private long downTime;
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        MotionEvent event = (MotionEvent) param.args[0];
                        View view = (View) param.thisObject;
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                downX = event.getRawX(); // 记录按下时的屏幕 X 坐标
                                downY = event.getRawY(); // 记录按下时的屏幕 Y 坐标
                                downTime = System.currentTimeMillis(); // 记录按下时间
                                break;

                            case MotionEvent.ACTION_UP:
                                float upX = event.getRawX();
                                float upY = event.getRawY();
                                long upTime = System.currentTimeMillis();

                                // 计算时间和位移
                                long duration = upTime - downTime;
                                float deltaX = Math.abs(upX - downX);
                                float deltaY = Math.abs(upY - downY);

                                // 设定点击的阈值（时间 & 移动距离）
                                if (duration < 200 && deltaX < 10 && deltaY < 10) {
                                    XposedBridge.log("performClick view: " + view.getClass().getSimpleName());
                                    if(view.getClass().getSimpleName().equals("HandlePressStateCommentTextView")){
                                        lastClickedView = view;

                                        if (detailActivity != null) {
                                            ViewGroup rootView = (ViewGroup) detailActivity.getWindow().getDecorView();
                                            List<View> noteDetailTextViews = findTargetViews(rootView, "NoteDetailTextView");
                                            XposedBridge.log("noteDetailTextViews: " + noteDetailTextViews);
                                            StringBuilder noteText = new StringBuilder();
                                            if(!noteDetailTextViews.isEmpty()){
                                                for(View _view : noteDetailTextViews){
                                                    noteText.append(((TextView) _view).getText()).append("。");
                                                }
                                            }
                                            noteDetailText = noteText.toString();
                                        }
                                    }
                                }
                                break;
                        }

                    }
                });


        XposedHelpers.findAndHookMethod(
                "com.xingin.comment.input.ui.NoteCommentActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        XposedBridge.log("发现回复 Activity: " + activity.getClass().getName());
                        if(lastClickedView != null){
                            ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();
                            View _editTextView = findTargetView(rootView, "RichEditTextPro");
                            if(_editTextView != null){
                                editTextView = (EditText) _editTextView;
                            }
                            else {
                                editTextView = null;
                            }

                            commentText = ((TextView) lastClickedView).getText();

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                showCommentView(activity, commentText);
                            }
                            XposedBridge.log("comment text: " + commentText);
                            XposedBridge.log("editText view: " + editTextView);
                        }

                        lastClickedView = null;
                    }
                }
        );

        XposedHelpers.findAndHookMethod("com.xingin.comment.input.ui.NoteCommentActivity", lpparam.classLoader, "onResume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                final Activity activity = (Activity) param.thisObject;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            showCommentView(activity, commentText);
                        }
                    }
                });
            }
        });

        XposedHelpers.findAndHookMethod("com.xingin.comment.input.ui.NoteCommentActivity", lpparam.classLoader, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                final Activity activity = (Activity) param.thisObject;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideCommentView();
                    }
                });
                lastClickedView = null;
            }
        });
        XposedHelpers.findAndHookMethod("com.xingin.comment.input.ui.NoteCommentActivity", lpparam.classLoader, "onPause", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                final Activity activity = (Activity) param.thisObject;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideCommentView();
                    }
                });
                lastClickedView = null;
            }
        });
        XposedHelpers.findAndHookMethod(
                "com.xingin.matrix.notedetail.NoteDetailActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                detailActivity = (Activity) param.thisObject;
            }
        });
    }
}

// com.xingin.comment.input.ui.NoteCommentActivity