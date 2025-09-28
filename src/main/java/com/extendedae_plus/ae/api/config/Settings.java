package com.extendedae_plus.ae.api.config;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import com.google.common.base.Preconditions;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public final class Settings {
    private static final Map<String, Setting<?>> SETTINGS = new HashMap<>();
    public static final Setting<YesNo> ACCELERATE = register("accelerate", YesNo.NO, YesNo.YES);


    private Settings() {
    }

    private synchronized static <T extends Enum<T>> Setting<T> register(String name, Class<T> enumClass) {
        Preconditions.checkState(!SETTINGS.containsKey(name));
        var setting = new Setting<>(name, enumClass);
        SETTINGS.put(name, setting);
        return setting;
    }

    @SafeVarargs
    private synchronized static <T extends Enum<T>> Setting<T> register(String name, T firstOption, T... moreOptions) {
        Preconditions.checkState(!SETTINGS.containsKey(name));
        var setting = new Setting<T>(name, firstOption.getDeclaringClass(), EnumSet.of(firstOption, moreOptions));
        SETTINGS.put(name, setting);
        return setting;
    }

    public static Setting<?> getOrThrow(String name) {
        var setting = SETTINGS.get(name);
        if (setting == null) {
            throw new IllegalArgumentException("Unknown setting '" + name + "'");
        }
        return setting;
    }
}
