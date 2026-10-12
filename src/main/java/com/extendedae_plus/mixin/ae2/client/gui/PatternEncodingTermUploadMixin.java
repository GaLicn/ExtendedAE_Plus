package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.ActionButton;
import com.extendedae_plus.api.upload.IPatternEncodeRequestSync.EncodeRequest;
import com.extendedae_plus.api.upload.UploadPreferences;
import com.extendedae_plus.client.ProviderListCache;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.network.RequestProvidersListC2SPacket;
import com.extendedae_plus.network.upload.EncodeWithShiftFlagC2SPacket;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PatternEncodingTermScreen.class)
public class PatternEncodingTermUploadMixin {
    @ModifyVariable(method = "<init>", at = @At(value = "STORE"), name = "encodeBtn")
    private ActionButton eap$encodingButton(ActionButton button) {
        return new ActionButton(ActionItems.ENCODE, actionItems -> {
            boolean shiftDown = Screen.hasShiftDown();
            PacketDistributor.sendToServer(new EncodeWithShiftFlagC2SPacket(
                    new EncodeRequest(shiftDown, eap$findUniqueProvider(shiftDown), eap$clientUploadPreferences())));
            // 静默刷新缓存，供下一次连续编码使用。
            PacketDistributor.sendToServer(RequestProvidersListC2SPacket.SILENT);
            var screen = (PatternEncodingTermScreen<?>) (Object) this;
            screen.getMenu().encode();
        });
    }

    /**
     * 本地判定唯一匹配的供应器：名称与搜索词同处客户端、同一语言环境，不受专用服务器语言影响。
     *
     * <p>命中即消费搜索词：它只属于触发它的那次 JEI/EMI 转移，避免之后手动摆放的配方被传到同一供应器。</p>
     */
    @Unique
    private static Component eap$findUniqueProvider(boolean shiftDown) {
        if (shiftDown) {
            return null;
        }
        try {
            if (!ModConfigs.UNIQUE_PROVIDER_AUTO_UPLOAD_ENABLE.get()) {
                return null;
            }
            Component provider = ProviderListCache.findUniqueMatch(ExtendedAEPatternUploadUtil.peekLastProviderSearchKey());
            if (provider != null) {
                ExtendedAEPatternUploadUtil.consumeLastProviderSearchKey();
            }
            return provider;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 读取矩阵/ECO 自动上传的客户端偏好并随「编码」按钮一并上报。
     *
     * <p>这些配置项位于客户端配置，专用服务器不加载，故服务端无法直接读取，只能由客户端上报。</p>
     */
    @Unique
    private static UploadPreferences eap$clientUploadPreferences() {
        try {
            return new UploadPreferences(
                    ModConfigs.ECO_AUTO_UPLOAD_ENABLE.get(),
                    ModConfigs.PATTERN_AUTO_UPLOAD_PRIORITY.get());
        } catch (Throwable ignored) {
            return UploadPreferences.defaults();
        }
    }
}
