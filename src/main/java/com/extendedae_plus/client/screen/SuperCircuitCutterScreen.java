package com.extendedae_plus.client.screen;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import com.extendedae_plus.menu.SuperCircuitCutterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 复用原电路切片机布局的超级版本界面。 */
public class SuperCircuitCutterScreen extends UpgradeableScreen<SuperCircuitCutterMenu> {
    private final ProgressBar progressBar;
    private final ServerSettingToggleButton<YesNo> autoExportButton;

    public SuperCircuitCutterScreen(SuperCircuitCutterMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        progressBar = new ProgressBar(menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        widgets.add("progressBar", progressBar);
        autoExportButton = new ServerSettingToggleButton<>(Settings.AUTO_EXPORT, YesNo.NO);
        addToLeftToolbar(autoExportButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        int percent = menu.getCurrentProgress() * 100 / menu.getMaxProgress();
        progressBar.setFullMsg(Component.literal(percent + "%"));
        autoExportButton.set(menu.getAutoExport());
    }
}
