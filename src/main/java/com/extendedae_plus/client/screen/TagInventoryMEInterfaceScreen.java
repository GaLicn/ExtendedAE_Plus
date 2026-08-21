package com.extendedae_plus.client.screen;

import com.extendedae_plus.content.ae2.TagInventoryMEInterfaceBlockEntity;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.menu.TagInventoryMEInterfaceMenu;
import com.extendedae_plus.network.TagInventoryFilterC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class TagInventoryMEInterfaceScreen extends AbstractContainerScreen<TagInventoryMEInterfaceMenu> {

    private static final ResourceLocation BACKGROUND = new ResourceLocation("ae2", "textures/guis/background.png");
    private static final Pattern TAG_EXPRESSION_FILTER = Pattern.compile("[0-9A-Za-z* &|^!():/_.\\r\\n-]*");

    private EditBox whiteListInput;
    private EditBox blackListInput;

    public TagInventoryMEInterfaceScreen(TagInventoryMEInterfaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 220;
        this.imageHeight = 130;
        this.inventoryLabelY = this.imageHeight;
    }

    @Override
    protected void init() {
        super.init();

        int inputX = this.leftPos + 12;
        int inputWidth = this.imageWidth - 24;
        this.whiteListInput = this.createInput(inputX, this.topPos + 34, inputWidth, this.menu.getWhiteListExpression());
        this.blackListInput = this.createInput(inputX, this.topPos + 70, inputWidth, this.menu.getBlackListExpression());
        this.addRenderableWidget(this.whiteListInput);
        this.addRenderableWidget(this.blackListInput);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.extendedae_plus.tag_inventory_me_interface.save"),
                        button -> this.saveFilters())
                .bounds(this.leftPos + this.imageWidth - 70, this.topPos + 100, 58, 18)
                .build());
        this.setInitialFocus(this.whiteListInput);
    }

    private EditBox createInput(int x, int y, int width, String value) {
        EditBox input = new EditBox(this.font, x, y, width, 18, Component.empty());
        input.setMaxLength(TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH);
        input.setValue(value == null ? "" : value);
        // 放行大小写字符和 Windows 剪贴板常见的 CRLF 换行。
        input.setFilter(text -> TAG_EXPRESSION_FILTER.matcher(text).matches());
        return input;
    }

    private void saveFilters() {
        ModNetwork.CHANNEL.sendToServer(new TagInventoryFilterC2SPacket(
                this.menu.getBlockEntityPos(), this.whiteListInput.getValue(), this.blackListInput.getValue()));
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.closeContainer();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTicks);
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTicks, int mouseX, int mouseY) {
        gfx.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(this.font, this.title, 8, 6, 0x404040, false);
        gfx.drawString(this.font, Component.translatable("gui.extendedae_plus.tag_inventory_me_interface.whitelist"),
                12, 22, 0x404040, false);
        gfx.drawString(this.font, Component.translatable("gui.extendedae_plus.tag_inventory_me_interface.blacklist"),
                12, 58, 0x404040, false);
        gfx.drawString(this.font, Component.translatable("gui.extendedae_plus.tag_inventory_me_interface.hint"),
                12, 104, 0x606060, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.getFocused() instanceof EditBox
                && Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
