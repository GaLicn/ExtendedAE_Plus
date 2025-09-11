package com.extendedae_plus.ae.screen;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.util.PowerUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class EntitySpeedTickerScreen<C extends EntitySpeedTickerMenu> extends UpgradeableScreen<C> {

    public EntitySpeedTickerScreen(
            EntitySpeedTickerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super((C) menu, playerInventory, title, style);
    }

    @Override
    protected void updateBeforeRender(){
        super.updateBeforeRender();
        textData();
    }

    public void refreshGui(){
        textData();
    }

    private void textData() {
        int speedCardCount = getMenu().speedCardCount;
        int energyCardCount = getMenu().energyCardCount;

        double finalPower = PowerUtils.getFinalPower(speedCardCount, energyCardCount);
        int speed = PowerUtils.getSpeedMultiplier(speedCardCount);
        double remainingRatio = PowerUtils.getRemainingRatio(energyCardCount);

        setTextContent("speed", Component.translatable("screen.extendedae_plus.entity_speed_ticker.speed", speed));
        setTextContent("energy", Component.translatable("screen.extendedae_plus.entity_speed_ticker.energy", PowerUtils.formatPower(finalPower)));
        setTextContent("power_ratio", Component.translatable("screen.extendedae_plus.entity_speed_ticker.power_ratio", PowerUtils.formatPercentage(remainingRatio)));
    }
}