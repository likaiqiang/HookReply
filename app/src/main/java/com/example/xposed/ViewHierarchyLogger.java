package com.example.xposed;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ViewHierarchyLogger {

    private static final String TAG = "ViewHierarchyLog";
    private static final int MAX_DEPTH = 20; // 避免过深的递归导致堆栈溢出

    /**
     * 在performClick中hook并打印视图层次结构
     */
    public static void hookPerformClick(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log(TAG + ": Hooking View.performClick");

            XposedHelpers.findAndHookMethod(View.class, "performClick", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View clickedView = (View) param.thisObject;
                    XposedBridge.log(TAG + ": View被点击: " + getViewInfo(clickedView));

                    // 查找根视图
                    View rootView = findRootView(clickedView);

                    // 打印完整层次结构，并高亮当前点击的视图
                    XposedBridge.log(TAG + ": ========= 视图层次结构开始 =========");
                    dumpViewHierarchy(rootView, 0, clickedView);
                    XposedBridge.log(TAG + ": ========= 视图层次结构结束 =========");
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook View.performClick 失败: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * 查找视图的根节点
     */
    private static View findRootView(View view) {
        View parent = (View) view.getParent();
        int count = 0;

        while (parent instanceof View && count < MAX_DEPTH) {
            view = parent;
            parent = (View) view.getParent();
            count++;
        }

        return view;
    }

    /**
     * 递归打印视图层次结构
     * @param view 当前视图
     * @param depth 当前深度
     * @param targetView 目标视图（需要高亮标记的视图）
     */
    private static void dumpViewHierarchy(View view, int depth, View targetView) {
        if (view == null || depth > MAX_DEPTH) return;

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }

        String marker = (view == targetView) ? " <--- 当前点击的视图" : "";
        XposedBridge.log(TAG + ": " + indent + getDetailedViewInfo(view) + marker);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int childCount = group.getChildCount();

            // 打印子视图数量
            XposedBridge.log(TAG + ": " + indent + "└── 子视图数量: " + childCount);

            // 递归打印子视图
            for (int i = 0; i < childCount; i++) {
                View child = group.getChildAt(i);
                dumpViewHierarchy(child, depth + 1, targetView);
            }
        }
    }

    /**
     * 获取基本视图信息
     */
    private static String getViewInfo(View view) {
        if (view == null) return "null";

        String id = "";
        try {
            if (view.getId() != View.NO_ID) {
                id = " id=0x" + Integer.toHexString(view.getId());
            }
        } catch (Exception e) {
            id = " id=unknown";
        }

        return view.getClass().getName() + id +
                " " + view.getWidth() + "x" + view.getHeight() +
                " visible=" + (view.getVisibility() == View.VISIBLE);
    }

    /**
     * 获取详细的视图信息
     */
    private static String getDetailedViewInfo(View view) {
        if (view == null) return "null";

        String baseInfo = getViewInfo(view);
        StringBuilder info = new StringBuilder(baseInfo);

        info.append(" alpha=").append(view.getAlpha());
        info.append(" enabled=").append(view.isEnabled());
        info.append(" clickable=").append(view.isClickable());
        info.append(" focusable=").append(view.isFocusable());

        // 添加特定类型视图的额外信息
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            info.append(" text=\"").append(tv.getText()).append("\"");
        } else if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            info.append(" contentDesc=\"").append(iv.getContentDescription()).append("\"");
        }

        // 添加布局参数信息
        try {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null) {
                info.append(" layout_width=").append(lp.width);
                info.append(" layout_height=").append(lp.height);
            }
        } catch (Exception e) {
            // 忽略布局参数异常
        }

        // 添加View的tag信息
        Object tag = view.getTag();
        if (tag != null) {
            info.append(" tag=").append(tag);
        }

        return info.toString();
    }

    /**
     * 示例用法：在您的主钩子方法中调用此方法
     */
    public static void main(XC_LoadPackage.LoadPackageParam lpparam) {
        hookPerformClick(lpparam);
    }
}
