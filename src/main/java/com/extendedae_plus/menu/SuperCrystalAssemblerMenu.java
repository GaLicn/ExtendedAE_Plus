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
import com.glodblock.github.extendedae.container.helper.DirectionSet;
import com.glodblock.github.glodium.network.packet.sync.ActionMap;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** 超级水晶装配器的库存、液槽、进度与升级卡菜单。 */
public class SuperCrystalAssemblerMenu extends UpgradeableMenu<SuperCrystalAssemblerBlockEntity>
        implements IProgressProvider, IActionHolder {
    @GuiSync(3)
    public int processingTime = -1;
    @GuiSync(8)
    public YesNo autoExport = YesNo.NO;
    @GuiSync(9)
    public DirectionSet outputSides = new DirectionSet(new ArrayList<>());

    private final ActionMap actions = ActionMap.create();
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
        // 输出侧配置：客户端逐个面开关，服务端直接改主机的输出面集合。
        this.actions.put("set_side", o -> this.setOutputSide(o.get(0), o.get(1)));
    }

    private void setOutputSide(String name, boolean value) {
        var side = Direction.byName(name);
        if (value) {
            this.getHost().getOutputSides().add(side);
        } else {
            this.getHost().getOutputSides().remove(side);
        }
    }

    @NotNull
    @Override
    public ActionMap getActionMap() {
        return this.actions;
    }

    public List<Direction> getOutputSides() {
        return outputSides.sides();
    }

    public boolean isTank(Slot slot) {
        return slot == tankSlot;
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager configManager) {
        autoExport = configManager.getSetting(Settings.AUTO_EXPORT);
        this.outputSides.clear();
        this.outputSides.addAll(this.getHost().getOutputSides());
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
