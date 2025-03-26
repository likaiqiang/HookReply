package com.example.xposed;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ClickRecorder implements IXposedHookLoadPackage {
    private static final String TAG = "ClickRecorder";
    private static final String SAVE_DIR = Environment.getExternalStorageDirectory() + "/ClickScreenshots/";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.xingin.xhs")) {
            return;
        }
        // 仅针对目标应用（可选）
        // if (!lpparam.packageName.equals("com.target.app")) return;

        // Hook View的performClick方法
        XposedHelpers.findAndHookMethod(View.class, "performClick", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                Activity activity = (Activity) view.getContext();

                // 获取View的Bounds
                Rect bounds = new Rect();
                view.getGlobalVisibleRect(bounds);

                // 截图并保存
                captureView(activity, bounds);
            }
        });

        // Hook触摸事件以获取点击坐标（备用方案）
//        XposedHelpers.findAndHookMethod(
//                View.class,
//                "dispatchTouchEvent",
//                MotionEvent.class,
//                new XC_MethodHook() {
//                    @Override
//                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                        MotionEvent event = (MotionEvent) param.args[0];
//                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
//                            float x = event.getRawX();
//                            float y = event.getRawY();
//                            XposedBridge.log("点击坐标: (" + x + ", " + y + ")");
//
//                            // 根据坐标查找View
//                            View rootView = ((View) param.thisObject).getRootView();
//                            View targetView = findViewAt(rootView, (int) x, (int) y);
//                            if (targetView != null) {
//                                Rect bounds = new Rect();
//                                targetView.getGlobalVisibleRect(bounds);
//                                captureView((Activity) targetView.getContext(), bounds);
//                            }
//                        }
//                    }
//                }
//        );
    }

    // 递归查找包含坐标的View
    private View findViewAt(View view, int x, int y) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                View foundView = findViewAt(child, x, y);
                if (foundView != null) return foundView;
            }
        }
        Rect rect = new Rect();
        view.getGlobalVisibleRect(rect);
        return rect.contains(x, y) ? view : null;
    }

    // 截图并保存
    private void captureView(Activity activity, Rect bounds) {
        try {
            // 创建保存目录
            File dir = new File(SAVE_DIR);
            if (!dir.exists()) dir.mkdirs();

            // 生成文件名
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = SAVE_DIR + "screenshot_" + timestamp + ".png";

            // 使用PixelCopy截图（API 24+）
            {
                Bitmap bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
                int[] location = new int[2];
                activity.getWindow().getDecorView().getLocationOnScreen(location);
                Rect screenBounds = new Rect(
                        bounds.left - location[0],
                        bounds.top - location[1],
                        bounds.right - location[0],
                        bounds.bottom - location[1]
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PixelCopy.request(
                            activity.getWindow(),
                            screenBounds,
                            bitmap,
                            copyResult -> {
                                if (copyResult == PixelCopy.SUCCESS) {
                                    saveBitmap(bitmap, filename);
                                }
                            },
                            new Handler(Looper.getMainLooper())
                    );
                }
            }
        } catch (Exception e) {
            XposedBridge.log("截图失败: " + e.getMessage());
        }
    }

    private void saveBitmap(Bitmap bitmap, String filename) {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            XposedBridge.log("截图已保存: " + filename);
        } catch (Exception e) {
            XposedBridge.log("保存失败: " + e.getMessage());
        }
    }
}