package com.extendedae_plus.client.gui;

public final class PageLayoutContext {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> CURRENT_PAGE = ThreadLocal.withInitial(() -> 0);

    private PageLayoutContext() {}

    public static void enable(int page) {
        ACTIVE.set(true);
        CURRENT_PAGE.set(page);
    }

    public static void disable() {
        ACTIVE.set(false);
    }

    public static boolean isActive() {
        Boolean b = ACTIVE.get();
        return b != null && b;
    }

    public static int getCurrentPage() {
        Integer i = CURRENT_PAGE.get();
        return i != null ? i : 0;
    }

    public static void withPage(int page, Runnable action) {
        enable(page);
        try {
            action.run();
        } finally {
            disable();
        }
    }
}
