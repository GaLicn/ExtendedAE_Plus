package com.extendedae_plus.mixin.advancedae.client.gui;

import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import com.extendedae_plus.api.IInputBackgroundRenderer;
import com.extendedae_plus.api.advancedBlocking.IPatternProviderMenuAdvancedSync;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.api.smartDoubling.IPatternProviderMenuDoublingSync;
import com.extendedae_plus.client.gui.widgets.EAPServerSettingToggleButton;
import com.extendedae_plus.network.SetPerProviderScalingLimitC2SPacket;
import com.extendedae_plus.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.pedroksl.advanced_ae.client.gui.AdvPatternProviderScreen;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

/**
 * 为高级ae样板供应器界面添加"高级阻挡模式"按钮。
 * - 位于左侧工具栏
 * - 点击仅发送 C2S 切换请求；状态由 AE2 @GuiSync 回传决定
 */
@Mixin(AdvPatternProviderScreen.class)
public abstract class AdvPatternProviderSmartFeaturesMixin extends AEBaseScreen<AdvPatternProviderMenu> implements IInputBackgroundRenderer {
    @Unique private EAPServerSettingToggleButton<YesNo> eap$AdvancedBlockingToggle;
    @Unique private EAPServerSettingToggleButton<YesNo> eap$SmartDoublingToggle;
    @Unique private AETextField eap$PerProviderLimitInput;
    @Unique private int eap$PerProviderScalingLimit = 0;
    @Unique private int eap$inputBgX;
    @Unique private int eap$inputBgY;
    @Unique private int eap$inputBgW;
    @Unique private int eap$inputBgH;

    public AdvPatternProviderSmartFeaturesMixin(AdvPatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void eap$initAdvancedBlocking(AdvPatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        this.eap$AdvancedBlockingToggle = new EAPServerSettingToggleButton<>(EAPSettings.ADVANCED_BLOCKING, YesNo.YES);
        this.addToLeftToolbar(this.eap$AdvancedBlockingToggle);
        this.eap$SmartDoublingToggle = new EAPServerSettingToggleButton<>(EAPSettings.SMART_DOUBLING, YesNo.YES);
        this.addToLeftToolbar(this.eap$SmartDoublingToggle);

        if (menu instanceof IPatternProviderMenuDoublingSync sync) {
            this.eap$PerProviderScalingLimit = sync.eap$getScalingLimit();
        }

        this.eap$PerProviderLimitInput = GuiUtil.createPerProviderLimitInput(this.style, this.font, this.eap$PerProviderScalingLimit, limit -> {
            this.eap$PerProviderScalingLimit = limit;
            PacketDistributor.sendToServer(new SetPerProviderScalingLimitC2SPacket(limit));
        });
        this.addRenderableWidget(this.eap$PerProviderLimitInput);
    }

    /**
     * 刷新输入框的内容和可见性
     */
    @Unique
    private void eap$updateLimitInput() {
        if (this.eap$PerProviderLimitInput == null) return;

        int remoteLimit = this.eap$PerProviderScalingLimit;
        // 获取服务端最新的 scaling limit
        if (this.menu instanceof IPatternProviderMenuDoublingSync sync) {
            remoteLimit = sync.eap$getScalingLimit();
        }

        boolean focused = this.eap$PerProviderLimitInput.isFocused();
        // 如果未聚焦且服务端有变化，则同步显示
        if (!focused && remoteLimit != this.eap$PerProviderScalingLimit) {
            this.eap$PerProviderScalingLimit = remoteLimit;
            this.eap$PerProviderLimitInput.setValue(String.valueOf(remoteLimit));
        }

        if (this.eap$SmartDoublingToggle.getCurrentValue() == YesNo.YES) {
            // 智能翻倍启用时，确保输入框可见
            if (!this.renderables.contains(this.eap$PerProviderLimitInput)) {
                this.addRenderableWidget(this.eap$PerProviderLimitInput);
            }
            // 未聚焦且内容为空时，显示0
            if (!focused && this.eap$PerProviderLimitInput.getValue().trim().isEmpty()) {
                this.eap$PerProviderLimitInput.setValue("0");
            }

            Button ref = eap$SmartDoublingToggle;
            if (ref != null) {
                int visualWidth = this.eap$PerProviderLimitInput.getWidth() + 4 + this.font.width("_");
                int visualHeight = 16;
                int padding = 2;
                int ex = ref.getX() - visualWidth - 5 - padding ;
                int ey = ref.getY() + (ref.getHeight() - visualHeight) / 2 - padding + 6;
                this.eap$PerProviderLimitInput.setX(ex + padding);
                this.eap$PerProviderLimitInput.setY(ey + padding);
                this.eap$inputBgX = ex;
                this.eap$inputBgY = ey;
                this.eap$inputBgW = visualWidth + padding * 2;
                this.eap$inputBgH = visualHeight + padding * 2;
            }

            String cur = this.eap$PerProviderLimitInput.getValue();
            if (cur.isBlank()) cur = "0";
            this.eap$PerProviderLimitInput.setTooltipMessage(Collections.singletonList(Component.translatable("gui.extendedae_plus.per_provider_limit.tooltip", cur)));
        } else {
            // 智能翻倍未启用时，移除输入框
            this.removeWidget(this.eap$PerProviderLimitInput);
        }
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

        eap$updateLimitInput();
    }

    @Override
    public void eap$renderInputBackground(GuiGraphics guiGraphics) {
        if (this.eap$SmartDoublingToggle != null && this.eap$SmartDoublingToggle.getCurrentValue() == YesNo.YES
                && this.eap$PerProviderLimitInput != null && this.eap$PerProviderLimitInput.isVisible()) {
            Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter()
                                          .dest(this.eap$inputBgX - 5, this.eap$inputBgY-3, this.eap$inputBgW + 6, this.eap$inputBgH- 2)
                                          .blit(guiGraphics);
        }
    }
}
