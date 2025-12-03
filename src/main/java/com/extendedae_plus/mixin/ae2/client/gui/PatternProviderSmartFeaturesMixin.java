package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.IExPatternButton;
import com.extendedae_plus.api.advancedBlocking.IPatternProviderMenuAdvancedSync;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.api.smartDoubling.IPatternProviderMenuDoublingSync;
import com.extendedae_plus.client.gui.widgets.EAPServerSettingToggleButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
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
public abstract class PatternProviderSmartFeaturesMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {
    @Unique private EAPServerSettingToggleButton<YesNo> eap$AdvancedBlockingToggle;
    @Unique private EAPServerSettingToggleButton<YesNo> eap$SmartDoublingToggle;

    public PatternProviderSmartFeaturesMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void eap$initAdvancedBlocking(C menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        this.eap$AdvancedBlockingToggle = new EAPServerSettingToggleButton<>(EAPSettings.ADVANCED_BLOCKING, YesNo.YES);
        this.addToLeftToolbar(this.eap$AdvancedBlockingToggle);
        this.eap$SmartDoublingToggle = new EAPServerSettingToggleButton<>(EAPSettings.SMART_DOUBLING, YesNo.YES);
        this.addToLeftToolbar(this.eap$SmartDoublingToggle);
    }

    // 每帧刷新：仅从菜单(@GuiSync)同步布尔值，保持按钮状态一致
    @Inject(method = "updateBeforeRender", at = @At("HEAD"), remap = false)
    private void eap$updateAdvancedBlocking(CallbackInfo ci) {
        if (this.menu instanceof IPatternProviderMenuDoublingSync sync) {
            this.eap$SmartDoublingToggle.set(sync.eap$getSmartDoublingSynced());
        }
        if (this.menu instanceof IPatternProviderMenuAdvancedSync sync) {
            this.eap$AdvancedBlockingToggle.set(sync.eap$getAdvancedBlockingSynced());
        }

        if ((Object) this instanceof GuiExPatternProvider) {
            try {
                ((IExPatternButton) this).eap$updateButtonsLayout();
            } catch (Throwable t) {
                // debug removed
            }
        }
    }
}
