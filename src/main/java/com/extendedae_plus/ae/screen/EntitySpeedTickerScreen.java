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
import com.extendedae_plus.util.entitySpeed.PowerUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体加速器界面，显示加速状态、卡数量、能耗和倍率信息。
 */
public class EntitySpeedTickerScreen<C extends EntitySpeedTickerMenu> extends UpgradeableScreen<C> {
    private boolean eap$entitySpeedTickerEnabled = false;                   // 本地缓存的加速开关状态
    private boolean eap$redstoneControlEnabled = false;                     // 本地缓存的红石控制状态
    private final SettingToggleButton<YesNo> eap$entitySpeedTickerToggle;   // 加速开关按钮
    private final SettingToggleButton<YesNo> eap$redstoneControlToggle;     // 红石控制开关按钮

    /**
     * 构造函数，初始化界面和控件。
     * @param menu 实体加速器菜单
     * @param playerInventory 玩家背包
     * @param title 界面标题
     * @param style 界面样式
     */
    public EntitySpeedTickerScreen(EntitySpeedTickerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super((C) menu, playerInventory, title, style);
        this.addToLeftToolbar(CommonButtons.togglePowerUnit()); // 添加功率单位切换按钮
        this.eap$entitySpeedTickerEnabled = menu.getAccelerateEnabled();
        this.eap$redstoneControlEnabled = menu.getRedstoneControlEnabled();

        // 初始化加速开关按钮
        eap$entitySpeedTickerToggle = new SettingToggleButton<>(
                Settings.BLOCKING_MODE,
                this.eap$entitySpeedTickerEnabled ? YesNo.YES : YesNo.NO,
                (btn, backwards) ->
                        ModNetwork.CHANNEL.sendToServer(new ToggleEntityTickerC2SPacket(ToggleEntityTickerC2SPacket.Setting.accelerateEnabled))
        ) {
            @Override
            public List<Component> getTooltipMessage() {
                if (menu.targetBlacklisted) {
                    return List.of(
                            Component.translatable("extendedae_plus.gui.entity_acceleration.title"),
                            Component.translatable("extendedae_plus.gui.entity_acceleration.blacklisted")
                    );
                }
                boolean enabled = eap$entitySpeedTickerEnabled;
                return List.of(
                        Component.translatable("extendedae_plus.gui.entity_acceleration.title"),
                        enabled ? Component.translatable("extendedae_plus.gui.entity_acceleration.enabled") :
                                Component.translatable("extendedae_plus.gui.entity_acceleration.disabled")
                );
            }

            @Override
            protected Icon getIcon() {
                if (menu.targetBlacklisted) return Icon.INVALID;
                return this.getCurrentValue() == YesNo.YES ? Icon.VALID : Icon.INVALID;
            }
        };
        eap$entitySpeedTickerToggle.set(this.eap$entitySpeedTickerEnabled ? YesNo.YES : YesNo.NO);
        this.addToLeftToolbar(eap$entitySpeedTickerToggle);

        // 初始化加速开关按钮
        eap$redstoneControlToggle = new SettingToggleButton<>(
                Settings.BLOCKING_MODE,
                this.eap$redstoneControlEnabled ? YesNo.YES : YesNo.NO,
                (btn, backwards) ->
                        ModNetwork.CHANNEL.sendToServer(new ToggleEntityTickerC2SPacket(ToggleEntityTickerC2SPacket.Setting.redstoneControlEnabled))
        ) {
            @Override
            public List<Component> getTooltipMessage() {
                boolean enabled = eap$redstoneControlEnabled;
                return List.of(
                        Component.translatable("extendedae_plus.gui.redstone_control.title"),
                        enabled ? Component.translatable("extendedae_plus.gui.redstone_control.enabled") :
                                Component.translatable("extendedae_plus.gui.redstone_control.disabled")                );
            }

            @Override
            protected Icon getIcon() {
                return this.getCurrentValue() == YesNo.YES ? Icon.REDSTONE_LOW : Icon.REDSTONE_IGNORE;
            }
        };
        eap$redstoneControlToggle.set(this.eap$redstoneControlEnabled ? YesNo.YES : YesNo.NO);
        this.addToLeftToolbar(eap$redstoneControlToggle);
    }

    /**
     * 在渲染前更新界面状态。
     */
    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (eap$entitySpeedTickerToggle != null && menu != null) {
            eap$entitySpeedTickerEnabled = menu.getAccelerateEnabled();
            // 如果目标在黑名单，禁用按钮并显示关闭状态
            eap$entitySpeedTickerToggle.set(menu.targetBlacklisted ? YesNo.NO : (eap$entitySpeedTickerEnabled ? YesNo.YES : YesNo.NO));
            eap$entitySpeedTickerToggle.active = !menu.targetBlacklisted;
        }
        
        if (eap$redstoneControlToggle != null && menu != null) {
            eap$redstoneControlEnabled = menu.getRedstoneControlEnabled();
            eap$redstoneControlToggle.set(eap$redstoneControlEnabled ? YesNo.YES : YesNo.NO);
        }
        
        textData();
    }

    /**
     * 刷新界面显示。
     */
    public void refreshGui() {
        textData();
    }

    /**
     * 更新界面文本内容，包括加速状态、速度、能耗和倍率。
     */
    private void textData() {
        Map<String, Component> textContents = new HashMap<>();
        if (getMenu().targetBlacklisted || !getMenu().getAccelerateEnabled()) {
            // 黑名单禁用或加速关闭时的默认显示
            textContents.put("enable", Component.translatable("screen.extendedae_plus.entity_speed_ticker.enable"));
            textContents.put("speed", Component.translatable("screen.extendedae_plus.entity_speed_ticker.speed", 0));
            textContents.put("energy", Component.translatable("screen.extendedae_plus.entity_speed_ticker.energy", Platform.formatPower(0.0, false)));
            textContents.put("power_ratio", Component.translatable("screen.extendedae_plus.entity_speed_ticker.power_ratio", PowerUtils.formatPercentage(0.0)));
            textContents.put("multiplier", Component.translatable("screen.extendedae_plus.entity_speed_ticker.multiplier", String.format("%.2fx", 0.0)));
        } else {
            // 正常状态下显示实际数据
            int energyCardCount = getMenu().energyCardCount;
            double multiplier = getMenu().multiplier;
            int effectiveSpeed = getMenu().effectiveSpeed;
            double finalPower = PowerUtils.getCachedPower(effectiveSpeed, energyCardCount);
            double remainingRatio = PowerUtils.getCachedRatio(energyCardCount);

            textContents.put("enable", getMenu().networkEnergySufficient ? null :
                    Component.translatable("screen.extendedae_plus.entity_speed_ticker.warning_network_energy_insufficient"));
            textContents.put("speed", Component.translatable("screen.extendedae_plus.entity_speed_ticker.speed", effectiveSpeed));
            textContents.put("energy", Component.translatable("screen.extendedae_plus.entity_speed_ticker.energy", Platform.formatPower(finalPower, false)));
            textContents.put("power_ratio", Component.translatable("screen.extendedae_plus.entity_speed_ticker.power_ratio", PowerUtils.formatPercentage(remainingRatio)));
            textContents.put("multiplier", Component.translatable("screen.extendedae_plus.entity_speed_ticker.multiplier", String.format("%.2fx", multiplier)));
        }
        textContents.forEach(this::setTextContent);
    }
}