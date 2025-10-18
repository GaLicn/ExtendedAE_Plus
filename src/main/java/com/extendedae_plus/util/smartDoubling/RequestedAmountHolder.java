package com.extendedae_plus.util.smartDoubling;

import java.util.ArrayDeque;
import java.util.Deque;

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
    }

    /**
     * Pop the top value from the thread-local stack. Safe if empty.
     */
    public static void pop() {
        Deque<Long> dq = HOLDER.get();
        if (dq.isEmpty()) {
            return;
        }
        dq.pop();
    }

    /**
     * Peek the current requested amount or return 0 if none.
     */
    public static long get() {
        Deque<Long> dq = HOLDER.get();
        Long v = dq.peek();
        return v == null ? 0L : v;
    }
}


