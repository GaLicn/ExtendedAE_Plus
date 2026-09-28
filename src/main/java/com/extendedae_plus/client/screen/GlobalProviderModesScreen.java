package com.extendedae_plus.client.screen;

import de.mari_023.ae2wtlib.api.gui.Icon;
import com.extendedae_plus.menu.NetworkPatternControllerMenu;
import com.extendedae_plus.network.GlobalToggleProviderModesC2SPacket;
import com.extendedae_plus.network.SetGlobalScalingLimitC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.NotNull;

public class GlobalProviderModesScreen extends AbstractContainerScreen<NetworkPatternControllerMenu> {
    private static final Component CUSTOM_TITLE = Component.translatable("block.extendedae_plus.network_pattern_controller");
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("ae2", "textures/guis/background.png");

    private EditBox inputField;
    private static final int INPUT_WIDTH = 50;
    private static final int INPUT_HEIGHT = 14;

    // 布局常量
    private static final int IMAGE_WIDTH = 210;
    private static final int IMAGE_HEIGHT = 165;
    private static final int BTN_W = 62;
    private static final int BTN_H = 18;
    private static final int BTN_SPACING = 6;
    private static final int START_Y = 25;
    private static final int LABEL_INPUT_SPACING = 18;

    // AE2 风格按钮颜色
    private static final int BTN_BG = 0xFF3A3A3A;
    private static final int BTN_BG_HOVER = 0xFF4A4A4A;
    private static final int BTN_BORDER_LIGHT = 0xFFAAAAAA;
    private static final int BTN_BORDER_DARK = 0xFF555555;

