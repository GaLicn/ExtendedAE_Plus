package com.extendedae_plus.ae.screen;


import appeng.api.config.YesNo;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.CommonButtons;
import appeng.util.Platform;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.client.gui.widgets.EAPServerSettingToggleButton;
import com.extendedae_plus.client.gui.widgets.EAPSettingToggleButton;
import com.extendedae_plus.util.entitySpeed.PowerUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class EntitySpeedTickerScreen extends UpgradeableScreen<EntitySpeedTickerMenu> {
    private final EAPSettingToggleButton<YesNo> accelerateButton; // 加速开关按钮
    private final EAPSettingToggleButton<YesNo> redstoneControlButton; // 加速开关按钮
    private final TextUpdater textUpdater = new TextUpdater();

    /**
     * 构造函数，初始化界面和控件。
     *
     * @param menu            实体加速器菜单
     * @param playerInventory 玩家背包
     * @param title           界面标题
     * @param style           界面样式
     */
    public EntitySpeedTickerScreen(EntitySpeedTickerMenu menu,
                                   Inventory playerInventory,
                                   Component title,
                                   ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.addToLeftToolbar(CommonButtons.togglePowerUnit()); // 添加功率单位切换按钮

        this.accelerateButton = new EAPServerSettingToggleButton<>(EAPSettings.ACCELERATE, YesNo.YES);
        this.addToLeftToolbar(this.accelerateButton);

        this.redstoneControlButton = new EAPServerSettingToggleButton<>(EAPSettings.REDSTONE_CONTROL, YesNo.NO);
        this.addToLeftToolbar(this.redstoneControlButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        // 如果目标在黑名单，禁用按钮并显示关闭状态
        if (this.menu.targetBlacklisted) {
            this.accelerateButton.active = false;
            this.accelerateButton.set(YesNo.UNDECIDED);
        } else {
            this.accelerateButton.set(this.menu.getAccelerate());
        }
        this.redstoneControlButton.set(this.menu.getRedstoneControl());
        // 文本更新统一处理
        this.textUpdater.update();
    }

    public void refreshGui() {
        this.textUpdater.update();
    }

    private class TextUpdater {
        void update() {
            if (EntitySpeedTickerScreen.this.menu.targetBlacklisted) {
                this.updateBlacklist();
            } else {
                this.updateNormal();
            }
        }

        private void updateBlacklist() {
            this.set("enable", this.translatable("enable"));
            this.set("speed", this.translatable("speed", 0));
            this.set("energy", this.translatable("energy", Platform.formatPower(0, false)));
            this.set("power_ratio", this.translatable("power_ratio", PowerUtils.formatPercentage(0.0)));
            this.set("multiplier", this.translatable("multiplier", "0.00x"));
        }

        private void updateNormal() {
            int energyCardCount = EntitySpeedTickerScreen.this.menu.energyCardCount;
            double multiplier = EntitySpeedTickerScreen.this.menu.multiplier;
            int effectiveSpeed = EntitySpeedTickerScreen.this.menu.effectiveSpeed;
            double finalPower = PowerUtils.computeFinalPowerForProduct(effectiveSpeed, energyCardCount);
            double powerRatio = PowerUtils.getRemainingRatio(energyCardCount);

            this.set("enable", EntitySpeedTickerScreen.this.menu.networkEnergySufficient == YesNo.YES
                    ? null
                    : this.translatable("warning_network_energy_insufficient"));

            this.set("speed", this.translatable("speed", effectiveSpeed));
            this.set("energy", this.translatable("energy", Platform.formatPower(finalPower, false)));
            this.set("power_ratio", this.translatable("power_ratio", PowerUtils.formatPercentage(powerRatio)));
            this.set("multiplier", this.translatable("multiplier", String.format("%.2fx", multiplier)));
        }

        private Component translatable(String key, Object... args) {
            return Component.translatable("screen.extendedae_plus.entity_speed_ticker." + key, args);
        }

        private void set(String id, Component c) {
            EntitySpeedTickerScreen.this.setTextContent(id, c);
        }
    }
}