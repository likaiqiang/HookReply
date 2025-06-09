package com.delete.aiReply;

import static com.delete.aiReply.ViewFinder.findParent;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RecyclerViewClickTracker {

    public static void init(final XC_LoadPackage.LoadPackageParam lpparam){
        // 方法 1: Hook View.setOnClickListener 方法
        hookViewSetOnClickListener(lpparam.classLoader);

        // 方法 2: Hook RecyclerView.Adapter 的关键方法
//        hookRecyclerViewAdapter(lpparam.classLoader);
//
//        // 方法 3: Hook ViewHolder 类的构造方法 (可选)
//        hookViewHolderConstructors(lpparam.classLoader);
    }

    /**
     * 方法 1: 跟踪所有 View.setOnClickListener 调用，特别关注与 RecyclerView 相关的调用
     */
    private static void hookViewSetOnClickListener(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "android.view.View",
                classLoader,
                "setOnClickListener",
                "android.view.View$OnClickListener",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        Object listener = param.args[0];

                        // 检查是否在 RecyclerView context 中 (通过检查父级视图链)
                        boolean inRecyclerViewContext = false;
                        View recycler = findParent(view, v-> v.getClass().getSimpleName().equals("CommentListView"));
                        if(recycler!=null) inRecyclerViewContext = true;

                        if (inRecyclerViewContext) {
                            // 获取相关信息
                            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

                            String callerClass = "Unknown";
                            String callerMethod = "Unknown";
                            String callerLine = "Unknown";

                            for (int i = 3; i < stackTrace.length; i++) {
                                String className = stackTrace[i].getClassName();
                                if (!className.startsWith("android.") &&
                                        !className.startsWith("androidx.") &&
                                        !className.startsWith("java.") &&
                                        !className.startsWith("dalvik.") &&
                                        !className.startsWith("com.android.") &&
                                        !className.contains("XposedBridge")) {

                                    callerClass = className;
                                    callerMethod = stackTrace[i].getMethodName();
                                    callerLine = String.valueOf(stackTrace[i].getLineNumber());
                                    break;
                                }
                            }

                            XposedBridge.log("🔍 setOnClickListener 调用位置:");
                            XposedBridge.log("📍 调用类: " + callerClass);
                            XposedBridge.log("⚙️ 调用方法: " + callerMethod);
                            XposedBridge.log("📄 代码行: " + callerLine);

//                            Field[] fields = callerClass.

                        }
                    }
                }
        );
    }

    /**
     * 方法 2: 跟踪 RecyclerView.Adapter 的关键方法
     */
    private void hookRecyclerViewAdapter(ClassLoader classLoader) {
        // Hook onCreateViewHolder 方法
        XposedHelpers.findAndHookMethod(
                "androidx.recyclerview.widget.RecyclerView$Adapter",
                classLoader,
                "onCreateViewHolder",
                "android.view.ViewGroup",
                "int",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object viewHolder = param.getResult();
                        int viewType = (int) param.args[1];

                        if (viewHolder != null) {
                            XposedBridge.log("🏭 RecyclerView.Adapter.onCreateViewHolder called");
                            XposedBridge.log("📋 ViewHolder type: " + viewHolder.getClass().getName());
                            XposedBridge.log("🔢 ViewType: " + viewType);

                            // 记录堆栈以识别适配器类
                            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                            // 找出适配器类 (通常是调用栈中第一个非系统类)
                            String adapterClass = "Unknown";
                            for (int i = 3; i < stackTrace.length; i++) {
                                String className = stackTrace[i].getClassName();
                                if (!className.startsWith("android.") &&
                                        !className.startsWith("androidx.") &&
                                        !className.startsWith("java.") &&
                                        !className.startsWith("dalvik.")) {
                                    adapterClass = className;
                                    break;
                                }
                            }
                            XposedBridge.log("🧩 Adapter class: " + adapterClass);
                            XposedBridge.log("------------------------");
                        }
                    }
                }
        );

        // Hook onBindViewHolder 方法
        XposedHelpers.findAndHookMethod(
                "androidx.recyclerview.widget.RecyclerView$Adapter",
                classLoader,
                "onBindViewHolder",
                "androidx.recyclerview.widget.RecyclerView$ViewHolder",
                "int",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object viewHolder = param.args[0];
                        int position = (int) param.args[1];

                        if (viewHolder != null) {
                            XposedBridge.log("🔄 onBindViewHolder called for position: " + position);
                            XposedBridge.log("📋 ViewHolder: " + viewHolder.getClass().getName());
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // onBindViewHolder 完成后，检查是否有新的点击监听器被设置
                        Object viewHolder = param.args[0];
                        int position = (int) param.args[1];

                        if (viewHolder != null) {
                            // 获取 itemView
                            Object itemView = XposedHelpers.getObjectField(viewHolder, "itemView");
                            if (itemView instanceof ViewGroup) {
                                XposedBridge.log("✅ onBindViewHolder completed for position: " + position);
                                // 可以在这里进行额外的检查
                            }
                        }
                    }
                }
        );
    }

    /**
     * 方法 3: 跟踪 ViewHolder 构造函数 (可选)
     */
    private void hookViewHolderConstructors(ClassLoader classLoader) {
        // 递归查找所有 RecyclerView.ViewHolder 的子类
        try {
            Class<?> viewHolderClass = XposedHelpers.findClass(
                    "androidx.recyclerview.widget.RecyclerView$ViewHolder",
                    classLoader
            );

            // 监控 ViewHolder 的构造函数
            XposedBridge.hookAllConstructors(viewHolderClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("👷 ViewHolder constructor called: " + param.thisObject.getClass().getName());

                    // 打印堆栈追踪，找出谁创建了 ViewHolder
                    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                    for (int i = 3; i < Math.min(10, stackTrace.length); i++) {
                        XposedBridge.log("   " + stackTrace[i].toString());
                    }
                    XposedBridge.log("------------------------");
                }
            });

        } catch (Throwable t) {
            XposedBridge.log("Failed to hook ViewHolder constructors: " + t.getMessage());
        }
    }

    /**
     * 判断一个 View 是否在 RecyclerView 内部
     */
    private boolean isInRecyclerView(View view) {
        if (view == null) return false;

        // 找寻父视图链中是否有 RecyclerView
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent.getClass().getName().contains("RecyclerView")) {
                return true;
            }
            if (!(parent instanceof ViewParent)) {
                break;
            }
            parent = ((View) parent).getParent();
        }

        // 也检查堆栈中是否有 RecyclerView 相关类
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.contains("RecyclerView") ||
                    className.contains("ViewHolder") ||
                    className.contains("Adapter")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取 View 的详细信息
     */
    private static String getViewInfo(View view) {
        StringBuilder info = new StringBuilder();

        try {
            // View的类名
            info.append("Class: ").append(view.getClass().getName());

            // View的ID
            int id = view.getId();
            if (id != View.NO_ID) {
                try {
                    info.append(", ID: ").append(view.getResources().getResourceEntryName(id))
                            .append("(").append(id).append(")");
                } catch (Exception e) {
                    info.append(", ID: ").append(id);
                }
            }

            // TextView 文本
            if (view instanceof TextView) {
                CharSequence text = ((TextView) view).getText();
                if (text != null && text.length() > 0) {
                    String textStr = text.toString();
                    if (textStr.length() > 30) {
                        textStr = textStr.substring(0, 27) + "...";
                    }
                    info.append(", Text: '").append(textStr).append("'");
                }
            }

            // 查找 ViewHolder
            ViewParent parent = view.getParent();
            while (parent != null) {
                if (parent.getClass().getName().contains("RecyclerView")) {
                    info.append(", In RecyclerView: ").append(parent.getClass().getName());
                    break;
                }
                if (!(parent instanceof ViewParent)) {
                    break;
                }
                parent = ((View) parent).getParent();
            }

        } catch (Exception e) {
            info.append(" [Error: ").append(e.getMessage()).append("]");
        }

        return info.toString();
    }
}