    public GlobalProviderModesScreen(NetworkPatternControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.leftPos + this.imageWidth / 2;
        int row1Y = this.topPos + START_Y;
        int row2Y = row1Y + BTN_H + BTN_SPACING;
        int row3Y = row2Y + BTN_H + BTN_SPACING;

        // 第一行：三个切换按钮，居中
        int totalW3 = BTN_W * 3 + BTN_SPACING * 2;
        int row1X = centerX - totalW3 / 2;

        addRenderableWidget(new AEStyleButton(row1X, row1Y, BTN_W, BTN_H,
                Component.translatable("gui.extendedae_plus.global.toggle_blocking"), b ->
                ClientPacketDistributor.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Operation.TOGGLE,
                        GlobalToggleProviderModesC2SPacket.Operation.NOOP,
                        GlobalToggleProviderModesC2SPacket.Operation.NOOP,
                        this.menu.getBlockEntityPos()
                ))));

        addRenderableWidget(new AEStyleButton(row1X + BTN_W + BTN_SPACING, row1Y, BTN_W, BTN_H,
                Component.translatable("gui.extendedae_plus.global.toggle_adv_blocking"), b ->
                ClientPacketDistributor.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Operation.NOOP,
                        GlobalToggleProviderModesC2SPacket.Operation.TOGGLE,
                        GlobalToggleProviderModesC2SPacket.Operation.NOOP,
                        this.menu.getBlockEntityPos()
                ))));

        addRenderableWidget(new AEStyleButton(row1X + (BTN_W + BTN_SPACING) * 2, row1Y, BTN_W, BTN_H,
                Component.translatable("gui.extendedae_plus.global.toggle_smart_doubling"), b ->
                ClientPacketDistributor.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Operation.NOOP,
                        GlobalToggleProviderModesC2SPacket.Operation.NOOP,
                        GlobalToggleProviderModesC2SPacket.Operation.TOGGLE,
                        this.menu.getBlockEntityPos()
                ))));

        // 第二行：全开/全关按钮，居中
        int totalW2 = BTN_W * 2 + BTN_SPACING;
        int row2X = centerX - totalW2 / 2;

        addRenderableWidget(new AEStyleButton(row2X, row2Y, BTN_W, BTN_H,
                Component.translatable("gui.extendedae_plus.global.all_on"), b ->
                ClientPacketDistributor.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Operation.SET_TRUE,
                        GlobalToggleProviderModesC2SPacket.Operation.SET_TRUE,
                        GlobalToggleProviderModesC2SPacket.Operation.SET_TRUE,
                        this.menu.getBlockEntityPos()
                ))));

        addRenderableWidget(new AEStyleButton(row2X + BTN_W + BTN_SPACING, row2Y, BTN_W, BTN_H,
                Component.translatable("gui.extendedae_plus.global.all_off"), b ->
                ClientPacketDistributor.sendToServer(new GlobalToggleProviderModesC2SPacket(
                        GlobalToggleProviderModesC2SPacket.Operation.SET_FALSE,
                        GlobalToggleProviderModesC2SPacket.Operation.SET_FALSE,
                        GlobalToggleProviderModesC2SPacket.Operation.SET_FALSE,
                        this.menu.getBlockEntityPos()
                ))));

        // 输入框区域 - 整体居中布局
        int inputRowY = row3Y + LABEL_INPUT_SPACING;
        int labelWidth = this.font.width(Component.translatable("gui.extendedae_plus.global.supplier_doubling_limit"));
        int totalWidth = labelWidth + 8 + INPUT_WIDTH + 6 + 18; // 标签 + 间距 + 输入框 + 间距 + 按钮(18宽)
        int startX = centerX - totalWidth / 2;
        int labelX = startX;
        int inputX = startX + labelWidth + 8;
        int confirmX = inputX + INPUT_WIDTH + 6;

        inputField = createInputField(inputX, inputRowY + 2);
        this.addRenderableWidget(inputField);

        addRenderableWidget(new AEConfirmButton(confirmX, inputRowY,
                Component.translatable("gui.extendedae_plus.global.confirm_tooltip"), b -> {
            String value = inputField.getValue();
            // 数据校验：解析并发送有效数值
            try {
                String sValue = (value == null || value.isBlank()) ? "0" : value.replaceFirst("^0+(?=.)", "");
                int limit = Integer.parseInt(sValue);
                ClientPacketDistributor.sendToServer(new SetGlobalScalingLimitC2SPacket(limit, this.menu.getBlockEntityPos()));
            } catch (NumberFormatException ignored) {
                // 输入值无效，重置为0
                inputField.setValue("0");
            }
        }));
    }

    private EditBox createInputField(int x, int y) {
        EditBox input = new EditBox(this.font, x, y, INPUT_WIDTH, INPUT_HEIGHT, Component.empty());
        input.setMaxLength(6);
        input.setBordered(true);
        input.setValue("0");
        input.setTextColor(0xFFFFFFFF);
        // 添加数据校验响应器
        input.setResponder(s -> {
            try {
                String sValue = (s == null || s.isBlank()) ? "0" : s.replaceFirst("^0+(?=.)", "");
                if (!sValue.equals(s)) {
                    input.setValue(sValue);
                }
                Integer.parseInt(sValue); // 验证是否为有效整数
            } catch (Throwable ignored) {
                // 输入无效，重置为0
                input.setValue("0");
            }
        });
        return input;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                this.leftPos, this.topPos, 0, 0,
                this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTicks);

        // 绘制标题
        gfx.text(this.font, CUSTOM_TITLE, this.leftPos + 8, this.topPos + 6, 0xFF404040, false);

        int centerX = this.leftPos + this.imageWidth / 2;
        int row1Y = this.topPos + START_Y;
        int row2Y = row1Y + BTN_H + BTN_SPACING;
        int row3Y = row2Y + BTN_H + BTN_SPACING;
        int inputRowY = row3Y + LABEL_INPUT_SPACING;

        // 计算居中位置并绘制标签
        int labelWidth = this.font.width(Component.translatable("gui.extendedae_plus.global.supplier_doubling_limit"));
        int totalWidth = labelWidth + 8 + INPUT_WIDTH + 6 + 18;
        int startX = centerX - totalWidth / 2;
        int labelX = startX;
        gfx.text(this.font, Component.translatable("gui.extendedae_plus.global.supplier_doubling_limit"), labelX, inputRowY + 4, 0xFF000000, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        // 不绘制默认的玩家物品栏标题
    }

    // AE2 风格按钮
    private static class AEStyleButton extends Button {
        private static final int TEXT_COLOR = 0xFFFFFFFF;

        public AEStyleButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partial) {
            if (this.visible) {
                int bgColor = isHovered() && this.active ? BTN_BG_HOVER : BTN_BG;

                // 填充背景
                guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);

                // 绘制1像素边框 - 上亮下暗的立体效果
                guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, BTN_BORDER_LIGHT);
                guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, BTN_BORDER_LIGHT);
                guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, BTN_BORDER_DARK);
                guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, BTN_BORDER_DARK);

                guiGraphics.centeredText(Minecraft.getInstance().font, getMessage(),
                        getX() + width / 2, getY() + (height - 8) / 2, TEXT_COLOR);
            }
        }
    }

    // AE2 风格确认按钮（带 Tooltip）
    private static class AEConfirmButton extends Button {
        private final Component tooltip;

        public AEConfirmButton(int x, int y, Component tooltip, OnPress onPress) {
            super(x, y, 18, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            this.tooltip = tooltip;
        }

        @Override
        protected void extractContents(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partial) {
            if (this.visible) {
                guiGraphics.fill(getX(), getY(), getX() + width, getY() + height,
                        isHovered() ? BTN_BG_HOVER : BTN_BG);
                guiGraphics.centeredText(Minecraft.getInstance().font, Component.literal("✓"),
                        getX() + width / 2, getY() + 5, 0xFFFFFFFF);

                if (isHovered()) {
                    guiGraphics.setTooltipForNextFrame(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
                }
            }
        }
    }
}
