package com.extendedae_plus.menu;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.core.localization.Tooltips;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.OutputSlot;
import appeng.util.ConfigMenuInventory;
import com.extendedae_plus.content.crystal.SuperCrystalAssemblerBlockEntity;
import com.extendedae_plus.init.ModMenuTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/** 超级水晶装配器的库存、液槽、进度与升级卡菜单。 */
public class SuperCrystalAssemblerMenu extends UpgradeableMenu<SuperCrystalAssemblerBlockEntity> implements IProgressProvider {
    @GuiSync(3)
    public int processingTime = -1;
    @GuiSync(8)
    public YesNo autoExport = YesNo.NO;

    private final AppEngSlot tankSlot;

    public SuperCrystalAssemblerMenu(int id, Inventory inventory, SuperCrystalAssemblerBlockEntity host) {
        super(ModMenuTypes.CRYSTAL_ASSEMBLER_PLUS.get(), id, inventory, host);
        for (int slot = 0; slot < SuperCrystalAssemblerBlockEntity.SLOTS; slot++) {
            this.addSlot(new AppEngSlot(host.getInput(), slot), SlotSemantics.MACHINE_INPUT);
        }
        this.addSlot(tankSlot = new AppEngSlot(new ConfigMenuInventory(host.getTank()), 0), SlotSemantics.STORAGE);
        this.addSlot(new OutputSlot(host.getOutput(), 0, null), SlotSemantics.MACHINE_OUTPUT);
        tankSlot.setEmptyTooltip(() -> List.of(
                Component.translatable("gui.extendedae_plus.crystal_assembler_plus.tank_empty"),
                Component.translatable("gui.extendedae_plus.crystal_assembler_plus.amount", 0,
                        SuperCrystalAssemblerBlockEntity.TANK_CAP).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT)));
    }

    public boolean isTank(Slot slot) {
        return slot == tankSlot;
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager configManager) {
        autoExport = configManager.getSetting(Settings.AUTO_EXPORT);
    }

    @Override
    protected void standardDetectAndSendChanges() {
        if (this.isServerSide()) {
            processingTime = this.getHost().getProgress();
        }
        super.standardDetectAndSendChanges();
    }

    @Override
    public int getCurrentProgress() {
        return processingTime;
    }

    @Override
    public int getMaxProgress() {
        return SuperCrystalAssemblerBlockEntity.MAX_PROGRESS;
    }

    public YesNo getAutoExport() {
        return autoExport;
    }
}
