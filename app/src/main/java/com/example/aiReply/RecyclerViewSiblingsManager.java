package com.example.aiReply;

import android.view.View;
import android.util.LruCache;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

/**
 * RecyclerView兄弟节点查找管理器（反射版本）
 * 专门用于从一个起始位置向前查找满足条件的兄弟节点
 * 使用反射处理不同ClassLoader加载的RecyclerView
 */
public class RecyclerViewSiblingsManager {

    // 使用弱引用持有RecyclerView对象，防止内存泄漏
    private WeakReference<Object> recyclerViewRef;

    // 缓存反射获取的方法
    static Method getAdapterMethod;
    private Method getItemCountMethod;
    private Method getChildCountMethod;
    private Method getChildAtMethod;
    private Method getChildViewHolderMethod;
    private Method getItemViewTypeMethod;
    private Method createViewHolderMethod;
    private Method bindViewHolderMethod;
    static Method findContainingViewHolderMethod;

    // ViewHolder相关的方法和常量
    private Method getBindingAdapterPositionMethod;
    private Method getAdapterPositionMethod;  // 备用方法
    private Method getLayoutPositionMethod;   // 备用方法
    private Field itemViewField;              // ViewHolder的itemView字段
    private int NO_POSITION = -1;             // 通常RecyclerView.NO_POSITION是-1

    // 用于缓存创建的ViewHolder，避免重复创建
    private LruCache<Integer, Object> viewHolderCache;

    /**
     * 条件接口，用于决定何时停止查找
     */
    public interface StopCondition {
        /**
         * 判断是否应该停止查找
         * @param view 当前检查的视图
         * @param position 视图在适配器中的位置
         * @return true表示应该停止查找，false表示继续
         */
        boolean shouldStop(View view, int position) throws InvocationTargetException, IllegalAccessException;
    }
    public interface FindSiblingsBeforeCallBack {
        void onAdd(View view);
    }
    /**
     * 构造函数
     * @param recyclerView 目标RecyclerView对象（可能来自不同的ClassLoader）
     * @param cacheSize 缓存大小
     */
    public RecyclerViewSiblingsManager(Object recyclerView, int cacheSize) {
        if (recyclerView == null) {
            throw new IllegalArgumentException("RecyclerView cannot be null");
        }

        this.recyclerViewRef = new WeakReference<>(recyclerView);
        this.viewHolderCache = new LruCache<>(cacheSize);

        // 初始化反射方法
        initReflectionMethods(recyclerView);
    }

    /**
     * 使用默认缓存大小的构造函数
     * @param recyclerView 目标RecyclerView对象
     */
    public RecyclerViewSiblingsManager(Object recyclerView) {
        this(recyclerView, 10); // 默认缓存10个ViewHolder
    }

