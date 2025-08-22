package com.extendedae_plus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global logger utility for ExtendedAE Plus mod
 */
public class ExtendedAELogger {
    public static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAEPlus");

    private ExtendedAELogger() {throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");}
}