package com.extendedae_plus.client.screen;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.wireless.LabelNetworkRegistry;
import com.extendedae_plus.menu.LabeledWirelessTransceiverMenu;
import com.extendedae_plus.network.LabelNetworkActionC2SPacket;
import com.extendedae_plus.network.LabelNetworkListC2SPacket;
import com.extendedae_plus.init.ModNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/**
 * 标签无线收发器屏幕（UI 占位，等待按钮布局).
 * 纹理：textures/gui/lable_wireless_transceiver_gui.png，尺寸 256x156.
 * 标签无线收发器屏幕（UI 占位，等待按钮布局）。
 * 纹理：textures/gui/lable_wireless_transceiver_gui.png，尺寸 256x156。
 */
public class LabeledWirelessTransceiverScreen extends AbstractContainerScreen<LabeledWirelessTransceiverMenu> {
    private static final ResourceLocation TEX = ExtendedAEPlus.id("textures/gui/lable_wireless_transceiver_gui.png");
    private static final int BTN_U = 2;
    private static final int BTN_V = 159;
    private static final int BTN_W = 28;
    private static final int BTN_H = 16;
    private static final int TEX_W = 256;
    private static final int TEX_H = 256;

    private static final int LIST_X = 9;
    private static final int LIST_Y = 27;
    private static final int LIST_W = 110; // 118-9+1
    private static final int LIST_H = 114; // 140-27+1
    private static final int ROW_H = 11; // 10px text height + 1px 分隔
    private static final int VISIBLE_ROWS = LIST_H / ROW_H; // 10
    private static final int SCROLL_X = 123;
    private static final int SCROLL_Y = 21;
    private static final int SCROLL_W = 6;
    private static final int SCROLL_H = 121; // 141-21+1
    private static final int INFO_MAX_WIDTH = 116; // 信息区实际宽度(249-134+1=116)

    private EditBox searchBox;
    private ImageButton newBtn;
    private ImageButton deleteBtn;
    private ImageButton setBtn;
    private ImageButton disconnectBtn;

    private final BlockPos bePos;
    private final List<LabelEntry> entries = new ArrayList<>();
    private final List<LabelEntry> filtered = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private String lastSelectedLabel = "";
    private String currentLabel = "";
    private String currentOwner = "";
    private int usedChannels = 0;
    private int maxChannels = 0;

    public LabeledWirelessTransceiverScreen(LabeledWirelessTransceiverMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 256;
        this.imageHeight = 156;
        this.inventoryLabelY = this.imageHeight; // 不显示玩家物品栏标签
        this.bePos = menu.getBlockEntityPos();
    }

