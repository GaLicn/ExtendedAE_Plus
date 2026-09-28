package com.extendedae_plus.client.screen;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.core.localization.Tooltips;
import com.extendedae_plus.content.crystal.SuperCrystalAssemblerBlockEntity;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.menu.SuperCrystalAssemblerMenu;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.client.button.EPPIcon;
import com.glodblock.github.extendedae.client.gui.subgui.OutputSideConfig;
import com.glodblock.github.extendedae.network.EAENetworkHandler;
import com.glodblock.github.extendedae.network.packet.CEAEGenericPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/** 复用 ExtendedAE 原机布局的超级水晶装配器界面。 */
public class SuperCrystalAssemblerScreen extends UpgradeableScreen<SuperCrystalAssemblerMenu> {
    private final ProgressBar progressBar;
    private final ServerSettingToggleButton<YesNo> autoExportButton;
    private final ActionEPPButton outputSideButton;

    public SuperCrystalAssemblerScreen(SuperCrystalAssemblerMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        progressBar = new ProgressBar(menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        widgets.add("progressBar", progressBar);
        autoExportButton = new ServerSettingToggleButton<>(Settings.AUTO_EXPORT, YesNo.NO);
        this.addToLeftToolbar(autoExportButton);
        outputSideButton = new ActionEPPButton(button -> openOutputConfig(), EPPIcon.OUTPUT_SIDES);
        outputSideButton.setMessage(Component.translatable("gui.extendedae.set_output_sides.open"));
        this.addToLeftToolbar(outputSideButton);
    }

    private void openOutputConfig() {
        if (getMenu().getHost() != null) {
            switchToScreen(new OutputSideConfig<>(this, new ItemStack(ModItems.CRYSTAL_ASSEMBLER_PLUS.get()),
                    getMenu().getHost(), getMenu().getOutputSides(),
                    (side, value) -> EAENetworkHandler.INSTANCE.sendToServer(new CEAEGenericPacket("set_side", side.getName(), value))));
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        int percent = menu.getCurrentProgress() * 100 / menu.getMaxProgress();
        progressBar.setFullMsg(Component.literal(percent + "%"));
        autoExportButton.set(menu.getAutoExport());
        outputSideButton.setVisibility(menu.getAutoExport() == YesNo.YES);
    }

    @Override
    protected void extractTooltip(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && isTankSlot(hoveredSlot)) {
            var tooltip = new ArrayList<>(getTooltipFromContainerItem(hoveredSlot.getItem()));
            var stack = GenericStack.fromItemStack(hoveredSlot.getItem());
            long amount = stack == null ? 0 : stack.amount();
            tooltip.add(Component.translatable("gui.extendedae_plus.crystal_assembler_plus.amount", amount,
                    SuperCrystalAssemblerBlockEntity.TANK_CAP).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            graphics.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    private boolean isTankSlot(Slot slot) {
        return slot != null && slot.isActive() && slot.hasItem() && menu.isTank(slot);
    }
}
