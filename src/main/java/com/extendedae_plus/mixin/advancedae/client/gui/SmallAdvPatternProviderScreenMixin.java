package com.extendedae_plus.mixin.advancedae.client.gui;

import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.SettingToggleButton;
import com.extendedae_plus.api.IPatternProviderMenuAdvancedSync;
import com.extendedae_plus.api.smartDoubling.IPatternProviderMenuDoublingSync;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.provider.SetPerProviderScalingLimitC2SPacket;
import com.extendedae_plus.network.provider.ToggleAdvancedBlockingC2SPacket;
import com.extendedae_plus.network.provider.ToggleSmartDoublingC2SPacket;
import com.extendedae_plus.util.GuiUtil;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.pedroksl.advanced_ae.client.gui.SmallAdvPatternProviderScreen;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;
import net.pedroksl.advanced_ae.gui.advpatternprovider.SmallAdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import static com.extendedae_plus.util.GuiUtil.createToggle;
import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * 为高级ae样板供应器界面添加“高级阻挡模式”按钮。
 * - 位于左侧工具栏
 * - 点击仅发送 C2S 切换请求；状态由 AE2 @GuiSync 回传决定
 */
@Mixin(SmallAdvPatternProviderScreen.class)
public abstract class SmallAdvPatternProviderScreenMixin extends AEBaseScreen<SmallAdvPatternProviderMenu> {
    // 高级阻挡模式切换按钮
    @Unique private SettingToggleButton<YesNo> eap$AdvancedBlockingToggle;
    // 智能翻倍切换按钮
    @Unique private SettingToggleButton<YesNo> eap$SmartDoublingToggle;
    // 智能翻倍上限输入框
    @Unique private EditBox eap$PerProviderLimitInput;

    // 当前高级阻挡模式是否启用
    @Unique private boolean eap$AdvancedBlockingEnabled = false;
    // 当前智能翻倍是否启用
    @Unique private boolean eap$SmartDoublingEnabled = false;
    // 当前智能翻倍上限
    @Unique private int eap$PerProviderScalingLimit = 0;

