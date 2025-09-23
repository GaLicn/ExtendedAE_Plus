package com.extendedae_plus.ae.screen;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.CommonButtons;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.util.Platform;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.ToggleEntityTickerC2SPacket;
import com.extendedae_plus.util.PowerUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class EntitySpeedTickerScreen<C extends EntitySpeedTickerMenu> extends UpgradeableScreen<C> {
    private boolean eap$entitySpeedTickerEnabled = false;
    private SettingToggleButton<YesNo> eap$entitySpeedTickerToggle;

    public EntitySpeedTickerScreen(
            EntitySpeedTickerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super((C) menu, playerInventory, title, style);
        this.addToLeftToolbar(CommonButtons.togglePowerUnit());

        try{
            this.eap$entitySpeedTickerEnabled = menu.getAccelerateEnabled();
        }catch (Exception ignored){}

        // 使用 SettingToggleButton<YesNo> 的外观（原版图标），但自定义悬停描述为“智能阻挡”
        // 不做本地切换，点击仅发送自定义C2S，显示由@GuiSync回传
        eap$entitySpeedTickerToggle = new SettingToggleButton<>(
                Settings.BLOCKING_MODE,
                this.eap$entitySpeedTickerEnabled ? YesNo.YES : YesNo.NO,
                (btn, backwards) -> {
                    // 不做本地切换，点击仅发送自定义C2S，显示由@GuiSync回传
                    ModNetwork.CHANNEL.sendToServer(new ToggleEntityTickerC2SPacket());
                }
        ) {
            @Override
            public List<Component> getTooltipMessage() {
                // 如果目标在黑名单中，直接显示已禁用的提示
                try {
                    if (menu != null && menu.targetBlacklisted) {
                        var title = Component.literal("实体加速");
                        var stateLine = Component.literal("已禁用（目标在黑名单）");
                        return List.of(title, stateLine);
                    }
                } catch (Exception ignored) {}

                boolean enabled = eap$entitySpeedTickerEnabled;
                var title = Component.literal("实体加速");
                var stateLine = enabled
                        ? Component.literal("已启用: 将加速目标方块实体的tick")
                        : Component.literal("已关闭: 不会对目标方块实体进行加速");
                return List.of(title, stateLine);
            }

            @Override
            protected Icon getIcon() {
                try {
                    if (menu != null && menu.targetBlacklisted) {
                        // 黑名单时显示禁用图标
                        return Icon.INVALID;
                    }
                } catch (Exception ignored) {}

                // 根据当前值显示不同图标（可按需替换 Icon 常量）
                if (this.getCurrentValue() == YesNo.YES) {
                    return Icon.VALID;
                } else {
                    return Icon.INVALID;
                }
            }
        };
        // 初始化后立刻对齐当前@GuiSync状态，避免首帧显示不一致
        eap$entitySpeedTickerToggle.set(this.eap$entitySpeedTickerEnabled ? YesNo.YES : YesNo.NO);

        this.addToLeftToolbar(eap$entitySpeedTickerToggle);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        if (this.eap$entitySpeedTickerToggle != null) {
            boolean desired = this.eap$entitySpeedTickerEnabled;
            if (this.menu != null) {
                desired = this.menu.getAccelerateEnabled();
            }

            this.eap$entitySpeedTickerEnabled = desired;
            // 如果目标在黑名单中，禁用切换并强制显示为关闭
            if (this.menu != null && this.menu.targetBlacklisted) {
                this.eap$entitySpeedTickerToggle.set(YesNo.NO);
                this.eap$entitySpeedTickerToggle.active = false;
            } else {
                this.eap$entitySpeedTickerToggle.set(desired ? YesNo.YES : YesNo.NO);
                this.eap$entitySpeedTickerToggle.active =  true;
            }
        }
        textData();
    }

    public void refreshGui() {
        textData();
    }

    private void textData() {
        // 如果目标被黑名单禁止，则显示禁用状态并把数值显示为 0
        if (getMenu().targetBlacklisted) {
            setTextContent("enable", Component.translatable("screen.extendedae_plus.entity_speed_ticker.enable"));
            setTextContent("speed", Component.translatable("screen.extendedae_plus.entity_speed_ticker.speed", 0));
            setTextContent("energy", Component.translatable("screen.extendedae_plus.entity_speed_ticker.energy", Platform.formatPower(0.0, false)));
            setTextContent("power_ratio", Component.translatable("screen.extendedae_plus.entity_speed_ticker.power_ratio", PowerUtils.formatPercentage(0.0)));
            setTextContent("multiplier", Component.translatable("screen.extendedae_plus.entity_speed_ticker.multiplier", String.format("%.2fx", 0.0)));
            return;
        }

        int energyCardCount = getMenu().energyCardCount;
        double multiplier = getMenu().multiplier;
        int effectiveSpeed = getMenu().effectiveSpeed;
        double finalPower = PowerUtils.computeFinalPowerForProduct(effectiveSpeed, energyCardCount);
        double remainingRatio = PowerUtils.getRemainingRatio(energyCardCount);

        // 如果网络能量不足，优先显示警告信息并在能量值处显示 0
        if (getMenu().networkEnergyInsufficient) {
            setTextContent("enable", Component.translatable("screen.extendedae_plus.entity_speed_ticker.warning_network_energy_insufficient"));
        }
        setTextContent("speed", Component.translatable("screen.extendedae_plus.entity_speed_ticker.speed", effectiveSpeed));
        setTextContent("energy", Component.translatable("screen.extendedae_plus.entity_speed_ticker.energy", Platform.formatPower(finalPower, false)));
        setTextContent("power_ratio", Component.translatable("screen.extendedae_plus.entity_speed_ticker.power_ratio", PowerUtils.formatPercentage(remainingRatio)));
        setTextContent("multiplier", Component.translatable("screen.extendedae_plus.entity_speed_ticker.multiplier", String.format("%.2fx", multiplier)));
    }
}