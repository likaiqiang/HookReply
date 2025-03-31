package com.example.aiReply;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Spanned;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import android.net.Uri;

import androidx.annotation.RequiresApi;

public class ViewHierarchyOverlay {
    private static final String TAG = "ViewHierarchyOverlay";
    private static WindowManager windowManager;
    private static OverlayView overlayView;
    private static ControlButton controlButton;
    private static WindowManager.LayoutParams controlButtonParmas;
    private static boolean isOverlayVisible = false;
    private static Map<String, Integer> classColors = new HashMap<>();
    private static Random random = new Random();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Activity currentActivity;

    /**
     * 在应用的Activity创建时初始化覆盖层
     */
    public static void init(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Activity的onCreate方法
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader,
                    "onCreate", "android.os.Bundle", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            final Activity activity = (Activity) param.thisObject;
                            if (!Settings.canDrawOverlays(activity)) {
                                XposedBridge.log("XposedHook 未授权悬浮窗权");

                                // 跳转到悬浮窗权限页面
                                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + activity.getPackageName()));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(intent);
                            }

                            currentActivity = activity;

                            // 在主线程上执行UI操作
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // 获取WindowManager
                                    windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

                                    // 如果悬浮按钮不存在，则创建它
                                    if (controlButton == null) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            createControlButton(activity);
                                        }
                                    }
                                }
                            });
                        }
                    });

            // Hook Activity的onDestroy方法
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader,
                    "onDestroy", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            final Activity activity = (Activity) param.thisObject;

                            // 在主线程上执行UI操作
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // 如果当前Activity正在被销毁，则移除覆盖层
                                    if (activity == currentActivity) {
                                        hideOverlay();
                                        removeControlButton();
                                        currentActivity = null;
                                    }
                                }
                            });
                        }
                    });

            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader,
                    "onResume", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            final Activity activity = (Activity) param.thisObject;

                            if (!Settings.canDrawOverlays(activity)) {
                                XposedBridge.log("XposedHook 未授权悬浮窗权");

                                // 跳转到悬浮窗权限页面
                                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + activity.getPackageName()));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(intent);
                            }

                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // onResume 兜底，如果按钮不存在，则重新创建
                                    if (controlButton == null) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            createControlButton(activity);
                                        }
                                    }
                                    currentActivity = activity; // 确保 currentActivity 是最新的
                                }
                            });
                        }
                    });
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader,
                    "onKeyDown", int.class, KeyEvent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            int keyCode = (int) param.args[0];
                            if (keyCode == KeyEvent.KEYCODE_BACK && isOverlayVisible) {
                                param.setResult(true); // 直接返回 true，拦截 Back 键
                            }
                        }
                    });

            XposedBridge.log(TAG + ": 初始化成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 初始化失败: " + t.getMessage());
        }
    }

    private static void ensureControlButtonOnTop(){
        if(controlButton != null && controlButtonParmas != null){
            windowManager.removeView(controlButton);
            windowManager.addView(controlButton, controlButtonParmas);
        }
    }

    /**
     * 创建悬浮控制按钮
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void createControlButton(Context context) {
        // 如果按钮已存在，先移除
        removeControlButton();

        // 创建新的控制按钮
        controlButton = new ControlButton(context);

        // 设置悬浮窗参数
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        controlButtonParmas = params;

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 100;

        // 添加到窗口
        windowManager.addView(controlButton, params);
    }

    /**
     * 移除控制按钮
     */
    private static void removeControlButton() {
        if (controlButton != null && windowManager != null) {
            try {
                windowManager.removeView(controlButton);
            } catch (Exception e) {
                XposedBridge.log(TAG + ": 移除控制按钮失败: " + e.getMessage());
            }
            controlButton = null;
        }
    }

    /**
     * 显示覆盖层
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void showOverlay() {
        if (currentActivity == null || windowManager == null) return;

        // 如果覆盖层已存在，先移除
        hideOverlay();

        overlayView = new OverlayView(currentActivity);

        // 设置覆盖层参数
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT);

        // 添加到窗口
        windowManager.addView(overlayView, params);
        isOverlayVisible = true;
        ensureControlButtonOnTop();

        // 刷新视图信息
        refreshOverlay();
    }

    /**
     * 隐藏覆盖层
     */
    private static void hideOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                XposedBridge.log(TAG + ": 移除覆盖层失败: " + e.getMessage());
            }
            overlayView = null;
            isOverlayVisible = false;
        }
    }

    /**
     * 刷新覆盖层视图信息
     */
    private static void refreshOverlay() {
        if (overlayView != null && currentActivity != null) {
            ViewGroup rootView = (ViewGroup) currentActivity.getWindow().getDecorView();
            overlayView.setRootView(rootView);
            overlayView.invalidate();
        }
    }

    /**
     * 为视图类型生成一致的颜色
     */
    private static int getColorForViewClass(String className) {
        if (!classColors.containsKey(className)) {
            // 生成一个半透明的随机颜色
            int color = Color.argb(120, random.nextInt(200) + 55,
                    random.nextInt(200) + 55, random.nextInt(200) + 55);
            classColors.put(className, color);
        }
        return classColors.get(className);
    }

    /**
     * 悬浮控制按钮类
     */
    private static class ControlButton extends FrameLayout {
        private float lastTouchX;
        private float lastTouchY;
        private float offsetX;
        private float offsetY;
        private boolean isDragging = false;

        public ControlButton(Context context) {
            super(context);
            init(context);
        }

        private void init(Context context) {
            // 创建一个圆形背景
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.parseColor("#3F51B5"));
            setBackground(drawable);

            // 添加文本
            TextView textView = new TextView(context);
            textView.setText("显示视图");
            textView.setTextColor(Color.WHITE);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setGravity(Gravity.CENTER);

            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            textParams.gravity = Gravity.CENTER;
            addView(textView, textParams);

            // 设置内边距
            int padding = dipToPx(context, 16);
            setPadding(padding, padding, padding, padding);

            // 设置点击和拖动事件
            setOnTouchListener(new OnTouchListener() {
                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastTouchX = event.getRawX();
                            lastTouchY = event.getRawY();

                            // 获取视图相对于窗口的偏移
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
                            offsetX = lp.x;
                            offsetY = lp.y;
                            break;

                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - lastTouchX;
                            float dy = event.getRawY() - lastTouchY;

                            // 如果移动距离足够大，认为是拖动
                            if (!isDragging && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
                                isDragging = true;
                            }

                            if (isDragging) {
                                // 更新按钮位置
                                WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) getLayoutParams();
                                layoutParams.x = (int) (offsetX + dx);
                                layoutParams.y = (int) (offsetY + dy);
                                windowManager.updateViewLayout(ControlButton.this, layoutParams);
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                            if (!isDragging) {
                                // 如果不是拖动，则是点击，切换覆盖层显示状态
                                if (isOverlayVisible) {
                                    hideOverlay();
                                    textView.setText("显示视图");
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        showOverlay();
                                    }
                                    textView.setText("隐藏视图");
                                }
                            }
                            isDragging = false;
                            break;
                    }
                    return true;
                }
            });
        }

        private int dipToPx(Context context, float dip) {
            float density = context.getResources().getDisplayMetrics().density;
            return (int) (dip * density + 0.5f);
        }
    }

    /**
     * 视图覆盖层类
     */
    private static class OverlayView extends View {
        private ViewGroup rootView;
        private Paint borderPaint;
        private Paint overlayPaint;
        private Paint textPaint;
        private Rect tempRect = new Rect();
        private List<ViewInfo> viewInfoList = new ArrayList<>();
//        private View highlightedView;
        private ViewInfo selectedViewInfo;
        private GestureDetector gestureDetector;

        private RectF tooltipRect;
        private String tooltipText = "显示控件信息";
        private boolean showTooltip = false;

        public OverlayView(Context context) {
            super(context);
            init();
        }

        private void init() {
            // 初始化描边画笔
            borderPaint = new Paint();
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3);

            overlayPaint = new Paint();
            overlayPaint.setColor(Color.argb(100, 0, 0, 0)); // Semi-transparent black
            overlayPaint.setStyle(Paint.Style.FILL);

            // 初始化文本画笔
            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(30);
            textPaint.setAntiAlias(true);

            gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    float x = e.getX();
                    float y = e.getY();

                    // Check if tooltip is showing and was clicked
                    if (showTooltip && tooltipRect != null && tooltipRect.contains(x, y)) {
                        // Show dialog with view info
                        try {
                            showViewInfoDialog(selectedViewInfo);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                        return true;
                    }

                    ViewInfo bestMatch = null;
                    int bestMatchArea = Integer.MAX_VALUE;


                    for (ViewInfo info : viewInfoList) {
                        // 检查点击是否在视图边界内
                        if (x >= info.x && x <= info.x + info.width &&
                                y >= info.y && y <= info.y + info.height) {

                            // 计算当前视图的面积
                            int area = info.width * info.height;

                            // 如果找到更小的视图（更具体的视图），就更新最佳匹配
                            if (area < bestMatchArea) {
                                bestMatchArea = area;
                                bestMatch = info;
                            }
                        }
                    }

                    // 如果找到了匹配的视图
                    if (bestMatch != null) {
                        selectedViewInfo = bestMatch;
                        showTooltip = true;

                        // 为选中的视图设置工具提示
                        setupTooltip(selectedViewInfo);

                        invalidate();
                        return true;
                    }


                    // If clicked elsewhere, clear selection
                    selectedViewInfo = null;
                    showTooltip = false;
                    invalidate();
                    return true;
                }
            });
        }
        private void setupTooltip(ViewInfo info) {
            // Measure tooltip text
            float tooltipTextWidth = textPaint.measureText(tooltipText);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float tooltipTextHeight = metrics.bottom - metrics.top;

            // Padding for tooltip
            float padding = 16;

            // Position tooltip near the view (above it if possible)
            float tooltipX = info.x + (float) info.width / 2 - tooltipTextWidth / 2 - padding;
            float tooltipY = info.y - tooltipTextHeight - padding * 2;

            // If too close to top, position below the view
            if (tooltipY < 0) {
                tooltipY = info.y + info.height + padding;
            }

            // Create tooltip rectangle
            tooltipRect = new RectF(
                    tooltipX,
                    tooltipY,
                    tooltipX + tooltipTextWidth + padding * 2,
                    tooltipY + tooltipTextHeight + padding * 2);
        }
        public void setRootView(ViewGroup root) {
            this.rootView = root;
            collectViewInfo();
        }
        private int dpToPx(int dp) {
            return (int) (dp * getContext().getResources().getDisplayMetrics().density);
        }
        private void showViewInfoDialog(ViewInfo info) {
            if (info == null || getContext() == null) return;

            Context context = getContext();

            // 使用原生LinearLayout替代CardView
            LinearLayout rootLayout = new LinearLayout(context);
            rootLayout.setOrientation(LinearLayout.VERTICAL);
            rootLayout.setBackgroundColor(Color.WHITE);

            // 添加边框和背景
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dpToPx(8));
            shape.setColor(Color.WHITE);
            shape.setStroke(dpToPx(1), Color.parseColor("#DDDDDD"));

            rootLayout.setBackground(shape);

            // 添加一点内边距
            rootLayout.setPadding(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));

            // 设置阴影效果（这是通过边框模拟的，因为原生视图不支持真实阴影）
            LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                    dpToPx(300), LinearLayout.LayoutParams.WRAP_CONTENT);
            rootLayout.setLayoutParams(rootParams);

            // 创建内容容器
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

            // 标题栏
            LinearLayout titleBar = new LinearLayout(context);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);

            // 标题文本
            TextView titleText = new TextView(context);
            titleText.setText("控件信息");
            titleText.setTextSize(18);
            titleText.setTextColor(Color.parseColor("#333333"));
            titleText.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams titleTextParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            titleText.setLayoutParams(titleTextParams);

            // 关闭按钮 - 使用文本代替图标
            TextView closeButton = new TextView(context);
            closeButton.setText("✕"); // 使用Unicode X符号
            closeButton.setTextColor(Color.parseColor("#777777"));
            closeButton.setTextSize(18);
            closeButton.setPadding(dpToPx(8), 0, 0, 0);
            closeButton.setGravity(Gravity.CENTER);

            // 添加标题和关闭按钮到标题栏
            titleBar.addView(titleText);
            titleBar.addView(closeButton);
            container.addView(titleBar);

            // 分隔线
            View divider = new View(context);
            divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
            dividerParams.topMargin = dpToPx(8);
            dividerParams.bottomMargin = dpToPx(8);
            divider.setLayoutParams(dividerParams);
            container.addView(divider);

            // 内容区域的滚动视图
            ScrollView scrollView = new ScrollView(context);
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            scrollView.setLayoutParams(scrollParams);

            // 内容文本
            TextView contentText = new TextView(context);
            contentText.setTextSize(14);
            contentText.setTextColor(Color.parseColor("#333333"));
            contentText.setLineSpacing(dpToPx(4), 1.0f);
            contentText.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

            // 准备详细信息
            StringBuilder sb = new StringBuilder();
            sb.append("类名: ").append(info.className).append("\n\n");
            sb.append("原生类名: ").append(info.nativeClassName).append("\n\n");
            sb.append("位置: x=").append(info.x).append(", y=").append(info.y).append("\n\n");
            sb.append("尺寸: ").append(info.width).append(" × ").append(info.height).append("\n\n");

            if (info.view instanceof TextView) {
                sb.append("文本: ").append(((TextView) info.view).getText()).append("\n\n");
                sb.append("Spanned: ").append(info.spannedInfo).append("\n\n");
            }

