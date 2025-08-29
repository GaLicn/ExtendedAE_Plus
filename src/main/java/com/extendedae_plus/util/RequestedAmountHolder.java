package com.extendedae_plus.util;

import java.util.ArrayDeque;
import java.util.Deque;
import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

/**
 * Thread-local stack holder for requested amounts to support nested requests.
 */
public final class RequestedAmountHolder {
    private static final ThreadLocal<Deque<Long>> HOLDER = ThreadLocal.withInitial(ArrayDeque::new);

    private RequestedAmountHolder() {
    }

    /**
     * Push a requested amount onto the thread-local stack.
     */
    public static void push(long v) {
        Deque<Long> dq = HOLDER.get();
        dq.push(v);
        LOGGER.info("[extendedae_plus] 请求数量已推入堆栈: {} ; depth={}", v, dq.size());
    }

    /**
     * Pop the top value from the thread-local stack. Safe if empty.
     */
    public static void pop() {
        Deque<Long> dq = HOLDER.get();
        if (dq.isEmpty()) {
            LOGGER.info("[extendedae_plus] 请求数量堆栈为空，无法弹出");
            return;
        }
        Long popped = dq.pop();
        LOGGER.info("[extendedae_plus] 请求数量已弹出堆栈: {} ; depth={}", popped, dq.size());
    }

    /**
     * Peek the current requested amount or return 0 if none.
     */
    public static long get() {
        Deque<Long> dq = HOLDER.get();
        Long v = dq.peek();
        LOGGER.info("[extendedae_plus] 当前请求数量: {} ; depth={}", v, dq.size());
        return v == null ? 0L : v;
    }

    /**
     * Clear the entire stack for this thread.
     */
    public static void clearAll() {
        HOLDER.get().clear();
        LOGGER.info("[extendedae_plus] 请求数量堆栈已清空");
    }

    /**
     * 返回当前线程堆栈深度（仅供日志/诊断使用）。
     */
    public static int depth() {
        return HOLDER.get().size();
    }

    /**
     * 返回当前线程堆栈的字符串表示（仅供日志/诊断使用）。
     */
    public static String snapshot() {
        return HOLDER.get().toString();
    }
}


