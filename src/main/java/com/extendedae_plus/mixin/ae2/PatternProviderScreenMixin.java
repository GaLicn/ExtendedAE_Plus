package com.extendedae_plus.mixin.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extendedae_plus.api.PatternProviderMenuAdvancedSync;
import com.extendedae_plus.network.ModNetwork;
import com.extendedae_plus.network.ToggleAdvancedBlockingC2SPacket;
import com.extendedae_plus.client.ClientAdvancedBlockingState;
import com.extendedae_plus.mixin.accessor.PatternProviderMenuAdvancedAccessor;
import com.extendedae_plus.mixin.accessor.PatternProviderLogicAccessor;

/**
 * 为 AE2 原版样板供应器界面添加“高级阻挡模式”按钮（仅客户端UI反馈）。
 * - 位于左侧工具栏
 * - 点击后切换图标（YES/NO）并切换 tooltip 提示
 * - 当前不做任何网络/服务端逻辑
 */
@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    @Unique
    private ToggleButton eppAdvancedBlockingToggle;

    @Unique
    private boolean eppAdvancedBlockingEnabled = false;

    @Unique
    private String eppProviderKey = null;

    public PatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void epp$initAdvancedBlocking(C menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        // 计算供应器唯一键：维度ID + 方块坐标
        try {
            var logic = ((PatternProviderMenuAdvancedAccessor) menu).ext$logic();
            var host = ((PatternProviderLogicAccessor) logic).ext$host();
            var be = host.getBlockEntity();
            var level = be.getLevel();
            String dimId = level.dimension().location().toString();
            long posLong = be.getBlockPos().asLong();
            this.eppProviderKey = ClientAdvancedBlockingState.key(dimId, posLong);
            System.out.println("[EPP][CLIENT] init: providerKey=" + this.eppProviderKey);
        } catch (Throwable t) {
            System.out.println("[EPP][CLIENT] init: providerKey resolve failed: " + t);
        }

        // 优先使用该供应器最近一次 S2C 状态；否则回退读取 @GuiSync 初始化
        if (this.eppProviderKey != null && ClientAdvancedBlockingState.has(this.eppProviderKey)) {
            this.eppAdvancedBlockingEnabled = ClientAdvancedBlockingState.get(this.eppProviderKey);
            System.out.println("[EPP][CLIENT] init: use ClientState key=" + this.eppProviderKey + ", value=" + this.eppAdvancedBlockingEnabled);
        } else if (menu instanceof PatternProviderMenuAdvancedSync sync) {
            this.eppAdvancedBlockingEnabled = sync.ext$getAdvancedBlockingSynced();
            System.out.println("[EPP][CLIENT] init: use GuiSync value=" + this.eppAdvancedBlockingEnabled);
        }
        // 使用 ToggleButton 以便在 YES/NO 图标与提示之间动态切换
        this.eppAdvancedBlockingToggle = new ToggleButton(
                Icon.BLOCKING_MODE_YES,
                Icon.BLOCKING_MODE_NO,
                // 提示文本：名称与说明
                Component.literal("高级阻挡模式"),
                Component.literal("高级阻挡模式：当开启时，执行更严格的阻挡判定"),
                (state) -> {
                    // 客户端立即反馈：切换图标/提示
                    this.eppAdvancedBlockingEnabled = state;
                    this.eppAdvancedBlockingToggle.setState(state);
                    System.out.println("[EPP][CLIENT] Click toggle: state=" + state);
                    // 发送 C2S 切换请求
                    ModNetwork.CHANNEL.sendToServer(new ToggleAdvancedBlockingC2SPacket());
                    // 可根据状态调整提示文本（演示性：开启/关闭不同第二行）
                    if (state) {
                        this.eppAdvancedBlockingToggle.setTooltipOn(java.util.List.of(
                                Component.literal("高级阻挡模式"),
                                Component.literal("高级阻挡模式：已开启")));
                        this.eppAdvancedBlockingToggle.setTooltipOff(java.util.List.of(
                                Component.literal("高级阻挡模式"),
                                Component.literal("高级阻挡模式：已开启")));
                    } else {
                        this.eppAdvancedBlockingToggle.setTooltipOn(java.util.List.of(
                                Component.literal("高级阻挡模式"),
                                Component.literal("高级阻挡模式：已关闭")));
                        this.eppAdvancedBlockingToggle.setTooltipOff(java.util.List.of(
                                Component.literal("高级阻挡模式"),
                                Component.literal("高级阻挡模式：已关闭")));
                    }
                }
        );
        this.eppAdvancedBlockingToggle.setState(this.eppAdvancedBlockingEnabled);
        // 初始 tooltip
        this.eppAdvancedBlockingToggle.setTooltipOn(java.util.List.of(
                Component.literal("高级阻挡模式"),
                Component.literal(this.eppAdvancedBlockingEnabled ? "高级阻挡模式：已开启" : "高级阻挡模式：已关闭")
        ));
        this.eppAdvancedBlockingToggle.setTooltipOff(java.util.List.of(
                Component.literal("高级阻挡模式"),
                Component.literal(this.eppAdvancedBlockingEnabled ? "高级阻挡模式：已开启" : "高级阻挡模式：已关闭")
        ));

        this.addToLeftToolbar(this.eppAdvancedBlockingToggle);
    }

    // 每帧刷新：从菜单同步布尔值，保持按钮状态一致
    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void epp$updateAdvancedBlocking(CallbackInfo ci) {
        // 打印一条轻量 tick 日志以确认该方法被调用（频繁输出可在验证后移除）
        // System.out.println("[EPP][CLIENT] updateBeforeRender tick, local=" + this.eppAdvancedBlockingEnabled);

        if (this.eppAdvancedBlockingToggle == null) return;

        boolean desired = this.eppAdvancedBlockingEnabled;
        // 优先使用该供应器最近一次 S2C 值
        if (this.eppProviderKey != null && ClientAdvancedBlockingState.has(this.eppProviderKey)) {
            desired = ClientAdvancedBlockingState.get(this.eppProviderKey);
        } else if (this.menu instanceof PatternProviderMenuAdvancedSync sync) {
            desired = sync.ext$getAdvancedBlockingSynced();
        }

        if (desired != this.eppAdvancedBlockingEnabled) {
            this.eppAdvancedBlockingEnabled = desired;
            this.eppAdvancedBlockingToggle.setState(desired);
            System.out.println("[EPP][CLIENT] updateBeforeRender apply: eppAdvancedBlocking=" + desired);
            // 同步 tooltip 二行提示
            this.eppAdvancedBlockingToggle.setTooltipOn(java.util.List.of(
                    Component.literal("高级阻挡模式"),
                    Component.literal(desired ? "高级阻挡模式：已开启" : "高级阻挡模式：已关闭")
            ));
            this.eppAdvancedBlockingToggle.setTooltipOff(java.util.List.of(
                    Component.literal("高级阻挡模式"),
                    Component.literal(desired ? "高级阻挡模式：已开启" : "高级阻挡模式：已关闭")
            ));
        }
    }
}
