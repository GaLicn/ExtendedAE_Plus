package com.extendedae_plus.util;

import com.extendedae_plus.ExtendedAEPlus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一日志工具类。
 * 在需要的类中可使用：
 * import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;
 */
public final class ExtendedAELogger {
    public static final Logger LOGGER = LoggerFactory.getLogger(ExtendedAEPlus.MODID);
    private ExtendedAELogger() {
        // no instance
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}