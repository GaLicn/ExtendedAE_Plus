package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.config.Setting;
import appeng.api.util.IConfigManager;
import appeng.menu.me.common.MEStorageMenu;
import com.extendedae_plus.mixin.ae2.accessor.MEStorageMenuAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复：当服务端 ConfigManager 注册了额外设置（例如 TERMINAL_SHOW_PATTERN_PROVIDERS）
 * 而客户端 clientCM 未注册时，AE2 在同步环节会对 clientCM 执行 getSetting，
 * 进而抛出 UnsupportedSettingException。
 * <p>
 * 方案：在服务端首次 broadcastChanges 时，仅为“客户端缺失”的设置执行注册补齐，且占位值与服务端不同，
 * 以确保 AE2 后续仍会发送 ConfigValuePacket 完成真正的值同步，避免影响排序等行为。
 */
@Mixin(MEStorageMenu.class)
public abstract class MEStorageMenuMixin {

    @Unique
    private boolean eap$settingsMirrored = false;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$mirrorServerSettingsToClient(CallbackInfo ci) {
        var self = (MEStorageMenu) (Object) this;
        if (this.eap$settingsMirrored) {
            return;
        }
        try {
            var acc = (MEStorageMenuAccessor) self;
            IConfigManager server = acc.getServerCM();
            IConfigManager client = acc.getClientCM();
            if (server == null || client == null) {
                // server==null 通常意味着客户端侧或无服务端配置，直接返回
                return;
            }
            for (Setting<?> setting : server.getSettings()) {
                boolean clientHasSetting = true;
                try {
                    // 若未注册，这里会抛出异常
                    client.getSetting(setting);
                } catch (Throwable unsupported) {
                    clientHasSetting = false;
                }

                if (!clientHasSetting) {
                    try {
                        Object serverValue = server.getSetting(setting);
                        Object placeholder = eap$chooseDifferentEnumValue(serverValue);
                        if (placeholder == null) {
                            // 若无法选择不同的占位值（例如只有一个枚举常量），则退回服务端值
                            placeholder = serverValue;
                        }
                        // 使用辅助方法，统一进行受检的泛型转换后再注册
                        eap$registerSettingCompact(client, setting, placeholder);
                    } catch (Throwable ignore) {
                        // 防御：不让异常影响主流程
                    }
                }
            }
            this.eap$settingsMirrored = true;
        } catch (Throwable t) {
            // 防御：绝不让同步失败导致崩溃
        }
    }

    @Unique
    private Object eap$chooseDifferentEnumValue(Object serverValue) {
        if (!(serverValue instanceof Enum<?> sv)) {
            return null;
        }
        Class<? extends Enum<?>> enumClass = sv.getDeclaringClass();
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object c : constants) {
            if (c != sv) {
                return c;
            }
        }
        return null;
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Enum<T>> void eap$registerSettingCompact(
            IConfigManager client, Setting<?> setting, Object value) {
        // 前置校验：仅处理枚举类型的设置值
        if (!(value instanceof Enum<?>)) {
            // 非枚举则忽略（AE2 设置值通常为枚举）
            return;
        }
        Setting<T> typedSetting = (Setting<T>) setting;
        T typedValue = (T) value;
        // 仅当具体实现为 ConfigManager 时，才能调用 registerSetting
        if (client instanceof appeng.util.ConfigManager cm) {
            cm.registerSetting(typedSetting, typedValue);
        }
    }
}
