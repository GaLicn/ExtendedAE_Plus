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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.HashMap;
import java.util.Map;

public class EntitySpeedTickerScreen extends UpgradeableScreen<EntitySpeedTickerMenu> {
    private final EAPSettingToggleButton<YesNo> accelerateButton; // 加速开关按钮
    private final EAPSettingToggleButton<YesNo> redstoneControlButton; // 加速开关按钮

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

        this.accelerateButton = new EAPServerSettingToggleButton<>(EAPSettings.ACCELERATE, YesNo.NO);
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
        this.textData();
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
    }

    public void refreshGui() {
        this.textData();
    }

    /**
     * 更新界面文本内容，包括加速状态、速度、能耗和倍率。
     */
    private void textData() {
        Map<String, Component> textContents = new HashMap<>();
        if (this.getMenu().targetBlacklisted) {
            // 黑名单禁用时的默认显示
            textContents.put("enable", Component.translatable("screen.extendedae_plus.entity_speed_ticker.enable"));
            textContents.put("speed", Component.translatable("screen.extendedae_plus.entity_speed_ticker.speed", 0));
            textContents.put("energy", Component.translatable("screen.extendedae_plus.entity_speed_ticker.energy", Platform.formatPower(0.0, false)));
            textContents.put("power_ratio", Component.translatable("screen.extendedae_plus.entity_speed_ticker.power_ratio", PowerUtils.formatPercentage(0.0)));
            textContents.put("multiplier", Component.translatable("screen.extendedae_plus.entity_speed_ticker.multiplier", String.format("%.2fx", 0.0)));
        } else {
            // 正常状态下显示实际数据
            int energyCardCount = this.getMenu().energyCardCount;
            double multiplier = this.getMenu().multiplier;
            int effectiveSpeed = this.getMenu().effectiveSpeed;
            double finalPower = PowerUtils.computeFinalPowerForProduct(effectiveSpeed, energyCardCount);
            double remainingRatio = PowerUtils.getRemainingRatio(energyCardCount);

            textContents.put("enable", this.getMenu().networkEnergySufficient == YesNo.YES ? null :
                    Component.translatable("screen.extendedae_plus.entity_speed_ticker.warning_network_energy_insufficient"));
            textContents.put("speed", Component.translatable("screen.extendedae_plus.entity_speed_ticker.speed", effectiveSpeed));
            textContents.put("energy", Component.translatable("screen.extendedae_plus.entity_speed_ticker.energy", Platform.formatPower(finalPower, false)));
            textContents.put("power_ratio", Component.translatable("screen.extendedae_plus.entity_speed_ticker.power_ratio", PowerUtils.formatPercentage(remainingRatio)));
            textContents.put("multiplier", Component.translatable("screen.extendedae_plus.entity_speed_ticker.multiplier", String.format("%.2fx", multiplier)));
        }
        textContents.forEach(this::setTextContent);
    }
}