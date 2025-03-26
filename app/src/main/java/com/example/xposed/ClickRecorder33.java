package com.example.xposed;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ClickRecorder33 implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.xingin.xhs")) {
            return;
        }
        //com.xingin.matrix.notedetail.NoteDetailActivity
        XposedHelpers.findAndHookMethod(Activity.class, "onWindowFocusChanged",
                boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        boolean hasFocus = (boolean) param.args[0];

                        // Only proceed if the activity has gained focus
                        if (hasFocus) {
                            String activityName = activity.getClass().getName();
                            XposedBridge.log("Activity with focus: " + activityName);

                            // Check if this is our target activity
                            if (activityName.equals("com.xingin.matrix.notedetail.NoteDetailActivity")) {
                                XposedBridge.log("Target NoteDetailActivity found!");
                                // Find all RecyclerView instances in the activity
                                findRecyclerViews(activity);
                            }
                        }
                    }
                });
//        XposedHelpers.findAndHookMethod(View.class, "performClick", new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                View view = (View) param.thisObject;
//                Activity activity = (Activity) view.getContext();
//
//                // 获取View的Bounds
//                Rect bounds = new Rect();
//                view.getGlobalVisibleRect(bounds);
//
//            }
//        });
    }
    private void findRecyclerViews(Activity activity) {
        // Get the root view of the activity
        View rootView = activity.getWindow().getDecorView().findViewById(android.R.id.content);

        // Start recursive search for RecyclerView instances
        findRecyclerViewsRecursive(rootView, 0, activity.getClass().getName());
    }
    private void findRecyclerViewsRecursive(View view, int depth, String activityName) {
        // Check if the current view is a RecyclerView
        if (view instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) view;
            String viewId = "No ID";

            try {
                if (view.getId() != View.NO_ID) {
                    viewId = view.getResources().getResourceName(view.getId());
                }
            } catch (Exception e) {
                // Ignore exceptions when getting resource name
            }

            XposedBridge.log("Found RecyclerView in " + activityName +
                    " - ID: " + viewId +
                    " - Position: " + getViewPosition(view) +
                    " - ItemCount: " + recyclerView.getAdapter().getItemCount());

            // Log RecyclerView properties
            if (recyclerView.getAdapter() != null) {
                XposedBridge.log("  Adapter: " + recyclerView.getAdapter().getClass().getName());
            }
            if (recyclerView.getLayoutManager() != null) {
                XposedBridge.log("  LayoutManager: " + recyclerView.getLayoutManager().getClass().getName());
            }

            // Try to dump the RecyclerView's field values to find more information
            dumpRecyclerViewFields(recyclerView);

            // Store reference to this RecyclerView for potential future use
            // This approach allows you to access this RecyclerView instance elsewhere in your code
            XposedBridge.log("  Storing reference to RecyclerView for later access");
            storeRecyclerViewReference(recyclerView, viewId, activityName);
        }

        // If the view is a ViewGroup, search its children recursively
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                findRecyclerViewsRecursive(viewGroup.getChildAt(i), depth + 1, activityName);
            }
        }
    }
    private String getViewPosition(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return "x=" + location[0] + ", y=" + location[1] +
                ", width=" + view.getWidth() + ", height=" + view.getHeight();
    }
    private static final java.util.Map<String, RecyclerView> foundRecyclerViews = new java.util.HashMap<>();

    private void storeRecyclerViewReference(RecyclerView recyclerView, String viewId, String activityName) {
        String key = activityName + ":" + viewId;
        foundRecyclerViews.put(key, recyclerView);
    }

    // Dump all fields in the RecyclerView using reflection to help with analysis
    private void dumpRecyclerViewFields(RecyclerView recyclerView) {
        XposedBridge.log("  Dumping RecyclerView fields:");

        try {
            // Get all declared fields from RecyclerView and its superclasses
            Class<?> clazz = recyclerView.getClass();
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(recyclerView);
                        XposedBridge.log("    " + field.getName() + " = " +
                                (value != null ? value.toString() : "null") +
                                (value != null ? " (" + value.getClass().getSimpleName() + ")" : ""));
                    } catch (Exception e) {
                        XposedBridge.log("    " + field.getName() + " = [Error accessing field: " + e.getMessage() + "]");
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            XposedBridge.log("  Error dumping fields: " + e.getMessage());
        }
    }
}
