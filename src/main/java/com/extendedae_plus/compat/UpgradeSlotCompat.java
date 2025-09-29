package com.extendedae_plus.compat;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * 升级卡槽兼容性管理类
 * 检测ExtendedAE-appflux模组是否存在，如果存在则使用其升级卡槽功能
 * 否则使用我们自己的实现
 */
public class UpgradeSlotCompat {
    private static final String APPFLUX_MOD_ID = "appflux";
    
    /**
     * 检测Applied Flux模组是否存在
     * @return true如果存在，false如果不存在
     */
    public static boolean isAppfluxPresent() {
        return ModList.get().isLoaded(APPFLUX_MOD_ID);
    }
    
    /**
     * 检测是否应该启用我们的升级卡槽功能
     * @return true如果应该启用，false如果检测到appflux模组存在
     */
    public static boolean shouldEnableUpgradeSlots() {
        boolean appfluxExists = isAppfluxPresent();
        return !appfluxExists;
    }
    
    /**
     * 检测是否应该启用频道卡功能
     * 频道卡是我们独有的功能，即使appflux存在也应该启用
     * @return 总是返回true，因为频道卡功能不与appflux冲突
     */
    public static boolean shouldEnableChannelCard() {
        return true; // 频道卡功能总是启用，因为appflux没有实现这个功能
    }
    
    /**
     * 检测是否应该在Screen中添加升级面板
     * @return true如果应该添加，false如果检测到appflux模组存在
     */
    public static boolean shouldAddUpgradePanelToScreen() {
        return shouldEnableUpgradeSlots();
    }
    
    /**
     * 初始化菜单升级功能（如果需要的话）
     * @param menu 目标菜单
     * @param host 样板供应器逻辑主机
     * @return 是否成功初始化
     */
    public static boolean initMenuUpgrades(AEBaseMenu menu, PatternProviderLogicHost host) {
        if (!shouldEnableUpgradeSlots()) {
            return false;
        }
        
        try {
            // 创建工具箱菜单
            ToolboxMenu toolbox = new ToolboxMenu(menu);
            
            // 设置升级槽
            if (host instanceof IUpgradeableObject upgradeableHost) {
                // 使用反射调用protected的setupUpgrades方法
                try {
                    var setupUpgradesMethod = AEBaseMenu.class.getDeclaredMethod("setupUpgrades", IUpgradeInventory.class);
                    setupUpgradesMethod.setAccessible(true);
                    setupUpgradesMethod.invoke(menu, upgradeableHost.getUpgrades());
                } catch (Exception e) {
                    ExtendedAELogger.LOGGER.error("反射调用setupUpgrades失败", e);
                    return false;
                }
                
                // 使用反射或接口设置工具箱
                if (menu instanceof IUpgradeableMenuCompat compatMenu) {
                    compatMenu.setCompatToolbox(toolbox);
                }
                return true;
            }
        } catch (Exception e) {
            ExtendedAELogger.LOGGER.error("初始化PatternProviderMenu升级功能时出错", e);
        }
        
        return false;
    }
    
    /**
     * 为Screen添加升级面板（如果需要的话）
     * @param widgets 小部件映射
     * @param menu 菜单实例
     * @param style 屏幕样式
     * @return 是否成功添加
     */
    public static boolean addUpgradePanelToScreen(Object widgets, Object menu, ScreenStyle style) {
        if (!shouldAddUpgradePanelToScreen()) {
            return false;
        }
        
        try {
            if (menu instanceof IUpgradeableMenuCompat compatMenu) {
                try {
                    // 使用反射获取widgets的add方法 - 尝试不同的方法签名
                    var widgetsClass = widgets.getClass();
                    var addMethod = widgetsClass.getDeclaredMethod("add", String.class, Object.class);
                    addMethod.setAccessible(true);
                    
                    // 获取升级槽位
                    var menuClass = menu.getClass();
                    var getSlotsMethod = menuClass.getMethod("getSlots", SlotSemantics.class);
                    @SuppressWarnings("unchecked")
                    List<Slot> upgradeSlots = (List<Slot>) getSlotsMethod.invoke(menu, SlotSemantics.UPGRADE);
                    
                    // 添加升级面板
                    UpgradesPanel upgradesPanel = new UpgradesPanel(upgradeSlots, () -> getCompatibleUpgrades(compatMenu));
                    addMethod.invoke(widgets, "upgrades", upgradesPanel);
                    
                    // 添加工具箱面板（如果存在）
                    ToolboxMenu toolbox = compatMenu.getCompatToolbox();
                    if (toolbox != null && toolbox.isPresent()) {
                        ToolboxPanel toolboxPanel = new ToolboxPanel(style, toolbox.getName());
                        addMethod.invoke(widgets, "toolbox", toolboxPanel);
                    }
                    return true;
                } catch (NoSuchMethodException e) {
                    // 尝试其他可能的方法签名
                    try {
                        var widgetsClass = widgets.getClass();
                        var putMethod = widgetsClass.getDeclaredMethod("put", String.class, Object.class);
                        putMethod.setAccessible(true);
                        
                        // 获取升级槽位
                        var menuClass = menu.getClass();
                        var getSlotsMethod = menuClass.getMethod("getSlots", SlotSemantics.class);
                        @SuppressWarnings("unchecked")
                        List<Slot> upgradeSlots = (List<Slot>) getSlotsMethod.invoke(menu, SlotSemantics.UPGRADE);
                        
                        // 添加升级面板
                        UpgradesPanel upgradesPanel = new UpgradesPanel(upgradeSlots, () -> getCompatibleUpgrades(compatMenu));
                        putMethod.invoke(widgets, "upgrades", upgradesPanel);
                        
                        // 添加工具箱面板（如果存在）
                        ToolboxMenu toolbox = compatMenu.getCompatToolbox();
                        if (toolbox != null && toolbox.isPresent()) {
                            ToolboxPanel toolboxPanel = new ToolboxPanel(style, toolbox.getName());
                            putMethod.invoke(widgets, "toolbox", toolboxPanel);
                        }
                        return true;
                    } catch (Exception e2) {
                        ExtendedAELogger.LOGGER.error("反射调用widgets方法失败", e2);
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            ExtendedAELogger.LOGGER.error("为PatternProviderScreen添加升级面板时出错", e);
        }
        
        return false;
    }
    
    /**
     * 获取兼容的升级列表
     */
    private static List<Component> getCompatibleUpgrades(IUpgradeableMenuCompat menu) {
        var list = new ArrayList<Component>();
        list.add(GuiText.CompatibleUpgrades.text());
        
        try {
            IUpgradeInventory upgrades = menu.getCompatUpgrades();
            if (upgrades != null) {
                list.addAll(appeng.api.upgrades.Upgrades.getTooltipLinesForMachine(upgrades.getUpgradableItem()));
            }
        } catch (Exception e) {
            ExtendedAELogger.LOGGER.error("获取兼容升级列表时出错", e);
        }
        
        return list;
    }
    
    /**
     * 兼容性升级菜单接口
     */
    public interface IUpgradeableMenuCompat {
        ToolboxMenu getCompatToolbox();
        void setCompatToolbox(ToolboxMenu toolbox);
        IUpgradeInventory getCompatUpgrades();
    }
}
