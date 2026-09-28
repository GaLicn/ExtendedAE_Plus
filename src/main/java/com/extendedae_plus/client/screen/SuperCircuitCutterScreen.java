package com.extendedae_plus.client.screen;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.menu.SuperCircuitCutterMenu;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.client.button.EPPIcon;
import com.glodblock.github.extendedae.client.gui.subgui.OutputSideConfig;
import com.glodblock.github.extendedae.network.EAENetworkHandler;
import com.glodblock.github.extendedae.network.packet.CEAEGenericPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** 复用原电路切片机布局的超级版本界面。 */
public class SuperCircuitCutterScreen extends UpgradeableScreen<SuperCircuitCutterMenu> {
    private final ProgressBar progressBar;
    private final ServerSettingToggleButton<YesNo> autoExportButton;
    private final ActionEPPButton outputSideButton;

    public SuperCircuitCutterScreen(SuperCircuitCutterMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        progressBar = new ProgressBar(menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        widgets.add("progressBar", progressBar);
        autoExportButton = new ServerSettingToggleButton<>(Settings.AUTO_EXPORT, YesNo.NO);
        addToLeftToolbar(autoExportButton);
        outputSideButton = new ActionEPPButton(button -> openOutputConfig(), EPPIcon.OUTPUT_SIDES);
        outputSideButton.setMessage(Component.translatable("gui.extendedae.set_output_sides.open"));
        addToLeftToolbar(outputSideButton);
    }

    private void openOutputConfig() {
        if (getMenu().getHost() != null) {
            switchToScreen(new OutputSideConfig<>(this, new ItemStack(ModItems.CIRCUIT_CUTTER_PLUS.get()),
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
}