    @Override
    protected void init() {
        super.init();
        // 搜索框：起点(134,23) 终点(249,31) => 宽116 高9（取整为9）
        int sx = this.leftPos + 134;
        int sy = this.topPos + 23;
        this.searchBox = new EditBox(this.font, sx, sy, 116, 9, Component.empty());
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(64);
        this.searchBox.setVisible(true);
        this.searchBox.setFocused(false);
        this.searchBox.setResponder(s -> {
            applyFilter();
        });
        this.addRenderableWidget(this.searchBox);

        int startX = this.leftPos + 145;
        int startY = this.topPos + 101;
        int hGap = 30;
        int vGap = 8;
        int secondColX = startX + BTN_W + hGap;
        int secondRowY = startY + BTN_H + vGap;

        this.newBtn = new StateImageButton(startX, startY, BTN_W, BTN_H, BTN_U, BTN_V, 2, 177, 2, 195, TEX, TEX_W, TEX_H,
                b -> sendSet(searchBox.getValue()), Component.translatable("gui.extendedae_plus.labeled_wireless.button.new"));
        this.deleteBtn = new StateImageButton(secondColX, startY, BTN_W, BTN_H, BTN_U, BTN_V, 2, 177, 2, 195, TEX, TEX_W, TEX_H,
                b -> sendDelete(), Component.translatable("gui.extendedae_plus.labeled_wireless.button.delete"));
        this.setBtn = new StateImageButton(startX, secondRowY, BTN_W, BTN_H, BTN_U, BTN_V, 2, 177, 2, 195, TEX, TEX_W, TEX_H,
                b -> sendSet(getSelectedLabel()), Component.translatable("gui.extendedae_plus.labeled_wireless.button.set"));
        this.disconnectBtn = new StateImageButton(secondColX, secondRowY, BTN_W, BTN_H, BTN_U, BTN_V, 2, 177, 2, 195, TEX, TEX_W, TEX_H,
                b -> sendDisconnect(), Component.translatable("gui.extendedae_plus.labeled_wireless.button.refresh"));

        this.addRenderableWidget(this.newBtn);
        this.addRenderableWidget(this.deleteBtn);
        this.addRenderableWidget(this.setBtn);
        this.addRenderableWidget(this.disconnectBtn);

        requestList();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTicks);
        drawAllButtonText(gfx);
        this.renderTooltip(gfx, mouseX, mouseY);
        if (this.searchBox != null) {
            this.searchBox.render(gfx, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        float titleScale = getTitleScale();
        var pose = gfx.pose();
        pose.pushPose();
        pose.translate(8, 8, 0);
        pose.scale(titleScale, titleScale, 1.0f);
        gfx.drawString(this.font, this.title, 0, 0, 0x404040, false);
        pose.popPose();

        pose.pushPose();
        pose.translate(134, 8, 0);
        pose.scale(titleScale, titleScale, 1.0f);
        gfx.drawString(this.font, Component.translatable("gui.extendedae_plus.labeled_wireless.info"), 0, 0, 0x404040, false);
        pose.popPose();
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gfx.blit(TEX, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        // 占位绘制：列表和信息区内的内容框线
        // 标签列表区域
        gfx.fill(this.leftPos + 9, this.topPos + 27, this.leftPos + 118 + 1, this.topPos + 140 + 1, 0x20FFFFFF);
        // 滚动条区域
        gfx.fill(this.leftPos + 123, this.topPos + 21, this.leftPos + 128 + 1, this.topPos + 141 + 1, 0x20000000);
        // 当前收发器信息区域
        gfx.fill(this.leftPos + 134, this.topPos + 41, this.leftPos + 249 + 1, this.topPos + 92 + 1, 0x10FFFFFF);

        renderList(gfx);
        renderScrollBar(gfx);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchBox != null && this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.searchBox);
            return true;
        }
        if (isMouseInList(mouseX, mouseY)) {
            int localY = (int) mouseY - (this.topPos + LIST_Y);
            int row = localY / ROW_H;
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < filtered.size()) {
                selectedIndex = idx;
                lastSelectedLabel = filtered.get(idx).label();
            }
            return true;
        }
        if (isMouseInScrollbar(mouseX, mouseY)) {
            updateScrollByMouse((int) mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseInList(mouseX, mouseY) || isMouseInScrollbar(mouseX, mouseY)) {
            int maxOffset = Math.max(0, filtered.size() - VISIBLE_ROWS);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void renderList(GuiGraphics gfx) {
        int baseX = this.leftPos + LIST_X;
        int baseY = this.topPos + LIST_Y;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scrollOffset + row;
            if (idx >= filtered.size()) break;
            int y = baseY + row * ROW_H;
            if (idx == selectedIndex) {
                gfx.fill(baseX, y, baseX + LIST_W, y + ROW_H, 0x40FFFFFF);
            }
            LabelEntry e = filtered.get(idx);
            String text = this.font.plainSubstrByWidth(e.label(), LIST_W - 2);
            int ty = y + (ROW_H - this.font.lineHeight) / 2;
            gfx.drawString(this.font, text, baseX + 2, ty, 0x404040, false);
        }

        // 信息显示
        int infoX = this.leftPos + 134;
        int infoY = this.topPos + 41;
        float infoScale = getInfoScale();
        String labelLine = Component.translatable("gui.extendedae_plus.labeled_wireless.current_label").getString() + ": " + (currentLabel == null || currentLabel.isEmpty() ? "-" : currentLabel);
        String ownerLine = Component.translatable("gui.extendedae_plus.labeled_wireless.current_owner").getString() + ": " + (currentOwner == null || currentOwner.isEmpty() ? Component.translatable("extendedae_plus.jade.owner.public").getString() : currentOwner);
        Component channelComp = maxChannels <= 0
                ? Component.translatable("extendedae_plus.jade.channels", usedChannels)
                : Component.translatable("extendedae_plus.jade.channels_of", usedChannels, maxChannels);
        drawInfoLine(gfx, labelLine, infoX, infoY, infoScale);
        drawInfoLine(gfx, ownerLine, infoX, infoY + 12, infoScale);
        drawInfoLine(gfx, channelComp.getString(), infoX, infoY + 24, infoScale);
    }

    private void renderScrollBar(GuiGraphics gfx) {
        int total = filtered.size();
        if (total <= VISIBLE_ROWS) {
            // 画静态条
            gfx.fill(this.leftPos + SCROLL_X, this.topPos + SCROLL_Y, this.leftPos + SCROLL_X + SCROLL_W, this.topPos + SCROLL_Y + SCROLL_H, 0x20000000);
            return;
        }
        int maxOffset = total - VISIBLE_ROWS;
        int trackX1 = this.leftPos + SCROLL_X;
        int trackY1 = this.topPos + SCROLL_Y;
        int trackY2 = trackY1 + SCROLL_H;
        gfx.fill(trackX1, trackY1, trackX1 + SCROLL_W, trackY2, 0x20000000);
        int knobH = Math.max(10, (int) ((double) VISIBLE_ROWS / total * SCROLL_H));
        int knobY = trackY1 + (int) ((SCROLL_H - knobH) * (scrollOffset / (double) maxOffset));
        gfx.fill(trackX1, knobY, trackX1 + SCROLL_W, knobY + knobH, 0x80FFFFFF);
    }

    private boolean isMouseInList(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + LIST_X && mouseX < this.leftPos + LIST_X + LIST_W
                && mouseY >= this.topPos + LIST_Y && mouseY < this.topPos + LIST_Y + LIST_H;
    }

    private boolean isMouseInScrollbar(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + SCROLL_X && mouseX < this.leftPos + SCROLL_X + SCROLL_W
                && mouseY >= this.topPos + SCROLL_Y && mouseY < this.topPos + SCROLL_Y + SCROLL_H;
    }

    private void updateScrollByMouse(int mouseY) {
        int total = filtered.size();
        if (total <= VISIBLE_ROWS) return;
        int maxOffset = total - VISIBLE_ROWS;
        int relativeY = mouseY - (this.topPos + SCROLL_Y);
        relativeY = Math.max(0, Math.min(SCROLL_H, relativeY));
        int knobH = Math.max(10, (int) ((double) VISIBLE_ROWS / total * SCROLL_H));
        double ratio = (relativeY - knobH / 2.0) / (double) (SCROLL_H - knobH);
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        scrollOffset = (int) Math.round(ratio * maxOffset);
    }

    private void applyFilter() {
        String prevSelected = lastSelectedLabel;
        String q = searchBox.getValue() == null ? "" : searchBox.getValue().trim();
        filtered.clear();
        if (q.isEmpty()) {
            filtered.addAll(entries);
        } else {
            for (LabelEntry e : entries) {
                if (e.label().contains(q)) {
                    filtered.add(e);
                }
            }
        }
        scrollOffset = 0;
        selectedIndex = -1;
        if (prevSelected != null && !prevSelected.isEmpty()) {
            for (int i = 0; i < filtered.size(); i++) {
                if (filtered.get(i).label().equals(prevSelected)) {
                    selectedIndex = i;
                    ensureSelectionVisible();
                    break;
                }
            }
        }
    }

    private void requestList() {
        ModNetwork.CHANNEL.sendToServer(new LabelNetworkListC2SPacket(bePos));
    }

    private void sendSet(String label) {
        if (label == null) label = "";
        ModNetwork.CHANNEL.sendToServer(new LabelNetworkActionC2SPacket(bePos, label, LabelNetworkActionC2SPacket.Action.SET));
        this.lastSelectedLabel = label;
        this.searchBox.setValue("");
        requestList();
    }

    private void sendDelete() {
        String label = getSelectedLabel();
        if (label == null || label.isEmpty()) {
            label = searchBox.getValue();
        }
        if (label == null) label = "";
        ModNetwork.CHANNEL.sendToServer(new LabelNetworkActionC2SPacket(bePos, label, LabelNetworkActionC2SPacket.Action.DELETE));
        this.lastSelectedLabel = "";
        requestList();
    }

    private void sendDisconnect() {
        ModNetwork.CHANNEL.sendToServer(new LabelNetworkActionC2SPacket(bePos, "", LabelNetworkActionC2SPacket.Action.DISCONNECT));
        this.lastSelectedLabel = "";
        requestList();
    }

    private String getSelectedLabel() {
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            return filtered.get(selectedIndex).label();
        }
        return "";
    }

