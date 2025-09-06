package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.ExPatternButtonsAccessor;
import com.extendedae_plus.api.PatternProviderMenuAdvancedSync;
import com.extendedae_plus.api.PatternProviderMenuDoublingSync;
import com.extendedae_plus.network.ToggleAdvancedBlockingC2SPacket;
import com.extendedae_plus.network.ToggleSmartDoublingC2SPacket;
import com.extendedae_plus.util.ExtendedAELogger;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为 AE2 原版样板供应器界面添加“高级阻挡模式”按钮。
 * - 位于左侧工具栏
 * - 点击仅发送 C2S 切换请求；状态由 AE2 @GuiSync 回传决定
 */
@Mixin(value = PatternProviderScreen.class, remap = false)
public abstract class PatternProviderScreenMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    @Unique
    private SettingToggleButton<YesNo> eap$AdvancedBlockingToggle;

    @Unique
    private boolean eap$AdvancedBlockingEnabled = false;

    @Unique
    private SettingToggleButton<YesNo> eap$SmartDoublingToggle;

    @Unique
    private boolean eap$SmartDoublingEnabled = false;

    public PatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void eap$initAdvancedBlocking(C menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        // 使用 @GuiSync 初始化
        try {
            if (menu instanceof PatternProviderMenuAdvancedSync sync) {
                this.eap$AdvancedBlockingEnabled = sync.eap$getAdvancedBlockingSynced();
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("Error initializing advanced sync", t);
        }

        // 使用 SettingToggleButton<YesNo> 的外观（原版图标），但自定义悬停描述为“智能阻挡”
        this.eap$AdvancedBlockingToggle = new SettingToggleButton<>(
                Settings.BLOCKING_MODE,
                this.eap$AdvancedBlockingEnabled ? YesNo.YES : YesNo.NO,
                (btn, backwards) -> {
                    // 不做本地切换，点击仅发送自定义C2S，显示由@GuiSync回传
                    ExtendedAELogger.LOGGER.debug("[EAP] Click advanced blocking toggle: send C2S");
                    var conn = Minecraft.getInstance().getConnection();
                    if (conn != null) conn.send(ToggleAdvancedBlockingC2SPacket.INSTANCE);
                }
        ) {
            @Override
            public java.util.List<net.minecraft.network.chat.Component> getTooltipMessage() {
                boolean enabled = eap$AdvancedBlockingEnabled;
                var title = net.minecraft.network.chat.Component.literal("智能阻挡");
                var line = enabled
                        ? net.minecraft.network.chat.Component.literal("已启用：对于同一种配方将不再阻挡(需要开启原版的阻挡模式)")
                        : net.minecraft.network.chat.Component.literal("已禁用：这么好的功能为什么不打开呢");
                return java.util.List.of(title, line);
            }
        };
        // 初始化后立刻对齐当前@GuiSync状态，避免首帧显示不一致
        ExtendedAELogger.LOGGER.debug("[EAP] Screen init: initial synced={} -> set button", this.eap$AdvancedBlockingEnabled);
        this.eap$AdvancedBlockingToggle.set(this.eap$AdvancedBlockingEnabled ? YesNo.YES : YesNo.NO);

        this.addToLeftToolbar(this.eap$AdvancedBlockingToggle);

        // 智能翻倍按钮：与高级阻挡同款样式，点击仅发送C2S，状态由@GuiSync驱动
        try {
            if (menu instanceof PatternProviderMenuDoublingSync sync2) {
                this.eap$SmartDoublingEnabled = sync2.eap$getSmartDoublingSynced();
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("Error initializing smart doubling sync", t);
        }

        this.eap$SmartDoublingToggle = new SettingToggleButton<>(
                Settings.BLOCKING_MODE,
                this.eap$SmartDoublingEnabled ? YesNo.YES : YesNo.NO,
                (btn, backwards) -> {
                    ExtendedAELogger.LOGGER.debug("[EAP] Click smart doubling toggle: send C2S");
                    var conn = Minecraft.getInstance().getConnection();
                    if (conn != null) conn.send(ToggleSmartDoublingC2SPacket.INSTANCE);
                }
        ) {
            @Override
            public java.util.List<net.minecraft.network.chat.Component> getTooltipMessage() {
                boolean enabled = eap$SmartDoublingEnabled;
                var title = net.minecraft.network.chat.Component.literal("智能翻倍");
                var line = enabled
                        ? net.minecraft.network.chat.Component.literal("已启用：根据请求量对处理样板进行智能缩放")
                        : net.minecraft.network.chat.Component.literal("已禁用：按原始样板数量进行发配");
                return java.util.List.of(title, line);
            }
        };

        this.eap$SmartDoublingToggle.set(this.eap$SmartDoublingEnabled ? YesNo.YES : YesNo.NO);
        this.addToLeftToolbar(this.eap$SmartDoublingToggle);
    }

    // 每帧刷新：仅从菜单(@GuiSync)同步布尔值，保持按钮状态一致
    @Inject(method = "updateBeforeRender", at = @At("HEAD"), remap = false)
    private void eap$updateAdvancedBlocking(CallbackInfo ci) {
        if (this.eap$AdvancedBlockingToggle != null) {
            boolean desired = this.eap$AdvancedBlockingEnabled;
            if (this.menu instanceof PatternProviderMenuAdvancedSync sync) {
                desired = sync.eap$getAdvancedBlockingSynced();
            }
            ExtendedAELogger.LOGGER.debug("[EAP] updateBeforeRender tick (adv): desired={}", desired);
            this.eap$AdvancedBlockingEnabled = desired;
            this.eap$AdvancedBlockingToggle.set(desired ? YesNo.YES : YesNo.NO);
        }

        if (this.eap$SmartDoublingToggle != null) {
            boolean desired2 = this.eap$SmartDoublingEnabled;
            if (this.menu instanceof PatternProviderMenuDoublingSync sync2) {
                desired2 = sync2.eap$getSmartDoublingSynced();
            }
            ExtendedAELogger.LOGGER.debug("[EAP] updateBeforeRender tick (dbl): desired={}", desired2);
            this.eap$SmartDoublingEnabled = desired2;
            this.eap$SmartDoublingToggle.set(desired2 ? YesNo.YES : YesNo.NO);
        }

        if ((Object) this instanceof GuiExPatternProvider) {
            try {
                ((ExPatternButtonsAccessor) this).eap$updateButtonsLayout();
            } catch (Throwable t) {
                ExtendedAELogger.LOGGER.debug("[EAP] updateButtonsLayout skipped: {}", t.toString());
            }
        }
    }
}
