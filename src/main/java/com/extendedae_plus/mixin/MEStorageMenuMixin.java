package com.extendedae_plus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.util.IConfigManager;
import appeng.menu.me.common.MEStorageMenu;
import com.extendedae_plus.mixin.accessor.MEStorageMenuAccessor;
import appeng.api.config.Setting;

/**
 * 修复：当服务端 ConfigManager 注册了额外设置（例如 TERMINAL_SHOW_PATTERN_PROVIDERS）
 * 而客户端 clientCM 未注册时，AE2 在同步环节会对 clientCM 执行 getSetting，
 * 进而抛出 UnsupportedSettingException。
 *
 * 方案：在服务端首次 broadcastChanges 时，将 serverCM 中的所有设置镜像注册到 clientCM，
 * 以确保后续同步安全。
 */
@Mixin(MEStorageMenu.class)
public abstract class MEStorageMenuMixin {

    @Unique
    private boolean extendedae_plus$settingsMirrored = false;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void extendedae_plus$mirrorServerSettingsToClient(CallbackInfo ci) {
        var self = (MEStorageMenu) (Object) this;
        if (this.extendedae_plus$settingsMirrored) {
            return;
        }
        try {
            var acc = (MEStorageMenuAccessor) (Object) self;
            IConfigManager server = acc.getServerCM();
            IConfigManager client = acc.getClientCM();
            if (server == null || client == null) {
                // server==null 通常意味着客户端侧或无服务端配置，直接返回
                return;
            }
            for (Setting<?> setting : server.getSettings()) {
                try {
                    // 使用 AE2 提供的通用复制接口，内部会处理未注册场景
                    setting.copy(server, client);
                } catch (Throwable ignore) { }
            }
            this.extendedae_plus$settingsMirrored = true;
        } catch (Throwable t) {
            // 防御：绝不让同步失败导致崩溃
        }
    }
}
