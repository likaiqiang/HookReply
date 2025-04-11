package com.example.aiReply;

import static com.example.aiReply.RecyclerViewSiblingsManager.findContainingViewHolderMethod;
import static com.example.aiReply.ViewFinder.findParent;

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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.Map;


public class Hooker implements IXposedHookLoadPackage {
    private static View lastClickedView = null;
    private static Float lastUpX;
    private static Float lastUpY;
    private RecyclerViewSiblingsManager siblingsManager;

    private ArrayList<String> commentContext;

    private boolean isCommentViewVisible = false;
    private CommentView commentViewLayout;
    private WindowManager commentWindowManager;
    private WindowManager.LayoutParams commentWindowManagerParams;
    private EditText editTextView;
    private String noteDetailText;

    private Activity detailActivity;
    private View commentRecyclerView;
    private static Map<Object, Object> subViewHolderCommentMap = new HashMap<>();
    private static Map<String, Object> subCommentIdToViewHolderMap = new HashMap<>();

    private static Map<Object, Object> parentViewHolderCommentMap = new HashMap<>();
    private static Map<String, Object> parentCommentIdToViewHolderMap = new HashMap<>();

    private void hookViewHolder(Class<?> clazz, Boolean enableLog ){
        String type = clazz.getSimpleName().startsWith("Parent") ? "parent" : "sub";
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals("onBindViewHolder")) {

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if(enableLog){
                            XposedBridge.log("onBindViewHolder hooked with args length = " + param.args.length);
                        }

                        Object viewHolder = param.args[0];
                        Object user = param.args[1];

                        Field commentCommentInfoField = new FieldFinder()
                                .setTargetObject(user)
                                .setFieldTypeName("CommentCommentInfo")
                                .setTypeMatchMode(FieldMatchMode.ENDS_WITH)
                                .find();
                        if(commentCommentInfoField !=null ){
                            Object commentCommentInfoValue = commentCommentInfoField.get(user);
                            String commentCommentInfoStr = Objects.requireNonNull(commentCommentInfoValue).toString();

                            JSONObject extractFields = StringFieldExtractor.extractFieldsAsJson(commentCommentInfoStr, Set.of("id","content","rootCommentId"));
                            if(enableLog){
                                XposedBridge.log("extractFields: " + extractFields);
                            }
                            String content = extractFields.has("content") ? extractFields.getString("content") : null;
                            String commentId = extractFields.has("id") ? extractFields.getString("id") : null;
                            String rootCommentId = extractFields.has("rootCommentId") ? extractFields.getString("rootCommentId") : null;

                            Field targetCommentField = commentCommentInfoValue.getClass().getDeclaredField("targetComment");
                            targetCommentField.setAccessible(true);
                            Object targetCommentValue = targetCommentField.get(commentCommentInfoValue);
                            Map<String, Object> commentMap = new HashMap<>();
                            Map<String, Object> holderMap = new HashMap<>();
                            if(targetCommentValue !=null){
                                Field commentTargetIdField = targetCommentValue.getClass().getDeclaredField("id");
                                commentTargetIdField.setAccessible(true);
                                String commentTargetId = (String) commentTargetIdField.get(targetCommentValue);

                                commentMap.put("commentTargetId",commentTargetId);

                                holderMap.put("commentTargetId", commentTargetId);

                            }
                            commentMap.put("id", commentId);
                            commentMap.put("content", content);
                            commentMap.put("user", user);
                            if(type.equals("sub")){
                                subViewHolderCommentMap.put(viewHolder, commentMap);
                            }
                            else{
                                parentViewHolderCommentMap.put(viewHolder, commentMap);
                            }

                            holderMap.put("content", content);
                            holderMap.put("viewHolder", viewHolder);
                            holderMap.put("user", user);
                            holderMap.put("id", commentId);
                            holderMap.put("rootCommentId", rootCommentId);
                            if(type.equals("sub")){
                                subCommentIdToViewHolderMap.put(commentId, holderMap);
                            }
                            else{
                                parentCommentIdToViewHolderMap.put(commentId, holderMap);
                            }

                        }
                    }
                });
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 记录当前加载的应用包名，方便调试
        XposedBridge.log("Xposed模块已加载到应用: " + lpparam.packageName);
        ClassLoader targetClassLoader = lpparam.classLoader;

        if (!lpparam.packageName.equals("com.xingin.xhs")) {
            return;
        }

        Class<?> subCommentBinderClazz = Class.forName(
                "com.xingin.matrix.v2.notedetail.itembinder.SubCommentBinderV2",
                false,
                lpparam.classLoader
        );
        Class<?> parentCommentBinderClazz = Class.forName(
                "com.xingin.matrix.v2.notedetail.itembinder.ParentCommentBinderV2",
                false,
                lpparam.classLoader
        );
        hookViewHolder(subCommentBinderClazz, false);
        hookViewHolder(parentCommentBinderClazz, true);


