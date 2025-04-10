package com.example.aiReply;

import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.*;
import android.view.View;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView反射包装类，用于在不同ClassLoader环境下操作目标应用的RecyclerView
 */
public class RecyclerViewWrapper {
    // 实际的RecyclerView对象
    private final View recyclerView;
    // 目标应用的RecyclerView类
    private final Class<?> recyclerViewClass;
    // 目标应用的ClassLoader
    private final ClassLoader targetClassLoader;

    // 缓存常用的类
    private Class<?> adapterClass;
    private Class<?> viewHolderClass;
    private Class<?> layoutManagerClass;

    /**
     * 构造函数接收View实例
     * @param view 目标RecyclerView视图实例
     * @throws Exception 如果提供的View不是RecyclerView则抛出异常
     */
    public RecyclerViewWrapper(View view) throws Exception {
        this.recyclerView = view;
        this.recyclerViewClass = view.getClass();
        this.targetClassLoader = view.getClass().getClassLoader();

        // 验证是否真的是RecyclerView
        try {
            recyclerViewClass.getMethod("getAdapter");
            recyclerViewClass.getMethod("setLayoutManager", Object.class);

            // 初始化常用类引用
            initClasses();

            XposedBridge.log("成功创建RecyclerViewWrapper，目标类: " + recyclerViewClass.getName());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("提供的View不是RecyclerView: " + e.getMessage());
        }
    }