    public void updateList(List<LabelNetworkRegistry.LabelNetworkSnapshot> list, String currentLabel, String ownerName, int usedChannels, int maxChannels) {
        String prevSelected = getSelectedLabel();
        this.entries.clear();
        for (LabelNetworkRegistry.LabelNetworkSnapshot s : list) {
            this.entries.add(new LabelEntry(s.label(), s.channel()));
        }
        this.currentLabel = currentLabel == null ? "" : currentLabel;
        this.currentOwner = ownerName == null ? "" : ownerName;
        this.usedChannels = usedChannels;
        this.maxChannels = maxChannels;
        if (prevSelected != null && !prevSelected.isEmpty()) {
            this.lastSelectedLabel = prevSelected;
        } else if (this.currentLabel != null && !this.currentLabel.isEmpty()) {
            this.lastSelectedLabel = this.currentLabel;
        } else {
            this.lastSelectedLabel = "";
        }
        applyFilter();
    }

    public boolean isFor(BlockPos pos) {
        return this.bePos.equals(pos);
    }

    private record LabelEntry(String label, long channel) {}

    private void drawAllButtonText(GuiGraphics gfx) {
        // 按钮文本（24px 内居中，避免溢出）。放在 super.render 之后，确保绘制在按钮纹理之上。
        int startX = this.leftPos + 145;
        int startY = this.topPos + 101;
        int hGap = 30;
        int vGap = 8;
        int secondColX = startX + BTN_W + hGap;
        int secondRowY = startY + BTN_H + vGap;

        drawButtonText(gfx, Component.translatable("gui.extendedae_plus.labeled_wireless.button.new"), startX, startY);
        drawButtonText(gfx, Component.translatable("gui.extendedae_plus.labeled_wireless.button.delete"), secondColX, startY);
        drawButtonText(gfx, Component.translatable("gui.extendedae_plus.labeled_wireless.button.set"), startX, secondRowY);
        drawButtonText(gfx, Component.translatable("gui.extendedae_plus.labeled_wireless.button.refresh"), secondColX, secondRowY);
    }