    /**
     * 初始化所有需要用到的反射方法
     */
    private void initReflectionMethods(Object recyclerView) {
        try {
            Class<?> recyclerViewClass = recyclerView.getClass();

            // 获取RecyclerView的方法
            getAdapterMethod = getMethodSafely(recyclerViewClass, "getAdapter");
            getChildCountMethod = getMethodSafely(recyclerViewClass, "getChildCount");
            getChildAtMethod = getMethodSafely(recyclerViewClass, "getChildAt", int.class);
            getChildViewHolderMethod = getMethodSafely(recyclerViewClass, "getChildViewHolder", View.class);
            findContainingViewHolderMethod = getMethodSafely(recyclerViewClass, "findContainingViewHolder", View.class);

            // 尝试获取NO_POSITION常量
            try {
                Field noPositionField = recyclerViewClass.getField("NO_POSITION");
                if (noPositionField != null) {
                    NO_POSITION = (int) noPositionField.get(null);
                }
            } catch (Exception e) {
                // 如果获取失败，使用默认值-1
                NO_POSITION = -1;
            }

            // 获取Adapter的方法
            Object adapter = null;
            if (getAdapterMethod != null) {
                adapter = getAdapterMethod.invoke(recyclerView);
            }

            if (adapter != null) {
                Class<?> adapterClass = adapter.getClass();
                getItemCountMethod = getMethodSafely(adapterClass, "getItemCount");
                getItemViewTypeMethod = getMethodSafely(adapterClass, "getItemViewType", int.class);

                // 获取createViewHolder和bindViewHolder方法
                // 需要找到正确的ViewHolder类型
                Class<?> viewHolderClass = findViewHolderClass(recyclerView);
                if (viewHolderClass != null) {
                    try {
                        createViewHolderMethod = adapterClass.getMethod("createViewHolder",
                                recyclerViewClass.getSuperclass(), int.class);
                    } catch (Exception e) {
                        try {
                            createViewHolderMethod = adapterClass.getMethod("createViewHolder",
                                    recyclerViewClass, int.class);
                        } catch (Exception e2) {
                            createViewHolderMethod = null;
                        }
                    }

                    try {
                        bindViewHolderMethod = adapterClass.getMethod("bindViewHolder",
                                viewHolderClass, int.class);
                    } catch (Exception e) {
                        try {
                            // 尝试查找接受Object类型参数的方法
                            for (Method m : adapterClass.getMethods()) {
                                if (m.getName().equals("bindViewHolder") && m.getParameterTypes().length == 2) {
                                    bindViewHolderMethod = m;
                                    break;
                                }
                            }
                        } catch (Exception e2) {
                            bindViewHolderMethod = null;
                        }
                    }
                }
            }

            // 获取ViewHolder的方法和字段
            Class<?> viewHolderClass = findViewHolderClass(recyclerView);
            if (viewHolderClass != null) {
                // 尝试获取不同版本RecyclerView中可能存在的position获取方法
                getBindingAdapterPositionMethod = getMethodSafely(viewHolderClass, "getBindingAdapterPosition");
                getAdapterPositionMethod = getMethodSafely(viewHolderClass, "getAdapterPosition");
                getLayoutPositionMethod = getMethodSafely(viewHolderClass, "getLayoutPosition");

                // 获取itemView字段
                try {
                    itemViewField = viewHolderClass.getField("itemView");
                } catch (Exception e) {
                    // 尝试遍历所有字段找到View类型的字段
                    for (Field field : viewHolderClass.getDeclaredFields()) {
                        if (View.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            itemViewField = field;
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 安全地获取方法，避免异常
     */
    private Method getMethodSafely(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 查找ViewHolder类
     */
    private Class<?> findViewHolderClass(Object recyclerView) {
        try {
            Class<?> recyclerViewClass = recyclerView.getClass();

            // 方法1：通过getChildViewHolder方法的返回类型判断
            if (getChildViewHolderMethod != null) {
                return getChildViewHolderMethod.getReturnType();
            }

            // 方法2：尝试从内部类中查找
            for (Class<?> innerClass : recyclerViewClass.getDeclaredClasses()) {
                if (innerClass.getSimpleName().equals("ViewHolder")) {
                    return innerClass;
                }
            }

            // 方法3：尝试从父类的内部类中查找
            Class<?> superClass = recyclerViewClass.getSuperclass();
            if (superClass != null) {
                for (Class<?> innerClass : superClass.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("ViewHolder")) {
                        return innerClass;
                    }
                }
            }

            // 方法4：尝试直接通过完整路径加载
            ClassLoader classLoader = recyclerViewClass.getClassLoader();
            try {
                // 尝试androidx包路径
                return Class.forName("androidx.recyclerview.widget.RecyclerView$ViewHolder",
                        false, classLoader);
            } catch (ClassNotFoundException e) {
                try {
                    // 尝试旧版support包路径
                    return Class.forName("android.support.v7.widget.RecyclerView$ViewHolder",
                            false, classLoader);
                } catch (ClassNotFoundException e2) {
                    // 都找不到，返回null
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从指定位置向前查找兄弟节点，直到满足停止条件
     *
     * @param startPosition 起始位置
     * @param stopCondition 停止条件
     * @return 找到的视图列表（包括起始视图）
     */
    public List<View> findSiblingsBefore(int startPosition, StopCondition stopCondition) {
        List<View> results = new ArrayList<>();
        Object recyclerView = recyclerViewRef.get();

        if (recyclerView == null) {
            return results;
        }

        try {
            Object adapter = null;
            if (getAdapterMethod != null) {
                adapter = getAdapterMethod.invoke(recyclerView);
            }

            if (adapter == null || getItemCountMethod == null) {
                return results;
            }

            // 获取适配器项目总数
            int itemCount = (int) getItemCountMethod.invoke(adapter);

            // 验证起始位置是否有效
            if (startPosition < 0 || startPosition >= itemCount) {
                return results;
            }

            // 首先添加起始位置的视图
            View startView = getViewAtPosition(startPosition, recyclerView, adapter);
            if (startView != null) {
                results.add(startView);

                // 判断起始视图是否已满足停止条件
                if (stopCondition.shouldStop(startView, startPosition)) {
                    return results;
                }
            }

            // 向前查找，直到满足停止条件或达到列表开头
            for (int pos = startPosition - 1; pos >= 0; pos--) {
                View siblingView = getViewAtPosition(pos, recyclerView, adapter);
                XposedBridge.log("siblingView: " + siblingView);

                if (siblingView != null) {
                    results.add(siblingView);

                    // 检查是否满足停止条件
                    if (stopCondition.shouldStop(siblingView, pos)) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    /**
     * 获取指定位置的视图
     * 首先检查可见区域，然后检查缓存，最后创建新的
     */
    private View getViewAtPosition(int position, Object recyclerView, Object adapter) {
        try {
            // 1. 首先检查这个位置是否在当前可见区域内
            View visibleView = findVisibleViewAtPosition(recyclerView, position);
            if (visibleView != null) {
                return visibleView;
            }

            // 2. 如果不可见，检查缓存中是否有对应的ViewHolder
            Object cachedHolder = viewHolderCache.get(position);

            // 3. 如果缓存中没有且能够创建，则创建新的ViewHolder
            if (cachedHolder == null && createViewHolderMethod != null && bindViewHolderMethod != null) {
                try {
                    int viewType = 0;
                    if (getItemViewTypeMethod != null) {
                        viewType = (int) getItemViewTypeMethod.invoke(adapter, position);
                    }

                    cachedHolder = createViewHolderMethod.invoke(adapter, recyclerView, viewType);
                    bindViewHolderMethod.invoke(adapter, cachedHolder, position);

                    // 加入缓存
                    viewHolderCache.put(position, cachedHolder);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            } else if (cachedHolder != null && bindViewHolderMethod != null) {
                // 更新绑定数据
                bindViewHolderMethod.invoke(adapter, cachedHolder, position);
            }

            // 通过反射获取ViewHolder的itemView属性
            if (cachedHolder != null && itemViewField != null) {
                return (View) itemViewField.get(cachedHolder);
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 在可见区域查找特定位置的视图
     */
    private View findVisibleViewAtPosition(Object recyclerView, int position) {
        try {
            if (getChildCountMethod == null || getChildAtMethod == null || getChildViewHolderMethod == null) {
                return null;
            }

            int childCount = (int) getChildCountMethod.invoke(recyclerView);
            for (int i = 0; i < childCount; i++) {
                View child = (View) getChildAtMethod.invoke(recyclerView, i);
                Object holder = getChildViewHolderMethod.invoke(recyclerView, child);

                int holderPosition = getViewHolderPosition(holder);
                if (holderPosition == position) {
                    return child;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取ViewHolder的位置，尝试多种方法
     */
    private int getViewHolderPosition(Object viewHolder) {
        try {
            // 尝试不同的获取position的方法
            if (getBindingAdapterPositionMethod != null) {
                return (int) getBindingAdapterPositionMethod.invoke(viewHolder);
            } else if (getAdapterPositionMethod != null) {
                return (int) getAdapterPositionMethod.invoke(viewHolder);
            } else if (getLayoutPositionMethod != null) {
                return (int) getLayoutPositionMethod.invoke(viewHolder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return NO_POSITION;
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        viewHolderCache.evictAll();
    }

    /**
     * 从缓存中移除特定位置的项
     */
    public void removeFromCache(int position) {
        viewHolderCache.remove(position);
    }

    /**
     * 释放资源
     */
    public void release() {
        clearCache();
        recyclerViewRef.clear();
    }

    /**
     * 通过传入View查找其位置，再向前查找兄弟节点
     *
     * @param view 起始视图，必须是RecyclerView的子视图
     * @param stopCondition 停止条件
     * @return 找到的视图列表（包括起始视图）
     */
    public List<View> findSiblingsBefore(View view, StopCondition stopCondition) {
        Object recyclerView = recyclerViewRef.get();

        if (recyclerView == null || view == null || findContainingViewHolderMethod == null) {
            return new ArrayList<>();
        }

        try {
            // 尝试获取视图的位置
            Object holder = findContainingViewHolderMethod.invoke(recyclerView, view);
            XposedBridge.log("holder: " + holder);
            if (holder == null) {
                return new ArrayList<>();
            }

            int position = getViewHolderPosition(holder);
            XposedBridge.log("position: " + position);
            if (position == NO_POSITION) {
                return new ArrayList<>();
            }

            return findSiblingsBefore(position, stopCondition);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}