    /**
     * 初始化RecyclerView相关类的引用
     */
    private void initClasses() throws Exception {
        // 尝试查找Adapter内部类
        try {
            // 不同版本RecyclerView的Adapter类可能有不同路径
            // 尝试获取内部类Adapter
            for (Class<?> innerClass : recyclerViewClass.getDeclaredClasses()) {
                if (innerClass.getSimpleName().equals("Adapter")) {
                    adapterClass = innerClass;
                    break;
                }
            }

            // 如果内部类查找失败，尝试直接加载类
            if (adapterClass == null) {
                String recyclerViewClassName = recyclerViewClass.getName();
                adapterClass = targetClassLoader.loadClass(recyclerViewClassName + "$Adapter");
            }
        } catch (Exception e) {
            XposedBridge.log("无法找到Adapter内部类，将使用动态判断: " + e.getMessage());
        }

        // 尝试查找ViewHolder内部类
        try {
            for (Class<?> innerClass : recyclerViewClass.getDeclaredClasses()) {
                if (innerClass.getSimpleName().equals("ViewHolder")) {
                    viewHolderClass = innerClass;
                    break;
                }
            }

            // 如果内部类查找失败，尝试直接加载类
            if (viewHolderClass == null) {
                String recyclerViewClassName = recyclerViewClass.getName();
                viewHolderClass = targetClassLoader.loadClass(recyclerViewClassName + "$ViewHolder");
            }
        } catch (Exception e) {
            XposedBridge.log("无法找到ViewHolder内部类，将使用动态判断: " + e.getMessage());
        }

        // 尝试加载LayoutManager类
        try {
            String packageName = recyclerViewClass.getPackage().getName();
            layoutManagerClass = targetClassLoader.loadClass(packageName + ".LayoutManager");
        } catch (Exception e) {
            // 尝试内部类
            try {
                for (Class<?> innerClass : recyclerViewClass.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("LayoutManager")) {
                        layoutManagerClass = innerClass;
                        break;
                    }
                }
            } catch (Exception ex) {
                XposedBridge.log("无法找到LayoutManager类，将使用动态判断: " + e.getMessage());
            }
        }
    }

    /**
     * 获取适配器
     */
    public AdapterWrapper getAdapter() throws Exception {
        Method method = recyclerViewClass.getMethod("getAdapter");
        Object adapter = method.invoke(recyclerView);
        return adapter != null ? new AdapterWrapper(adapter) : null;
    }

    /**
     * 设置适配器
     */
    public void setAdapter(AdapterWrapper adapter) throws Exception {
        Method method = recyclerViewClass.getMethod("setAdapter", Object.class);
        method.invoke(recyclerView, adapter.getWrappedAdapter());
    }

    /**
     * 获取布局管理器
     */
    public Object getLayoutManager() throws Exception {
        Method method = recyclerViewClass.getMethod("getLayoutManager");
        return method.invoke(recyclerView);
    }

    /**
     * 设置布局管理器
     */
    public void setLayoutManager(Object layoutManager) throws Exception {
        Method method = recyclerViewClass.getMethod("setLayoutManager", Object.class);
        method.invoke(recyclerView, layoutManager);
    }

    /**
     * 滚动到指定位置
     */
    public void scrollToPosition(int position) throws Exception {
        Method method = recyclerViewClass.getMethod("scrollToPosition", int.class);
        method.invoke(recyclerView, position);
    }

    /**
     * 平滑滚动到指定位置
     */
    public void smoothScrollToPosition(int position) throws Exception {
        Method method = recyclerViewClass.getMethod("smoothScrollToPosition", int.class);
        method.invoke(recyclerView, position);
    }

    /**
     * 获取指定位置的ViewHolder
     */
    public ViewHolderWrapper findViewHolderForAdapterPosition(int position) throws Exception {
        Method method = recyclerViewClass.getMethod("findViewHolderForAdapterPosition", int.class);
        Object viewHolder = method.invoke(recyclerView, position);
        return viewHolder != null ? new ViewHolderWrapper(viewHolder) : null;
    }

    /**
     * 获取指定位置的ViewHolder (布局位置)
     */
    public ViewHolderWrapper findViewHolderForLayoutPosition(int position) throws Exception {
        Method method = recyclerViewClass.getMethod("findViewHolderForLayoutPosition", int.class);
        Object viewHolder = method.invoke(recyclerView, position);
        return viewHolder != null ? new ViewHolderWrapper(viewHolder) : null;
    }

    /**
     * 通知数据集变化
     */
    public void notifyDataSetChanged() throws Exception {
        AdapterWrapper adapter = getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * 创建LinearLayoutManager
     */
    public Object createLinearLayoutManager(Context context, int orientation, boolean reverseLayout) throws Exception {
        ClassLoader contextClassLoader = context.getClassLoader();

        // 尝试找到LinearLayoutManager类
        String packageName = recyclerViewClass.getPackage().getName();
        Class<?> linearLayoutManagerClass = contextClassLoader.loadClass(packageName + ".LinearLayoutManager");

        // 获取构造函数
        Constructor<?> constructor = linearLayoutManagerClass.getConstructor(
                Context.class, int.class, boolean.class);

        // 创建实例
        return constructor.newInstance(context, orientation, reverseLayout);
    }

    /**
     * 创建GridLayoutManager
     */
    public Object createGridLayoutManager(Context context, int spanCount) throws Exception {
        ClassLoader contextClassLoader = context.getClassLoader();

        // 尝试找到GridLayoutManager类
        String packageName = recyclerViewClass.getPackage().getName();
        Class<?> gridLayoutManagerClass = contextClassLoader.loadClass(packageName + ".GridLayoutManager");

        // 获取构造函数
        Constructor<?> constructor = gridLayoutManagerClass.getConstructor(
                Context.class, int.class);

        // 创建实例
        return constructor.newInstance(context, spanCount);
    }

    /**
     * 获取项目装饰（分割线等）
     */
    public List<Object> getItemDecorations() throws Exception {
        List<Object> result = new ArrayList<>();

        Method method = recyclerViewClass.getMethod("getItemDecorationCount");
        int count = (int) method.invoke(recyclerView);

        Method getMethod = recyclerViewClass.getMethod("getItemDecorationAt", int.class);
        for (int i = 0; i < count; i++) {
            result.add(getMethod.invoke(recyclerView, i));
        }

        return result;
    }

    /**
     * 添加项目装饰（分割线等）
     */
    public void addItemDecoration(Object decoration) throws Exception {
        Method method = recyclerViewClass.getMethod("addItemDecoration", Object.class);
        method.invoke(recyclerView, decoration);
    }

    /**
     * 获取原始RecyclerView对象
     */
    public View getView() {
        return recyclerView;
    }

    /**
     * ViewHolder包装类
     */
    public class ViewHolderWrapper {
        private final Object viewHolder;
        private final Class<?> viewHolderClass;

        public ViewHolderWrapper(Object viewHolder) {
            this.viewHolder = viewHolder;
            this.viewHolderClass = viewHolder.getClass();
        }

        /**
         * 获取ViewHolder持有的itemView
         */
        public View getItemView() throws Exception {
            Field field = findField(viewHolderClass, "itemView");
            field.setAccessible(true);
            return (View) field.get(viewHolder);
        }

        /**
         * 获取适配器位置
         */
        public int getAdapterPosition() throws Exception {
            Method method = viewHolderClass.getMethod("getAdapterPosition");
            return (int) method.invoke(viewHolder);
        }

        /**
         * 获取布局位置
         */
        public int getLayoutPosition() throws Exception {
            Method method = viewHolderClass.getMethod("getLayoutPosition");
            return (int) method.invoke(viewHolder);
        }

        /**
         * 获取原始ViewHolder对象
         */
        public Object getWrappedViewHolder() {
            return viewHolder;
        }

        /**
         * 在ViewHolder类层次结构中查找字段
         */
        private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // 如果当前类中没有该字段，则查找父类
                Class<?> superClass = clazz.getSuperclass();
                if (superClass != null) {
                    return findField(superClass, fieldName);
                }
                throw e;
            }
        }
    }

    /**
     * Adapter包装类
     */
    public class AdapterWrapper {
        private final Object adapter;
        private final Class<?> adapterClass;

        public AdapterWrapper(Object adapter) {
            this.adapter = adapter;
            this.adapterClass = adapter.getClass();
        }

        /**
         * 获取项目数量
         */
        public int getItemCount() throws Exception {
            Method method = adapterClass.getMethod("getItemCount");
            return (int) method.invoke(adapter);
        }

        /**
         * 获取项目类型
         */
        public int getItemViewType(int position) throws Exception {
            Method method = adapterClass.getMethod("getItemViewType", int.class);
            return (int) method.invoke(adapter, position);
        }

        /**
         * 通知数据集变化
         */
        public void notifyDataSetChanged() throws Exception {
            Method method = adapterClass.getMethod("notifyDataSetChanged");
            method.invoke(adapter);
        }

        /**
         * 通知项目插入
         */
        public void notifyItemInserted(int position) throws Exception {
            Method method = adapterClass.getMethod("notifyItemInserted", int.class);
            method.invoke(adapter, position);
        }

        /**
         * 通知项目移除
         */
        public void notifyItemRemoved(int position) throws Exception {
            Method method = adapterClass.getMethod("notifyItemRemoved", int.class);
            method.invoke(adapter, position);
        }

        /**
         * 通知项目更新
         */
        public void notifyItemChanged(int position) throws Exception {
            Method method = adapterClass.getMethod("notifyItemChanged", int.class);
            method.invoke(adapter, position);
        }

        /**
         * 通知项目范围更新
         */
        public void notifyItemRangeChanged(int positionStart, int itemCount) throws Exception {
            Method method = adapterClass.getMethod("notifyItemRangeChanged", int.class, int.class);
            method.invoke(adapter, positionStart, itemCount);
        }

        /**
         * 获取原始Adapter对象
         */
        public Object getWrappedAdapter() {
            return adapter;
        }

        /**
         * 创建ViewHolder（仅当你需要创建自己的ViewHolder时使用）
         */
        public ViewHolderWrapper createViewHolder(View itemView) throws Exception {
            // 这是比较棘手的，因为需要找到合适的ViewHolder构造函数
            // 示例是一个基本尝试，可能需要根据具体情况调整
            try {
                // 尝试用内部ViewHolder类创建
                if (RecyclerViewWrapper.this.viewHolderClass != null) {
                    Constructor<?> constructor = RecyclerViewWrapper.this.viewHolderClass.getConstructor(View.class);
                    Object viewHolder = constructor.newInstance(itemView);
                    return new ViewHolderWrapper(viewHolder);
                }

                // 尝试在目标应用中查找合适的ViewHolder实现
                String packageName = recyclerViewClass.getPackage().getName();
                Class<?> viewHolderClass = targetClassLoader.loadClass(packageName + ".ViewHolder");
                Constructor<?> constructor = viewHolderClass.getConstructor(View.class);
                Object viewHolder = constructor.newInstance(itemView);
                return new ViewHolderWrapper(viewHolder);
            } catch (Exception e) {
                XposedBridge.log("创建ViewHolder失败: " + e.getMessage());
                throw e;
            }
        }
    }
}