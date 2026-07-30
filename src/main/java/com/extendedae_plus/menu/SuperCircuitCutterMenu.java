package com.extendedae_plus.menu;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.OutputSlot;
import com.extendedae_plus.content.cutter.SuperCircuitCutterBlockEntity;
import com.extendedae_plus.init.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;

/** 超级电路切片机的输入、输出和升级卡菜单。 */
public class SuperCircuitCutterMenu extends UpgradeableMenu<SuperCircuitCutterBlockEntity> implements IProgressProvider {
    @GuiSync(3)
    public int processingTime = -1;
    @GuiSync(8)
    public YesNo autoExport = YesNo.NO;

    public SuperCircuitCutterMenu(int id, Inventory inventory, SuperCircuitCutterBlockEntity host) {
        super(ModMenuTypes.CIRCUIT_CUTTER_PLUS.get(), id, inventory, host);
        addSlot(new AppEngSlot(host.getInput(), 0), SlotSemantics.MACHINE_INPUT);
        addSlot(new OutputSlot(host.getOutput(), 0, null), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager configManager) {
        autoExport = configManager.getSetting(Settings.AUTO_EXPORT);
    }

    @Override
    protected void standardDetectAndSendChanges() {
        if (isServerSide()) {
            processingTime = getHost().getProgress();
        }
        super.standardDetectAndSendChanges();
    }

    @Override
    public int getCurrentProgress() {
        return processingTime;
    }

    @Override
    public int getMaxProgress() {
        return SuperCircuitCutterBlockEntity.MAX_PROGRESS;
    }

    public YesNo getAutoExport() {
        return autoExport;
    }
}
