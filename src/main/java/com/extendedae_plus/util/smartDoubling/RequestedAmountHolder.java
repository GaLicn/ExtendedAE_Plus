package com.extendedae_plus.util.smartDoubling;

/**
 * 请求数量线程本地栈，用于支持嵌套请求（避免不同请求间的干扰）。
 * <p>
 * 每个线程都有自己独立的 LongStack，不会与其他线程共享。
 * 使用 long[] 而非 Long 对象，避免装箱开销与 GC 压力。
 */
public final class RequestedAmountHolder {
    // 每个线程独立持有一个 LongStack 实例
    private static final ThreadLocal<LongStack> HOLDER = ThreadLocal.withInitial(LongStack::new);

    // 栈深度上限（防止异常递归导致 OOM）
    private static final int MAX_DEPTH = 16777216;

    private RequestedAmountHolder() {
    }

    /**
     * 将一个请求数量压入当前线程的栈顶。
     *
     * @param v 要压入的请求值
     */
    public static void push(long v) {
        HOLDER.get().push(v);
    }

    /**
     * 从当前线程的栈顶弹出一个值。
     * 若栈为空，则安全忽略。
     */
    public static void pop() {
        HOLDER.get().pop();
    }

    /**
     * 获取当前线程栈顶的请求数量。
     * 若栈为空，则返回 0。
     *
     * @return 当前请求数量或 0
     */
    public static long get() {
        return HOLDER.get().peek();
    }

    /**
     * 清空当前线程的栈。
     * 若栈过大，会在清空时重置容量，防止占用过多内存。
     */
    public static void clear() {
        HOLDER.get().clear();
    }

    /**
     * 一个轻量级的 long 基础类型栈实现。
     * <p>
     * - 无装箱开销（比 Long 节省内存、避免 GC 压力）
     * - 每个线程独占实例（无需同步）
     * - 自动扩容机制（容量不够时按倍数增长）
     */
    private static final class LongStack {
        private long[] data = new long[16]; // 初始容量
        private int size = 0; // 当前栈深

        void push(long v) {
            if (size == data.length) {
                long[] n = new long[data.length * 2]; // 扩容为原容量的 2 倍
                System.arraycopy(data, 0, n, 0, data.length);
                data = n;
            }
            data[size++] = v;
        }

        void pop() {
            if (size > 0) size--;
        }

        long peek() {
            return size == 0 ? 0L : data[size - 1];
        }

        int depth() {
            return size;
        }

        void clear() {
            // 若数组容量过大，则在清空时重置为初始容量
            if (data.length > 1024) {
                data = new long[16];
            }
            size = 0;
        }
    }
}