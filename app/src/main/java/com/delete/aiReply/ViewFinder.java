package com.delete.aiReply;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.core.util.Predicate;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ViewFinder {

    /**
     * 查找指定类型的子 View（仅返回第一个匹配项）
     */
    public static View findChild(View parent, Predicate<View> predicate) {
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (predicate.test(child)) { // 使用 Predicate 进行匹配
                    return child;
                }
                View result = findChild(child, predicate);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * 查找所有指定类型的子 View
     */
    public static List<View> findChilds(View parent, Predicate<View> predicate) {
        List<View> result = new ArrayList<>();
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (predicate.test(child)) {
                    result.add(child);
                }
                result.addAll(findChilds(child, predicate));
            }
        }
        return result;
    }

    /**
     * 查找指定类型的父 View（返回第一个匹配项）
     */
    public static View findParent(View child, Predicate<View> predicate) {
        ViewParent parent = child.getParent();
        while (parent instanceof View) {
            View parentView = (View) parent;
            if (predicate.test(parentView)) {
                return parentView;
            }
            parent = parentView.getParent();
        }
        return null;
    }

    /**
     * 查找所有符合类型的父 View
     */
    public static List<View> findParents(View child, Predicate<View> predicate) {
        List<View> result = new ArrayList<>();
        View parent = (View) child.getParent();
        while (parent != null) {
            if (predicate.test(parent)) {
                result.add(parent);
            }
            parent = (View) parent.getParent();
        }
        return result;
    }
    public static List<View> getAllChildrenInRecycler(RecyclerView recycler) {
        if (recycler == null) return new ArrayList<>();

        List<View> allViews = new ArrayList<>();
        RecyclerView.Adapter adapter = recycler.getAdapter();

        if (adapter != null) {
            int itemCount = adapter.getItemCount();
            // 获取当前可见的子视图
            for (int i = 0; i < recycler.getChildCount(); i++) {
                View child = recycler.getChildAt(i);
                allViews.add(child);
            }

            // 获取不可见的子视图 - 通过适配器创建和绑定
            RecyclerView.RecycledViewPool viewPool = recycler.getRecycledViewPool();
            RecyclerView.LayoutManager layoutManager = recycler.getLayoutManager();

            // 对于不在屏幕上的项，需要通过ViewHolder来处理
            for (int i = 0; i < itemCount; i++) {
                // 检查该位置的视图是否已经在可见列表中
                boolean isViewVisible = false;
                for (int j = 0; j < recycler.getChildCount(); j++) {
                    View child = recycler.getChildAt(j);
                    RecyclerView.ViewHolder holder = recycler.getChildViewHolder(child);
                    if (holder.getAdapterPosition() == i) {
                        isViewVisible = true;
                        break;
                    }
                }

                // 如果不可见，尝试从回收池获取或创建新的
                if (!isViewVisible) {
                    try {
                        // 注意：这种方法可能会影响RecyclerView的缓存逻辑，应谨慎使用
                        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, adapter.getItemViewType(i));
                        adapter.bindViewHolder(holder, i);
                        allViews.add(holder.itemView);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return allViews;
    }
}
