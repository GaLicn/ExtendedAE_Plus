package com.extendedae_plus.client.screen;

import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.menu.NetworkPatternControllerMenu;
import com.extendedae_plus.network.provider.GlobalToggleProviderModesC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class GlobalProviderModesScreen extends AbstractContainerScreen<NetworkPatternControllerMenu> {
    private static final Component CUSTOM_TITLE = Component.translatable("block.extendedae_plus.network_pattern_controller");
    public GlobalProviderModesScreen(NetworkPatternControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 240;
        this.imageHeight = 140;
    }

    @Override
    protected void init() {
        super.init();
        int w = 70;           // 按钮宽
        int h = 20;           // 按钮高
        int s = 8;            // 按钮间距
        int y = this.topPos + 28; // 第一行 Y
        // 计算三列按钮的左侧起点，使其在面板内水平居中
        int totalW3 = w * 3 + s * 2;
        int x = this.leftPos + (this.imageWidth - totalW3) / 2;

        // 行1：三个单项切换
        addRenderableWidget(Button.builder(Component.translatable("gui.extendedae_plus.global.toggle_blocking"), b ->
                ModNetwork.CHANNEL.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Op.TOGGLE,
                        GlobalToggleProviderModesC2SPacket.Op.NOOP,
                        GlobalToggleProviderModesC2SPacket.Op.NOOP,
                        this.menu.getBlockEntityPos()
                ))).bounds(x, y, w, h).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.extendedae_plus.global.toggle_adv_blocking"), b ->
                ModNetwork.CHANNEL.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Op.NOOP,
                        GlobalToggleProviderModesC2SPacket.Op.TOGGLE,
                        GlobalToggleProviderModesC2SPacket.Op.NOOP,
                        this.menu.getBlockEntityPos()
                ))).bounds(x + w + s, y, w, h).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.extendedae_plus.global.toggle_smart_doubling"), b ->
                ModNetwork.CHANNEL.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Op.NOOP,
                        GlobalToggleProviderModesC2SPacket.Op.NOOP,
                        GlobalToggleProviderModesC2SPacket.Op.TOGGLE,
                        this.menu.getBlockEntityPos()
                ))).bounds(x + (w + s) * 2, y, w, h).build());

        // 行2：一键全开/全关
        int y2 = y + h + 12;
        // 第二行：两列按钮，总宽并居中
        int totalW2 = w * 2 + s;
        int x2 = this.leftPos + (this.imageWidth - totalW2) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.extendedae_plus.global.all_on"), b ->
                ModNetwork.CHANNEL.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Op.SET_TRUE,
                        GlobalToggleProviderModesC2SPacket.Op.SET_TRUE,
                        GlobalToggleProviderModesC2SPacket.Op.SET_TRUE,
                        this.menu.getBlockEntityPos()
                ))).bounds(x2, y2, w, h).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.extendedae_plus.global.all_off"), b ->
                ModNetwork.CHANNEL.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Op.SET_FALSE,
                        GlobalToggleProviderModesC2SPacket.Op.SET_FALSE,
                        GlobalToggleProviderModesC2SPacket.Op.SET_FALSE,
                        this.menu.getBlockEntityPos()
                ))).bounds(x2 + w + s, y2, w, h).build());
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTicks, int mouseX, int mouseY) {
        // 半透明全屏遮罩，避免底层 HUD（准星/物品栏文字）透出
        gfx.fill(0, 0, this.width, this.height, 0xC0000000);

        // 在按钮区域绘制一个半透明面板，提升可读性
        int pad = 6;
        int panelLeft = this.leftPos - pad;
        int panelTop = this.topPos - pad;
        int panelRight = this.leftPos + this.imageWidth + pad;
        int panelBottom = this.topPos + this.imageHeight + pad;
        gfx.fill(panelLeft, panelTop, panelRight, panelBottom, 0xA01E1E1E);
        // 边框
        gfx.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0x80FFFFFF);
        gfx.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0x80FFFFFF);
        gfx.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, 0x80FFFFFF);
        gfx.fill(panelRight - 1, panelTop, panelRight, panelBottom, 0x80FFFFFF);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTicks);
        gfx.drawString(this.font, CUSTOM_TITLE, this.leftPos + 10, this.topPos + 8, 0xFFFFFF, false);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // 不绘制默认的玩家物品栏标题（例如“物品栏”），避免与自定义面板重叠
        // 标题已在 render() 中手动绘制
    }
}