//            try {
//                if (info.view.getId() != View.NO_ID) {
//                    sb.append("资源ID: ").append(getContext().getResources().getResourceEntryName(info.view.getId()));
//                }
//            } catch (Exception e) {
//                // Resource not found
//            }

            contentText.setText(sb.toString());
            scrollView.addView(contentText);
            container.addView(scrollView);

            // 把所有内容添加到根布局
            rootLayout.addView(container);

            // 创建PopupWindow
            final PopupWindow popupWindow = new PopupWindow(
                    rootLayout,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true);  // Focusable

            // 设置触摸外部不关闭
            popupWindow.setOutsideTouchable(false);
            popupWindow.setTouchable(true);

            // 使用一个最小的背景，不使用ColorDrawable
            try {
                // 简单的空白背景
                Drawable background = new PaintDrawable(Color.TRANSPARENT);
                popupWindow.setBackgroundDrawable(background);
            } catch (Exception e) {
                // 如果上面的失败，尝试更简单的方法
                try {
                    popupWindow.setBackgroundDrawable(null);
                } catch (Exception e2) {
                    XposedBridge.log("无法设置PopupWindow背景");
                }
            }

            // 设置关闭按钮点击事件
            closeButton.setOnClickListener(v -> popupWindow.dismiss());

            // 显示在屏幕中央
            try {
                popupWindow.showAtLocation(this, Gravity.CENTER, 0, 0);
            } catch (Exception e) {
                XposedBridge.log("OverlayView,显示PopupWindow失败");
                // 如果显示失败，可以尝试使用Toast显示简单信息
                String message = "类: " + info.className + "\n" +
                        "尺寸: " + info.width + "x" + info.height;
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        }
        /**
         * 收集视图信息
         */
        private void collectViewInfo() {
            viewInfoList.clear();
            if (rootView != null) {
                collectViewInfoRecursive(rootView);
            }
        }
        public static String getAndroidViewClassName(View view) {
            Class<?> clazz = view.getClass();
            while (clazz != null && !clazz.getName().startsWith("android.")) {
                clazz = clazz.getSuperclass();
            }
            return clazz != null ? clazz.getName() : "Unknown";
        }

        /**
         * 递归收集视图信息
         */
        @SuppressLint("DefaultLocale")
        private void collectViewInfoRecursive(View view) {
            // 获取视图在屏幕上的位置
            int[] location = new int[2];
            view.getLocationOnScreen(location);

            // 创建视图信息对象
            ViewInfo info = new ViewInfo();
            info.view = view;
            info.x = location[0];
            info.y = location[1];
            info.width = view.getWidth();
            info.height = view.getHeight();
            info.className = view.getClass().getSimpleName();
            info.nativeClassName = getAndroidViewClassName(view);

            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                CharSequence text = textView.getText();

                if (text instanceof Spanned) {
                    Spanned spannedText = (Spanned) text;
                    Object[] spans = spannedText.getSpans(0, text.length(), Object.class);

                    StringBuilder spanInfo = new StringBuilder("\n");
                    int index = 0;
                    for (Object span : spans) {
                        int start = spannedText.getSpanStart(span);
                        int end = spannedText.getSpanEnd(span);

                        CharSequence spannedSubText = spannedText.subSequence(start, end);

                        // 处理 SimpleName 可能为空的情况
                        String spanClassName = span.getClass().getSimpleName();
                        if (spanClassName.isEmpty()) {
                            spanClassName = span.getClass().getName(); // 获取完整类名
                        }

                        spanInfo.append(String.format("#%d → %s [%d-%d]: \"%s\"\n",
                                index + 1, spanClassName, start, end, spannedSubText));
                        index++;
                    }
                    info.spannedInfo = spanInfo.toString(); // 记录 Spanned 相关信息
                }
            }

            // 添加到列表
            viewInfoList.add(info);

            // 如果是ViewGroup，继续收集子视图
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    collectViewInfoRecursive(group.getChildAt(i));
                }
            }
        }
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
        }
        @SuppressLint("DrawAllocation")
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (viewInfoList.isEmpty() && rootView != null) {
                collectViewInfo();
            }

            canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);

            if (selectedViewInfo != null) {
                // Use Porter-Duff mode to create a transparent hole
                overlayPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                canvas.drawRect(
                        selectedViewInfo.x,
                        selectedViewInfo.y,
                        selectedViewInfo.x + selectedViewInfo.width,
                        selectedViewInfo.y + selectedViewInfo.height,
                        overlayPaint
                );
                overlayPaint.setXfermode(null);

                // Reset color for future drawings
                overlayPaint.setColor(Color.argb(100, 0, 0, 0));
            }

            // 绘制所有视图的边界和信息
            for (ViewInfo info : viewInfoList) {
                // 设置边框颜色
                int color = getColorForViewClass(info.className);

                borderPaint.setColor(color);
                borderPaint.setStrokeWidth(5);

                // 绘制视图边框
                tempRect.set(info.x, info.y, info.x + info.width, info.y + info.height);
                canvas.drawRect(tempRect, borderPaint);

                if (showTooltip && tooltipRect != null) {
                    // Draw tooltip background (白色背景)
                    Paint tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    tooltipBgPaint.setColor(Color.WHITE); // 改为白色背景
                    tooltipBgPaint.setShadowLayer(5, 0, 2, Color.argb(50, 0, 0, 0)); // 添加轻微阴影，增强视觉效果
                    canvas.drawRoundRect(tooltipRect, 8, 8, tooltipBgPaint);

                    // 创建黑色文字的画笔
                    Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    tooltipTextPaint.setColor(Color.BLACK); // 黑色文字
                    tooltipTextPaint.setTextSize(textPaint.getTextSize()); // 保持原有文字大小
                    tooltipTextPaint.setTypeface(textPaint.getTypeface()); // 保持原有字体

                    // Draw tooltip text
                    float textX = tooltipRect.left + (tooltipRect.width() - tooltipTextPaint.measureText(tooltipText)) / 2;
                    float textY = tooltipRect.top + (tooltipRect.height() + tooltipTextPaint.getTextSize()) / 2 - tooltipTextPaint.getFontMetrics().bottom;
                    canvas.drawText(tooltipText, textX, textY, tooltipTextPaint); // 使用黑色文字画笔
                }
            }
        }

        /**
         * 视图信息类
         */
        private static class ViewInfo {
            View view;
            int x;
            int y;
            int width;
            int height;
            String className;
            String nativeClassName;
            String spannedInfo;
        }
    }
}