//        ViewHierarchyOverlay.init(lpparam);

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

                                lastUpX = upX;
                                lastUpY = upY;

                                // 设定点击的阈值（时间 & 移动距离）
                                if (duration < 200 && deltaX < 10 && deltaY < 10) {
                                    XposedBridge.log("performClick view: " + view.getClass().getSimpleName());
                                    XposedBridge.log("lastClickedView: " + lastClickedView);

                                    View recycler = findParent(view, view2-> view2.getClass().getSimpleName().equals("CommentListView"));
                                    if(recycler !=null){
                                        XposedBridge.log("recycler classname: " + recycler.getClass().getName());
                                        if(commentRecyclerView==null) {
                                            commentRecyclerView = recycler;
                                        }
                                        if(siblingsManager == null){
                                            siblingsManager = new RecyclerViewSiblingsManager(recycler);
                                        }
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
                        XposedBridge.log("lastClickedView: " + lastClickedView);
                        if(lastClickedView != null){
                            ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();
                            View _editTextView = findTargetView(rootView, "RichEditTextPro");
                            if(_editTextView != null){
                                editTextView = (EditText) _editTextView;
                            }
                            else {
                                editTextView = null;
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                showCommentView(activity);
                            }

                            Object holder = findContainingViewHolderMethod.invoke(commentRecyclerView, lastClickedView);
                            if(holder != null){
                                String holderName = holder.getClass().getSimpleName();

                                XposedBridge.log("holder classname: " + holderName);

                                ArrayList<String> context = new ArrayList<>();

                                if(!holderName.startsWith("Parent")){
                                    HashMap<String, Object> userMap = (HashMap<String, Object>) subViewHolderCommentMap.get(holder);
                                    String commentContent = (String) userMap.get("content");
                                    String commentTargetId = (String) userMap.get("commentTargetId");
                                    String commentId = (String) userMap.get("id");

                                    if (commentContent != null) {
                                        context.add(commentContent);
                                    }

                                    Integer index = 0;
                                    while (commentTargetId != null) {
                                        XposedBridge.log("commentTargetId " + index + ": " + commentTargetId);
                                        XposedBridge.log("commentId: " + index + ": " + commentId);
                                        XposedBridge.log("context " + index + ": " + context);

                                        HashMap<String, Object> commentMap = (HashMap<String, Object>) subCommentIdToViewHolderMap.get(commentTargetId);
                                        if (commentMap == null) {
                                            break;
                                        }

                                        commentContent = (String) commentMap.get("content");
                                        if (commentContent != null) {
                                            context.add(commentContent);
                                        }

                                        commentTargetId = (String) commentMap.get("commentTargetId");
                                        commentId = (String) commentMap.get("id");
                                        index++;
                                    }
                                    XposedBridge.log("parentCommentIdToViewHolderMap: " + parentCommentIdToViewHolderMap.keySet());
                                }
                                siblingsManager.findSiblingsBefore(lastClickedView, new RecyclerViewSiblingsManager.StopCondition() {
                                    @Override
                                    public boolean shouldStop(View view, int position) throws InvocationTargetException, IllegalAccessException {
                                        String simpName = view.getClass().getSimpleName();
                                        if(simpName.equals("ParentCommentItemView")){
                                            Object currentHolder = findContainingViewHolderMethod.invoke(commentRecyclerView, view);
                                            XposedBridge.log("found ParentCommentItemView: " + currentHolder);
                                            if(currentHolder != null){
                                                HashMap<Object,Object> userMap = (HashMap<Object,Object>) parentViewHolderCommentMap.get(currentHolder);
                                                String commentContent = (String) userMap.get("content");
                                                if (commentContent != null) {
                                                    context.add(commentContent);
                                                }
                                            }
                                            return true;
                                        }
                                        return false;
                                    };
                                });
                                XposedBridge.log("context: " + context);
                                commentContext = context;
                            }
                        }
                        lastClickedView = null;
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                "com.xingin.comment.input.ui.NoteCommentActivity",
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        final Activity activity = (Activity) param.thisObject;
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lastClickedView != null) {
                                    showCommentView(activity);
                                }
                            }
                        });
                    }
                });

        XposedHelpers.findAndHookMethod(
                "com.xingin.comment.input.ui.NoteCommentActivity",
                lpparam.classLoader, "onDestroy",
                new XC_MethodHook() {
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
                "com.xingin.comment.input.ui.NoteCommentActivity",
                lpparam.classLoader, "onPause",
                new XC_MethodHook() {
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

        XposedHelpers.findAndHookMethod(
                "com.xingin.matrix.notedetail.NoteDetailActivity",
                lpparam.classLoader,
                "onDestroy",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        commentRecyclerView = null;
                        siblingsManager = null;
                        subViewHolderCommentMap = new HashMap<>();
                        subCommentIdToViewHolderMap = new HashMap<>();
                        parentViewHolderCommentMap = new HashMap<>();
                        parentCommentIdToViewHolderMap = new HashMap<>();
                    }
                });
    }

    private View findTargetView(ViewGroup parent, String className) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            String cn = child.getClass().getSimpleName();

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
    private void showCommentView(Context context){
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
                    if(response.body() != null){
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
                }
            });
        }
        private String getContextStr(){
            int indentLevel = 0;
            StringBuilder str = new StringBuilder();
            for (int i = hooker.commentContext.size() - 1; i >= 0; i--) {
                String indent = "  ".repeat(indentLevel); // 每级缩进两个空格
                str.append(indent).append("- ").append(hooker.commentContext.get(i)).append("\n");
                indentLevel++;
            }
            return String.valueOf(str);
        }
        private void init(Context context){
            StringBuilder prompt = new StringBuilder("你是一位机智而敏锐的评论员，擅长针对文章评论进行合适的回复。你的任务是：")
                    .append("\n1. 阅读文章内容，理解其主题和核心观点（文章内容如下）。")
                    .append("\n2. 阅读用户的评论，确保你的回复能够体现对讨论主题的理解，而不是无关或空洞的回应。")
                    .append("\n3. 你的回复应该简短但有力，避免冗长。")
                    .append("\n4. 你的回复将直接作为回复文字使用，请勿说其他不必要的话")
                    .append("\n5. 根据我的指示（友好/不友好），提供相应的回复，但必须站在第三者的角度，而不是以文章作者的身份进行回复。例如：")
                    .append("\n\n * 友好回复：可以认同观点、补充信息、幽默互动或理性讨论。")
                    .append("\n\n * 不友好回复：可以机智反驳、讽刺、巧妙反击，但不能低俗或恶意攻击。")
                    .append("\n现在，请根据以下评论生成相应的回复：")
                    .append("\n");
            Button button1 = new Button(context);
            button1.setText("同意");
            button1.setOnClickListener(v -> {
                XposedBridge.log("Button click 1" + loading);
                if(loading) return;
                loading = true;
                prompt.append("文章内容: ").append(hooker.noteDetailText).append("\n用户评论: \n").append(getContextStr()).append("\n要求: 请生成一条友好的回复");
                XposedBridge.log("prompt: " + prompt);
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
                prompt.append("文章内容: ").append(hooker.noteDetailText).append("\n用户评论: \n").append(getContextStr()).append("\n要求: 请生成一条不友好的回复");
                XposedBridge.log("prompt: " + prompt);
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
    private enum FieldMatchMode {
        STARTS_WITH,
        ENDS_WITH,
        EQUALS,
        CONTAINS
    }
    private static class FieldFinder {
        private Object targetObject;
        private String fieldTypeName;
        private FieldMatchMode typeMatchMode;
        private String fieldValueName;
        private FieldMatchMode valueMatchMode;
        private boolean enableLogging = false;


        // 构造函数设置默认值
        public FieldFinder() {
            this.typeMatchMode = FieldMatchMode.EQUALS;
            this.valueMatchMode = FieldMatchMode.EQUALS;
        }

        public FieldFinder setTargetObject(Object targetObject) {
            this.targetObject = targetObject;
            return this;
        }

        public FieldFinder setFieldTypeName(String fieldTypeName) {
            this.fieldTypeName = fieldTypeName;
            return this;
        }

        public FieldFinder setTypeMatchMode(FieldMatchMode typeMatchMode) {
            this.typeMatchMode = typeMatchMode;
            return this;
        }

        public FieldFinder setFieldValueName(String fieldValueName) {
            this.fieldValueName = fieldValueName;
            return this;
        }

        public FieldFinder setValueMatchMode(FieldMatchMode valueMatchMode) {
            this.valueMatchMode = valueMatchMode;
            return this;
        }

        public FieldFinder enableLogging(boolean enable) {
            this.enableLogging = enable;
            return this;
        }

        /**
         * 根据配置的条件查找并返回匹配的字段
         * @return 匹配的字段，如果未找到则返回null
         * @throws IllegalArgumentException 如果必要参数缺失
         * @throws IllegalAccessException 如果无法访问字段
         */
        public Field find() throws IllegalArgumentException, IllegalAccessException {
            // 验证必要参数
            if (targetObject == null) {
                throw new IllegalArgumentException("Target object cannot be null");
            }

            if (fieldTypeName == null && fieldValueName == null) {
                throw new IllegalArgumentException("Either field type name or value name must be specified");
            }

            Class<?> clazz = targetObject.getClass();
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);

                // 检查类型匹配
                boolean typeMatched = true;
                if (fieldTypeName != null) {
                    Class<?> fieldType = field.getType();
                    String typeName = fieldType.getName();

                    if (enableLogging) {
                        XposedBridge.log("Field: " + field.getName() + ", Type: " + typeName);
                    }

                    switch (typeMatchMode) {
                        case STARTS_WITH:
                            typeMatched = typeName.startsWith(fieldTypeName);
                            break;
                        case ENDS_WITH:
                            typeMatched = typeName.endsWith(fieldTypeName);
                            break;
                        case CONTAINS:
                            typeMatched = typeName.contains(fieldTypeName);
                            break;
                        case EQUALS:
                        default:
                            typeMatched = typeName.equals(fieldTypeName);
                            break;
                    }

                    if (!typeMatched) {
                        continue; // 类型不匹配，跳过此字段
                    }
                }

                // 检查字段名称匹配
                boolean valueMatched = true;
                if (fieldValueName != null) {
                    String fieldName = field.getName();

                    if (enableLogging) {
                        XposedBridge.log("Checking field name: " + fieldName);
                    }

                    switch (valueMatchMode) {
                        case STARTS_WITH:
                            valueMatched = fieldName.startsWith(fieldValueName);
                            break;
                        case ENDS_WITH:
                            valueMatched = fieldName.endsWith(fieldValueName);
                            break;
                        case CONTAINS:
                            valueMatched = fieldName.contains(fieldValueName);
                            break;
                        case EQUALS:
                        default:
                            valueMatched = fieldName.equals(fieldValueName);
                            break;
                    }
                }

                if (typeMatched && valueMatched) {
                    if (enableLogging) {
                        XposedBridge.log("Found matching field: " + field.getName());
                    }
                    return field;
                }
            }

            if (enableLogging) {
                XposedBridge.log("No matching field found");
            }
            return null;
        }
    }
}
