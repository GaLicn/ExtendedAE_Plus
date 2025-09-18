package com.extendedae_plus.mixin.advancedae.client.gui;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.SettingToggleButton;
import com.extendedae_plus.api.ExPatternButtonsAccessor;
import com.extendedae_plus.api.PatternProviderMenuAdvancedSync;
import com.extendedae_plus.api.PatternProviderMenuDoublingSync;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.ToggleAdvancedBlockingC2SPacket;
import com.extendedae_plus.network.ToggleSmartDoublingC2SPacket;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.pedroksl.advanced_ae.client.gui.AdvPatternProviderScreen;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;
import org.checkerframework.checker.units.qual.C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

/**
 * 为高级ae样板供应器界面添加“高级阻挡模式”按钮。
 * - 位于左侧工具栏
 * - 点击仅发送 C2S 切换请求；状态由 AE2 @GuiSync 回传决定
 */
@Mixin(AdvPatternProviderScreen.class)
public abstract class AdvPatternProviderScreenMixin extends AEBaseScreen<AdvPatternProviderMenu> {

    @Unique
    private SettingToggleButton<YesNo> eap$AdvancedBlockingToggle;

    @Unique
    private boolean eap$AdvancedBlockingEnabled = false;

    @Unique
    private SettingToggleButton<YesNo> eap$SmartDoublingToggle;

    @Unique
    private boolean eap$SmartDoublingEnabled = false;

    public AdvPatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super((AdvPatternProviderMenu) menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void eap$initAdvancedBlocking(AdvPatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
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
                    ModNetwork.CHANNEL.sendToServer(new ToggleAdvancedBlockingC2SPacket());
                }
        ) {
            @Override
            public java.util.List<Component> getTooltipMessage() {
                boolean enabled = eap$AdvancedBlockingEnabled;
                var title = Component.literal("智能阻挡");
                var line = enabled
                        ? Component.literal("已启用：对于同一种配方将不再阻挡(需要开启原版的阻挡模式)")
                        : Component.literal("已禁用：这么好的功能为什么不打开呢");
                return java.util.List.of(title, line);
            }
        };
        // 初始化后立刻对齐当前@GuiSync状态，避免首帧显示不一致
        this.eap$AdvancedBlockingToggle.set(this.eap$AdvancedBlockingEnabled ? YesNo.YES : YesNo.NO);

        this.addToLeftToolbar(this.eap$AdvancedBlockingToggle);

        // 智能翻倍按钮：与高级阻挡同款样式，点击仅发送C2S，状态由@GuiSync驱动
        try {
            if (menu instanceof PatternProviderMenuDoublingSync sync2) {
                this.eap$SmartDoublingEnabled = sync2.eap$getSmartDoublingSynced();
            }
        } catch (Throwable t) {
            LOGGER.error("Error initializing smart doubling sync", t);
        }

        this.eap$SmartDoublingToggle = new SettingToggleButton<>(
                Settings.BLOCKING_MODE,
                this.eap$SmartDoublingEnabled ? YesNo.YES : YesNo.NO,
                (btn, backwards) -> {
                    ModNetwork.CHANNEL.sendToServer(new ToggleSmartDoublingC2SPacket());
                }
        ) {
            @Override
            public java.util.List<Component> getTooltipMessage() {
                boolean enabled = eap$SmartDoublingEnabled;
                var title = Component.literal("智能翻倍");
                var line = enabled
                        ? Component.literal("已启用：根据请求量对处理样板进行智能缩放")
                        : Component.literal("已禁用：按原始样板数量进行发配");
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
            this.eap$AdvancedBlockingEnabled = desired;
            this.eap$AdvancedBlockingToggle.set(desired ? YesNo.YES : YesNo.NO);
        }

        if (this.eap$SmartDoublingToggle != null) {
            boolean desired2 = this.eap$SmartDoublingEnabled;
            if (this.menu instanceof PatternProviderMenuDoublingSync sync2) {
                desired2 = sync2.eap$getSmartDoublingSynced();
            }
            this.eap$SmartDoublingEnabled = desired2;
            this.eap$SmartDoublingToggle.set(desired2 ? YesNo.YES : YesNo.NO);
        }

        if ((Object) this instanceof GuiExPatternProvider) {
            try {
                ((ExPatternButtonsAccessor) this).eap$updateButtonsLayout();
            } catch (Throwable t) {
                LOGGER.debug("[EAP] updateButtonsLayout skipped: {}", t.toString());
            }
        }
    }
}