    public SmallAdvPatternProviderScreenMixin(SmallAdvPatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /** 同步服务端状态（初始化时调用） */
    @Unique
    private void eap$syncInitialState(AdvPatternProviderMenu menu) {
        try {
            // 同步高级阻挡和智能翻倍的服务端状态
            if (menu instanceof IPatternProviderMenuAdvancedSync sync) {
                this.eap$AdvancedBlockingEnabled = sync.eap$getAdvancedBlockingSynced();
            }
            if (menu instanceof IPatternProviderMenuDoublingSync sync) {
                this.eap$SmartDoublingEnabled = sync.eap$getSmartDoublingSynced();
                this.eap$PerProviderScalingLimit = sync.eap$getScalingLimit();
            }
        } catch (Throwable t) {
            EAP$LOGGER.error("Error initializing sync", t);
        }
    }

    /** 创建并添加按钮和输入框 */
    @Unique
    private void eap$createWidgets() {
        // 高级阻挡
        this.eap$AdvancedBlockingToggle = createToggle(
                eap$AdvancedBlockingEnabled,
                () -> ModNetwork.CHANNEL.sendToServer(new ToggleAdvancedBlockingC2SPacket()),
                () -> {
                    var t = Component.literal("智能阻挡");
                    var line = eap$AdvancedBlockingEnabled
                            ? Component.literal("已启用：对于同一种配方将不再阻挡 (需要启用原版阻挡模式)")
                            : Component.literal("已禁用：建议开启以获得更智能的阻挡行为");
                    return List.of(t, line);
                }
        );
        this.eap$AdvancedBlockingToggle.set(eap$AdvancedBlockingEnabled ? YesNo.YES : YesNo.NO);
        this.addToLeftToolbar(this.eap$AdvancedBlockingToggle);

        // 智能翻倍
        this.eap$SmartDoublingToggle = createToggle(
                eap$SmartDoublingEnabled,
                () -> ModNetwork.CHANNEL.sendToServer(new ToggleSmartDoublingC2SPacket()),
                () -> {
                    var t = Component.literal("智能翻倍");
                    var line = eap$SmartDoublingEnabled
                            ? Component.literal("已启用：根据请求量对处理样板进行智能缩放")
                            : Component.literal("已禁用：按原始样板数量进行发配");
                    return List.of(t, line);
                }
        );
        this.eap$SmartDoublingToggle.set(eap$SmartDoublingEnabled ? YesNo.YES : YesNo.NO);
        this.addToLeftToolbar(this.eap$SmartDoublingToggle);

        // 缩放上限输入框（使用 GuiUtil 抽离）
        this.eap$PerProviderLimitInput = GuiUtil.createPerProviderLimitInput(this.font, this.eap$PerProviderScalingLimit, limit -> {
            this.eap$PerProviderScalingLimit = limit;
            ModNetwork.CHANNEL.sendToServer(new SetPerProviderScalingLimitC2SPacket(limit));
        });
        this.addRenderableWidget(this.eap$PerProviderLimitInput);
    }

    /* ---------------------------- 注入点 ---------------------------- */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void eap$onInit(SmallAdvPatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        // 初始化时同步服务端状态并创建控件
        eap$syncInitialState(menu);
        eap$createWidgets();
    }

    /** 每帧刷新：从服务端同步状态并更新 UI */
    @Inject(method = "updateBeforeRender", at = @At("HEAD"), remap = false)
    private void eap$updateBeforeRender(CallbackInfo ci) {
        // 每帧刷新按钮和输入框状态
        eap$updateToggles();
        eap$updateLimitInput();
    }

    /* ---------------------------- 刷新逻辑 ---------------------------- */
    /**
     * 刷新切换按钮的状态（与服务端同步）
     */
    @Unique
    private void eap$updateToggles() {
        if (this.eap$AdvancedBlockingToggle != null && this.menu instanceof IPatternProviderMenuAdvancedSync sync) {
            this.eap$AdvancedBlockingEnabled = sync.eap$getAdvancedBlockingSynced();
            this.eap$AdvancedBlockingToggle.set(eap$AdvancedBlockingEnabled ? YesNo.YES : YesNo.NO);
        }
        if (this.eap$SmartDoublingToggle != null && this.menu instanceof IPatternProviderMenuDoublingSync sync) {
            this.eap$SmartDoublingEnabled = sync.eap$getSmartDoublingSynced();
            this.eap$SmartDoublingToggle.set(eap$SmartDoublingEnabled ? YesNo.YES : YesNo.NO);
        }
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

        if (this.eap$SmartDoublingEnabled) {
            // 智能翻倍启用时，确保输入框可见
            if (!this.renderables.contains(this.eap$PerProviderLimitInput)) {
                this.addRenderableWidget(this.eap$PerProviderLimitInput);
            }
            // 未聚焦且内容为空时，显示0
            if (!focused && this.eap$PerProviderLimitInput.getValue().trim().isEmpty()) {
                this.eap$PerProviderLimitInput.setValue("0");
            }

            // 定位输入框到智能翻倍按钮左侧
            Button ref = eap$SmartDoublingToggle;
            if (ref != null) {
                int ex = ref.getX() - this.eap$PerProviderLimitInput.getWidth() - 5;
                int ey = ref.getY() + 2;
                this.eap$PerProviderLimitInput.setX(ex);
                this.eap$PerProviderLimitInput.setY(ey);
            }

            // 设置 tooltip
            String cur = this.eap$PerProviderLimitInput.getValue();
            if (cur.isBlank()) cur = "0";
            this.eap$PerProviderLimitInput.setTooltip(Tooltip.create(Component.literal("单样物品发配数量上限: " + cur)));
        } else {
            // 智能翻倍未启用时，移除输入框
            this.removeWidget(this.eap$PerProviderLimitInput);
        }
    }
}
