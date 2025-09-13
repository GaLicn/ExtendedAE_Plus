package com.extendedae_plus.ae.screen;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
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
                boolean enabled = eap$entitySpeedTickerEnabled;
                var title = Component.literal("实体加速");
                var stateLine = enabled
                        ? Component.literal("已启用: 将加速目标方块实体的tick")
                        : Component.literal("已关闭: 不会对目标方块实体进行加速");
                return List.of(title, stateLine);
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
            this.eap$entitySpeedTickerToggle.set(desired ? YesNo.YES : YesNo.NO);
        }
        textData();
    }

    public void refreshGui() {
        textData();
    }

    private void textData() {
        int energyCardCount = getMenu().energyCardCount;
        double multiplier = getMenu().multiplier;
        int effectiveSpeed = getMenu().effectiveSpeed;

        double finalPower = PowerUtils.computeFinalPowerForProduct(effectiveSpeed, energyCardCount);
        double remainingRatio = PowerUtils.getRemainingRatio(energyCardCount);

        setTextContent("speed", Component.translatable("screen.extendedae_plus.entity_speed_ticker.speed", effectiveSpeed));
        setTextContent("energy", Component.translatable("screen.extendedae_plus.entity_speed_ticker.energy", Platform.formatPower(finalPower, false)));
        setTextContent("power_ratio", Component.translatable("screen.extendedae_plus.entity_speed_ticker.power_ratio", PowerUtils.formatPercentage(remainingRatio)));
        setTextContent("multiplier", Component.translatable("screen.extendedae_plus.entity_speed_ticker.multiplier", String.format("%.2fx", multiplier)));
    }
}