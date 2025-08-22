package com.extendedae_plus.mixin.ae2;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.ExPatternButtonsAccessor;
import com.extendedae_plus.api.PatternProviderMenuAdvancedSync;
import com.extendedae_plus.network.ModNetwork;
import com.extendedae_plus.network.ToggleAdvancedBlockingC2SPacket;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

/**
 * 为 AE2 原版样板供应器界面添加“高级阻挡模式”按钮。
 * - 位于左侧工具栏
 * - 点击仅发送 C2S 切换请求；状态由 AE2 @GuiSync 回传决定
 */
@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    @Unique
    private SettingToggleButton<YesNo> eap$AdvancedBlockingToggle;

    @Unique
    private boolean eap$AdvancedBlockingEnabled = false;

    public PatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void eap$initAdvancedBlocking(C menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        // 使用 @GuiSync 初始化
        try {
            if (menu instanceof PatternProviderMenuAdvancedSync sync) {
                this.eap$AdvancedBlockingEnabled = sync.eap$getAdvancedBlockingSynced();
            }
        } catch (Throwable t) {
            LOGGER.error("Error initializing advanced sync", t);
        }

        // 使用 SettingToggleButton<YesNo> 的外观（原版图标），但自定义悬停描述为“智能阻挡”
        this.eap$AdvancedBlockingToggle = new SettingToggleButton<>(
                Settings.BLOCKING_MODE,
                this.eap$AdvancedBlockingEnabled ? YesNo.YES : YesNo.NO,
                (btn, backwards) -> {
                    // 不做本地切换，点击仅发送自定义C2S，显示由@GuiSync回传
                    LOGGER.debug("[EAP] Click advanced blocking toggle: send C2S");
                    ModNetwork.CHANNEL.sendToServer(new ToggleAdvancedBlockingC2SPacket());
                }
        ) {
            @Override
            public java.util.List<net.minecraft.network.chat.Component> getTooltipMessage() {
                boolean enabled = eap$AdvancedBlockingEnabled;
                var title = net.minecraft.network.chat.Component.literal("智能阻挡");
                var line = enabled
                        ? net.minecraft.network.chat.Component.literal("已启用：YES")
                        : net.minecraft.network.chat.Component.literal("已禁用：NO");
                return java.util.List.of(title, line);
            }
        };
        // 初始化后立刻对齐当前@GuiSync状态，避免首帧显示不一致
        LOGGER.debug("[EAP] Screen init: initial synced={} -> set button", this.eap$AdvancedBlockingEnabled);
        this.eap$AdvancedBlockingToggle.set(this.eap$AdvancedBlockingEnabled ? YesNo.YES : YesNo.NO);

        this.addToLeftToolbar(this.eap$AdvancedBlockingToggle);
    }

    // 每帧刷新：仅从菜单(@GuiSync)同步布尔值，保持按钮状态一致
    @Inject(method = "updateBeforeRender", at = @At("HEAD"), remap = false)
    private void eap$updateAdvancedBlocking(CallbackInfo ci) {
        if (this.eap$AdvancedBlockingToggle == null) return;

        boolean desired = this.eap$AdvancedBlockingEnabled;
        if (this.menu instanceof PatternProviderMenuAdvancedSync sync) {
            desired = sync.eap$getAdvancedBlockingSynced();
        }

        // 与AE2一致：每帧无条件对齐按钮状态至@GuiSync（使用YesNo以获得原版图标与提示）
        LOGGER.debug("[EAP] updateBeforeRender tick: desired={}", desired);
        if (this.eap$AdvancedBlockingEnabled != desired) {
            LOGGER.debug("[EAP] updateBeforeRender: desired changed {} -> {}", this.eap$AdvancedBlockingEnabled, desired);
        }
        this.eap$AdvancedBlockingEnabled = desired;
        this.eap$AdvancedBlockingToggle.set(desired ? YesNo.YES : YesNo.NO);

        // 如果当前屏幕是 ExtendedAE 的 GuiExPatternProvider，则委托布局更新到 accessor
        if ((Object) this instanceof GuiExPatternProvider) {
            try {
                ((ExPatternButtonsAccessor) this).eap$updateButtonsLayout();
            } catch (Throwable t) {
                LOGGER.debug("[EAP] updateButtonsLayout skipped: {}", t.toString());
            }
        }
    }
}
