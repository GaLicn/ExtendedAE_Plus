package com.extendedae_plus.mixin.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.PatternProviderMenuAdvancedSync;
import com.extendedae_plus.client.ClientAdvancedBlockingState;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAdvancedAccessor;
import com.extendedae_plus.network.ModNetwork;
import com.extendedae_plus.network.ToggleAdvancedBlockingC2SPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;
/**
 * 为 AE2 原版样板供应器界面添加“高级阻挡模式”按钮（仅客户端UI反馈）。
 * - 位于左侧工具栏
 * - 点击后切换图标（YES/NO）并切换 tooltip 提示
 * - 当前不做任何网络/服务端逻辑
 */
@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    @Unique
    private ToggleButton eap$AdvancedBlockingToggle;

    @Unique
    private boolean eap$AdvancedBlockingEnabled = false;

    @Unique
    private String eap$ProviderKey = null;

    public PatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void eap$initAdvancedBlocking(C menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        // 计算供应器唯一键：维度ID + 方块坐标
        try {
            var logic = ((PatternProviderMenuAdvancedAccessor) menu).eap$logic();
            var host = ((PatternProviderLogicAccessor) logic).eap$host();
            var be = host.getBlockEntity();
            var level = be.getLevel();
            String dimId = level.dimension().location().toString();
            long posLong = be.getBlockPos().asLong();
            this.eap$ProviderKey = ClientAdvancedBlockingState.key(dimId, posLong);
        } catch (Throwable t) {
            LOGGER.error("Error initializing advanced sync", t);
        }

        // 优先使用该供应器最近一次 S2C 状态；否则回退读取 @GuiSync 初始化
        if (this.eap$ProviderKey != null && ClientAdvancedBlockingState.has(this.eap$ProviderKey)) {
            this.eap$AdvancedBlockingEnabled = ClientAdvancedBlockingState.get(this.eap$ProviderKey);
        } else if (menu instanceof PatternProviderMenuAdvancedSync sync) {
            this.eap$AdvancedBlockingEnabled = sync.eap$getAdvancedBlockingSynced();
        }
        // 使用 ToggleButton 以便在 YES/NO 图标与提示之间动态切换
        this.eap$AdvancedBlockingToggle = new ToggleButton(
                Icon.BLOCKING_MODE_YES,
                Icon.BLOCKING_MODE_NO,
                // 提示文本：名称与说明
                Component.literal("高级阻挡模式"),
                Component.literal("高级阻挡模式：当开启时，执行更严格的阻挡判定"),
                (state) -> {
                    // 客户端立即反馈：切换图标/提示
                    this.eap$AdvancedBlockingEnabled = state;
                    this.eap$AdvancedBlockingToggle.setState(state);
                    // 发送 C2S 切换请求
                    ModNetwork.CHANNEL.sendToServer(new ToggleAdvancedBlockingC2SPacket());
                    // 可根据状态调整提示文本（演示性：开启/关闭不同第二行）
                    if (state) {
                        this.eap$AdvancedBlockingToggle.setTooltipOn(java.util.List.of(
                                Component.literal("高级阻挡模式"),
                                Component.literal("高级阻挡模式：已开启")));
                        this.eap$AdvancedBlockingToggle.setTooltipOff(java.util.List.of(
                                Component.literal("高级阻挡模式"),
                                Component.literal("高级阻挡模式：已开启")));
                    } else {
                        this.eap$AdvancedBlockingToggle.setTooltipOn(java.util.List.of(
                                Component.literal("高级阻挡模式"),
                                Component.literal("高级阻挡模式：已关闭")));
                        this.eap$AdvancedBlockingToggle.setTooltipOff(java.util.List.of(
                                Component.literal("高级阻挡模式"),
                                Component.literal("高级阻挡模式：已关闭")));
                    }
                }
        );
        this.eap$AdvancedBlockingToggle.setState(this.eap$AdvancedBlockingEnabled);
        // 初始 tooltip
        this.eap$AdvancedBlockingToggle.setTooltipOn(java.util.List.of(
                Component.literal("高级阻挡模式"),
                Component.literal(this.eap$AdvancedBlockingEnabled ? "高级阻挡模式：已开启" : "高级阻挡模式：已关闭")
        ));
        this.eap$AdvancedBlockingToggle.setTooltipOff(java.util.List.of(
                Component.literal("高级阻挡模式"),
                Component.literal(this.eap$AdvancedBlockingEnabled ? "高级阻挡模式：已开启" : "高级阻挡模式：已关闭")
        ));

        this.addToLeftToolbar(this.eap$AdvancedBlockingToggle);
    }

    // 每帧刷新：从菜单同步布尔值，保持按钮状态一致
    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void eap$updateAdvancedBlocking(CallbackInfo ci) {
        // 打印一条轻量 tick 日志以确认该方法被调用（频繁输出可在验证后移除）
        // System.out.println("[EPP][CLIENT] updateBeforeRender tick, local=" + this.eppAdvancedBlockingEnabled);

        if (this.eap$AdvancedBlockingToggle == null) return;

        boolean desired = this.eap$AdvancedBlockingEnabled;
        // 优先使用该供应器最近一次 S2C 值
        if (this.eap$ProviderKey != null && ClientAdvancedBlockingState.has(this.eap$ProviderKey)) {
            desired = ClientAdvancedBlockingState.get(this.eap$ProviderKey);
        } else if (this.menu instanceof PatternProviderMenuAdvancedSync sync) {
            desired = sync.eap$getAdvancedBlockingSynced();
        }

        if (desired != this.eap$AdvancedBlockingEnabled) {
            this.eap$AdvancedBlockingEnabled = desired;
            this.eap$AdvancedBlockingToggle.setState(desired);
            // 同步 tooltip 二行提示
            this.eap$AdvancedBlockingToggle.setTooltipOn(java.util.List.of(
                    Component.literal("高级阻挡模式"),
                    Component.literal(desired ? "高级阻挡模式：已开启" : "高级阻挡模式：已关闭")
            ));
            this.eap$AdvancedBlockingToggle.setTooltipOff(java.util.List.of(
                    Component.literal("高级阻挡模式"),
                    Component.literal(desired ? "高级阻挡模式：已开启" : "高级阻挡模式：已关闭")
            ));
        }
    }
}
