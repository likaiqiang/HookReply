package com.example.xposed;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class LogFloatingView {
    private static LogFloatingView instance;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private LinearLayout floatView;
    private TextView logTextView;
    private float lastX, lastY;
    private boolean isMoving = false;

    public static LogFloatingView getInstance(Context context) {
        if (instance == null) {
            instance = new LogFloatingView(context);
        }
        return instance;
    }

    private LogFloatingView(Context context) {
        Context appContext = context.getApplicationContext();
        windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);

        // 创建一个 ScrollView 让日志可滚动
        ScrollView scrollView = new ScrollView(context);
        logTextView = new TextView(context);
        logTextView.setTextSize(14);
        logTextView.setPadding(20, 20, 20, 20);
        logTextView.setBackgroundColor(0xAA000000); // 半透明黑色
        logTextView.setTextColor(0xFFFFFFFF); // 白色字体

        scrollView.addView(logTextView);

        // 外部包裹一层 LinearLayout
        floatView = new LinearLayout(context);
        floatView.addView(scrollView);
        floatView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // 悬浮窗 Layout 参数
        layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY; // 适用于 Android 8.0+
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.width = 600;
        layoutParams.height = 400;
        layoutParams.x = 100;
        layoutParams.y = 100;

        // 添加拖动功能
        floatView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        isMoving = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - lastX;
                        float dy = event.getRawY() - lastY;
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) { // 防止误触
                            isMoving = true;
                            layoutParams.x += dx;
                            layoutParams.y += dy;
                            windowManager.updateViewLayout(floatView, layoutParams);
                            lastX = event.getRawX();
                            lastY = event.getRawY();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        return isMoving;
                }
                return false;
            }
        });

        // 添加悬浮窗
        windowManager.addView(floatView, layoutParams);
    }

    public void appendLog(final String log) {
        if (logTextView != null) {
            logTextView.post(() -> {
                logTextView.append(log + "\n");
                // 自动滚动到底部
                if (logTextView.getParent() instanceof ScrollView) {
                    ScrollView scrollView = (ScrollView) logTextView.getParent();
                    scrollView.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    public void remove() {
        if (windowManager != null && floatView != null) {
            windowManager.removeView(floatView);
            floatView = null;
            instance = null;
        }
    }
}
