package com.example.xposed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;

import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ClickRecorder2 implements IXposedHookLoadPackage {
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
            protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                final View view = (View) param.thisObject;
                final Activity activity = (Activity) view.getContext();

                // 1. 暂停点击事件
                param.setResult(false); // 阻止原方法执行

                // 2. 获取View的Bounds
                final Rect bounds = new Rect();
                view.getGlobalVisibleRect(bounds);

                // 3. 异步截图
                captureView(activity, bounds, () -> {
                    // 4. 截图完成后，手动触发原performClick
                    XposedBridge.log("截图完成，继续执行点击事件");
                    view.performClick();
                });
            }
        });
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
    interface CaptureCallback {
        void onCaptureDone();
    }
    // 异步截图方法（带回调）
    private void captureView(Activity activity, Rect bounds, CaptureCallback callback) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String filename = SAVE_DIR + "screenshot_" + timestamp + ".png";
        new Thread(() -> {
            try {
                // 在主线程执行截图操作
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    try {
                        // 截取全屏并标记Bounds
                        View rootView = activity.getWindow().getDecorView();
                        Bitmap bitmap = Bitmap.createBitmap(rootView.getWidth(), rootView.getHeight(), Bitmap.Config.ARGB_8888);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            PixelCopy.request(
                                    activity.getWindow(),
                                    new Rect(0, 0, rootView.getWidth(), rootView.getHeight()),
                                    bitmap,
                                    copyResult -> {
                                        if (copyResult == PixelCopy.SUCCESS) {
                                            Bitmap marked = drawBoundsOnBitmap(activity,bitmap, bounds );
                                            saveBitmap(marked,filename);
                                            callback.onCaptureDone();
                                        }
                                    },
                                    new Handler(Looper.getMainLooper())
                            );
                        }
                    } catch (Exception e) {
                        XposedBridge.log("截图异常: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                XposedBridge.log("线程异常: " + e.getMessage());
            }
        }).start();
    }

    private Bitmap drawBoundsOnBitmap(Context context, Bitmap source, Rect bounds) {
            // 复制Bitmap以避免修改原始数据
            Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(result);

            // 创建红色画笔
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f); // 线宽5像素

            // 计算相对于窗口的坐标
            View rootView = ((Activity) context).getWindow().getDecorView();
            int[] windowLocation = new int[2];
            rootView.getLocationOnScreen(windowLocation);

            Rect relativeBounds = new Rect(
                    bounds.left - windowLocation[0],
                    bounds.top - windowLocation[1],
                    bounds.right - windowLocation[0],
                    bounds.bottom - windowLocation[1]
            );

            // 绘制矩形框
            canvas.drawRect(relativeBounds, paint);
            return result;
        }

    private void saveBitmap(Bitmap bitmap, String filename) {
            try (FileOutputStream fos = new FileOutputStream(filename)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                XposedBridge.log("截图已保存: " + filename);
            } catch (Exception e) {
                XposedBridge.log("保存失败: " + e.getMessage());
            }
        }

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
