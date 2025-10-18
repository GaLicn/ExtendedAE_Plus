package com.extendedae_plus.util;

import com.extendedae_plus.ExtendedAEPlus;
import org.slf4j.LoggerFactory;

/**
 * 统一日志工具类。
 * 在需要的类中可使用：
 * import static com.extendedae_plus.util.Logger.LOGGER;
 */
public final class Logger {
    public static final org.slf4j.Logger EAP$LOGGER = LoggerFactory.getLogger(ExtendedAEPlus.MODID);
    private Logger() {
        // no instance
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}