    private void drawButtonText(GuiGraphics gfx, Component text, int x, int y) {
        String s = this.font.plainSubstrByWidth(text.getString(), BTN_W - 4);
        int tx = x + (BTN_W - this.font.width(s)) / 2;
        int ty = y + (BTN_H - this.font.lineHeight) / 2 + 1;
        gfx.drawString(this.font, s, tx, ty, 0xFFFFFF, false);
    }

    private void ensureSelectionVisible() {
        if (selectedIndex < 0) return;
        int maxOffset = Math.max(0, filtered.size() - VISIBLE_ROWS);
        int targetRow = selectedIndex;
        if (targetRow < scrollOffset) {
            scrollOffset = targetRow;
        } else if (targetRow >= scrollOffset + VISIBLE_ROWS) {
            scrollOffset = Math.min(maxOffset, targetRow - VISIBLE_ROWS + 1);
        }
    }

    private String trimInfo(String text, float scale) {
        if (text == null) return "";
        int maxWidth = (int) (INFO_MAX_WIDTH / Math.max(0.0001f, scale));
        return this.font.plainSubstrByWidth(text, maxWidth);
    }

    private void drawInfoLine(GuiGraphics gfx, String text, int x, int y, float scale) {
        String trimmed = trimInfo(text, scale);
        var pose = gfx.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1.0f);
        gfx.drawString(this.font, trimmed, 0, 0, 0x404040, false);
        pose.popPose();
    }

    private boolean isEnglish() {
        Minecraft mc = Minecraft.getInstance();
        var lang = mc.getLanguageManager().getSelected();
        return lang != null && lang.equalsIgnoreCase("en_us");
    }

    private float getInfoScale() {
        return isEnglish() ? 0.75f : 1.0f;
    }

    private float getTitleScale() {
        return isEnglish() ? 0.75f : 1.0f;
    }

    private static class StateImageButton extends ImageButton {
        private final ResourceLocation tex;
        private final int texW;
        private final int texH;
        private final int baseU;
        private final int baseV;
        private final int hoverU;
        private final int hoverV;
        private final int pressU;
        private final int pressV;
        private boolean pressedVisual = false;

        public StateImageButton(int x, int y, int w, int h, int baseU, int baseV, int hoverU, int hoverV, int pressU, int pressV,
                                ResourceLocation tex, int texW, int texH, OnPress onPress, Component tooltip) {
            super(x, y, w, h, baseU, baseV, 0, tex, texW, texH, onPress, tooltip);
            this.tex = tex;
            this.texW = texW;
            this.texH = texH;
            this.baseU = baseU;
            this.baseV = baseV;
            this.hoverU = hoverU;
            this.hoverV = hoverV;
            this.pressU = pressU;
            this.pressV = pressV;
        }

        @Override
        public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
            boolean hovered = this.isMouseOver(mouseX, mouseY);
            boolean pressed = pressedVisual || (hovered && GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS);
            int u = baseU;
            int v = baseV;
            if (pressed) {
                u = pressU;
                v = pressV;
            } else if (hovered) {
                u = hoverU;
                v = hoverV;
            }
            gfx.blit(tex, this.getX(), this.getY(), u, v, this.width, this.height, texW, texH);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            this.pressedVisual = true;
            super.onClick(mouseX, mouseY);
            this.setFocused(false);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            this.pressedVisual = false;
            super.onRelease(mouseX, mouseY);
            this.setFocused(false);
        }
    }
}