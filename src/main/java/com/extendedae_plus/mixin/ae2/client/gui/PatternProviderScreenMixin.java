package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.IExPatternButtonsAccessor;
import com.extendedae_plus.api.IPatternProviderMenuAdvancedSync;
import com.extendedae_plus.api.smartDoubling.IPatternProviderMenuDoublingSync;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.provider.SetPerProviderScalingLimitC2SPacket;
import com.extendedae_plus.network.provider.ToggleAdvancedBlockingC2SPacket;
import com.extendedae_plus.network.provider.ToggleSmartDoublingC2SPacket;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * 为 AE2 原版样板供应器界面添加“高级阻挡模式”按钮。
 * - 位于左侧工具栏
 * - 点击仅发送 C2S 切换请求；状态由 AE2 @GuiSync 回传决定
 */
@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    @Unique private SettingToggleButton<YesNo> eap$AdvancedBlockingToggle;
    @Unique private SettingToggleButton<YesNo> eap$SmartDoublingToggle;
    @Unique private EditBox eap$PerProviderLimitInput;

    @Unique private boolean eap$AdvancedBlockingEnabled = false;
    @Unique private boolean eap$SmartDoublingEnabled = false;
    @Unique private int eap$PerProviderScalingLimit = 0;
    @Unique private boolean eap$PerProviderLimitWasFocused = false;

    public PatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void eap$initAdvancedBlocking(C menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        // 使用 @GuiSync 初始化
        try {
            if (menu instanceof IPatternProviderMenuAdvancedSync sync) {
                this.eap$AdvancedBlockingEnabled = sync.eap$getAdvancedBlockingSynced();
            }
        } catch (Throwable t) {
            EAP$LOGGER.error("Error initializing advanced sync", t);
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
            if (menu instanceof IPatternProviderMenuDoublingSync sync2) {
                this.eap$SmartDoublingEnabled = sync2.eap$getSmartDoublingSynced();
                this.eap$PerProviderScalingLimit = sync2.eap$getScalingLimit();
            }
        } catch (Throwable t) {
            EAP$LOGGER.error("Error initializing smart doubling sync", t);
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

        // 占位：每-provider 缩放上限输入框（仅作为 UI 占位，不发送网络请求）
        try {
            // 使用与左侧工具栏一致的布局托管，避免绝对坐标问题
            // 使用更短的输入框并去除前导 0（显示更紧凑）
            this.eap$PerProviderLimitInput = new EditBox(this.font, 0, 0, 28, 12, Component.literal("Limit"));
            this.eap$PerProviderLimitInput.setValue(String.valueOf(this.eap$PerProviderScalingLimit));
            this.eap$PerProviderLimitInput.setMaxLength(6);
            // 值变更响应
            this.eap$PerProviderLimitInput.setResponder((s) -> {
                try {
                    if (s != null && !s.isEmpty()) {
                        String trimmed = s.replaceFirst("^0+(?=.)", "");
                        if (!trimmed.equals(s)) {
                            this.eap$PerProviderLimitInput.setValue(trimmed);
                            s = trimmed;
                        }
                    }
                    // 实时发送：尝试解析并发送到服务端（若值变化）
                    int val = Integer.parseInt(s == null || s.isBlank() ? "0" : s);

                    if (val != this.eap$PerProviderScalingLimit) {
                        this.eap$PerProviderScalingLimit = val;
                        ModNetwork.CHANNEL.sendToServer(new SetPerProviderScalingLimitC2SPacket(val));
                    }
                } catch (Throwable ignored) {}
            });
            // 初次加入渲染列表（后续在 updateBeforeRender 每帧更新 tooltip/位置）
            this.addRenderableWidget(this.eap$PerProviderLimitInput);
            // 若菜单/逻辑已在之前同步了上限，则在控件创建后填充显示
            this.eap$PerProviderLimitInput.setValue(String.valueOf(this.eap$PerProviderScalingLimit));
        } catch (Throwable ignored) {}
    }

    // 每帧刷新：仅从菜单(@GuiSync)同步布尔值，保持按钮状态一致
    @Inject(method = "updateBeforeRender", at = @At("HEAD"), remap = false)
    private void eap$updateAdvancedBlocking(CallbackInfo ci) {
        if (this.eap$AdvancedBlockingToggle != null) {
            boolean desired = this.eap$AdvancedBlockingEnabled;
            if (this.menu instanceof IPatternProviderMenuAdvancedSync sync) {
                desired = sync.eap$getAdvancedBlockingSynced();
            }
            this.eap$AdvancedBlockingEnabled = desired;
            this.eap$AdvancedBlockingToggle.set(desired ? YesNo.YES : YesNo.NO);
        }

        if (this.eap$SmartDoublingToggle != null) {
            boolean desired2 = this.eap$SmartDoublingEnabled;
            if (this.menu instanceof IPatternProviderMenuDoublingSync sync2) {
                desired2 = sync2.eap$getSmartDoublingSynced();
            }
            this.eap$SmartDoublingEnabled = desired2;
            this.eap$SmartDoublingToggle.set(desired2 ? YesNo.YES : YesNo.NO);
        }

        if (this.eap$PerProviderLimitInput != null) {
            int remoteLimit = this.eap$PerProviderScalingLimit;
            if (this.menu instanceof IPatternProviderMenuDoublingSync sync3) {
                remoteLimit = sync3.eap$getScalingLimit();
            }
            // 若输入框没有焦点且远端值变化则更新控件；否则保留用户编辑的值
            boolean focused = this.eap$PerProviderLimitInput.isFocused();
            if (!focused && remoteLimit != this.eap$PerProviderScalingLimit) {
                this.eap$PerProviderScalingLimit = remoteLimit;
                this.eap$PerProviderLimitInput.setValue(String.valueOf(remoteLimit));

            }
        }

        if ((Object) this instanceof GuiExPatternProvider) {
            try {
                ((IExPatternButtonsAccessor) this).eap$updateButtonsLayout();
            } catch (Throwable t) {
                EAP$LOGGER.debug("[EAP] updateButtonsLayout skipped: {}", t.toString());
            }
        }
        // 保证 EditBox 在每帧存在且定位到左侧工具栏旁边（参考已有左侧按钮位置）
        try {
            if (this.eap$PerProviderLimitInput != null) {
                // 仅在智能倍增已启用时显示输入框
                if (this.eap$SmartDoublingEnabled) {
                    if (!this.renderables.contains(this.eap$PerProviderLimitInput)) {
                        this.addRenderableWidget(this.eap$PerProviderLimitInput);
                    }

                    // 当输入框未获得焦点且内容为空时填充 0
                    if (!this.eap$PerProviderLimitInput.isFocused() && this.eap$PerProviderLimitInput.getValue().trim().isEmpty()) {
                        this.eap$PerProviderLimitInput.setValue("0");
                    }

                    // 优先参考已有的左侧按钮定位
                    Button ref = eap$SmartDoublingToggle;
                    if (ref != null) {
                        int ex = ref.getX() - this.eap$PerProviderLimitInput.getWidth() - 5;
                        int ey = ref.getY() + 2; // 向下移动 2 像素
                        this.eap$PerProviderLimitInput.setX(ex);
                        this.eap$PerProviderLimitInput.setY(ey);
                    }

                    // 动态更新 tooltip，简短且包含当前值
                    String cur = this.eap$PerProviderLimitInput.getValue();
                    if (cur.isBlank()) cur = "0";
                    var tip = Component.literal("智能翻倍上限: " + cur);
                    this.eap$PerProviderLimitInput.setTooltip(Tooltip.create(tip));

                    // 当输入框失去焦点且之前处于聚焦时，提交当前值并更新本地同步字段
                    boolean focusedNow = this.eap$PerProviderLimitInput.isFocused();
                    if (this.eap$PerProviderLimitWasFocused && !focusedNow) {
                        int val = Integer.parseInt(cur);
                        // 更新本地展示值并发包到服务端
                        this.eap$PerProviderScalingLimit = val;
                        EAP$LOGGER.info("更新");
                        ModNetwork.CHANNEL.sendToServer(new SetPerProviderScalingLimitC2SPacket(val));
                    }
                    this.eap$PerProviderLimitWasFocused = focusedNow;

                } else {
                    // 隐藏输入框
                    if (this.renderables.contains(this.eap$PerProviderLimitInput)) {
                        this.removeWidget(this.eap$PerProviderLimitInput);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}

