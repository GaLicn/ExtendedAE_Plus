package com.extendedae_plus.client;

import com.extendedae_plus.config.ModConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModConfigScreen extends Screen {
    private final Screen parent;

    // 输入控件
    private EditBox pageMultiplierBox;
    private EditBox wirelessMaxRangeBox;
    private CycleButton<Boolean> crossDimToggle;
    private CycleButton<Boolean> providerRoundRobinToggle;
    private EditBox smartScalingMaxMulBox;
    private CycleButton<Boolean> showEncoderToggle;
    private CycleButton<Boolean> patternTerminalShowSlotsToggle;

    public ModConfigScreen(Screen parent) {
        super(Component.translatable("screen.extendedae_plus.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 6 + 24; // 起始高度，整体更上方
        int row = 0;
        int rowHeight = 26;
        int boxWidth = 150;
        // 左右两列：左侧标签起点，右侧输入控件起点
        int leftX = centerX - 170;
        int rightX = centerX + 20;

        // pageMultiplier: Int 1-64
        pageMultiplierBox = new EditBox(this.font, rightX, y + row * rowHeight, boxWidth, 20, Component.translatable("config.extendedae_plus.pageMultiplier"));
        pageMultiplierBox.setValue(String.valueOf(ModConfigs.PAGE_MULTIPLIER.get()));
        pageMultiplierBox.setFilter(s -> s.matches("\\d*") && parseIntOrDefault(s, 1) >= 1 && parseIntOrDefault(s, 64) <= 64);
        this.addRenderableWidget(pageMultiplierBox);
        row++;

        // wirelessMaxRange: Double 1-4096
        wirelessMaxRangeBox = new EditBox(this.font, rightX, y + row * rowHeight, boxWidth, 20, Component.translatable("config.extendedae_plus.wirelessMaxRange"));
        wirelessMaxRangeBox.setValue(String.valueOf(ModConfigs.WIRELESS_MAX_RANGE.get()));
        wirelessMaxRangeBox.setFilter(s -> s.isEmpty() || s.matches("\\d*(\\.\\d*)?"));
        this.addRenderableWidget(wirelessMaxRangeBox);
        row++;

        // cross dim toggle
        crossDimToggle = this.addRenderableWidget(createToggle(rightX, y + row * rowHeight, boxWidth, 20, ModConfigs.WIRELESS_CROSS_DIM_ENABLE.get()));
        row++;

        // provider round-robin toggle (smart doubling)
        providerRoundRobinToggle = this.addRenderableWidget(createToggle(rightX, y + row * rowHeight, boxWidth, 20, ModConfigs.PROVIDER_ROUND_ROBIN_ENABLE.get()));
        row++;

        // smartScalingMaxMultiplier: Int 0-1048576 (0 means unlimited)
        smartScalingMaxMulBox = new EditBox(this.font, rightX, y + row * rowHeight, boxWidth, 20, Component.translatable("config.extendedae_plus.smartScalingMaxMultiplier"));
        smartScalingMaxMulBox.setValue(String.valueOf(ModConfigs.SMART_SCALING_MAX_MULTIPLIER.get()));
        smartScalingMaxMulBox.setFilter(s -> s.matches("\\d*") && parseIntOrDefault(s, 0) >= 0 && parseIntOrDefault(s, 1048576) <= 1048576);
        this.addRenderableWidget(smartScalingMaxMulBox);
        row++;

        // show encoder pattern player toggle
        showEncoderToggle = this.addRenderableWidget(createToggle(rightX, y + row * rowHeight, boxWidth, 20, ModConfigs.SHOW_ENCOD_PATTERN_PLAYER.get()));
        row++;

        // pattern terminal show slots default toggle
        patternTerminalShowSlotsToggle = this.addRenderableWidget(createToggle(rightX, y + row * rowHeight, boxWidth, 20, ModConfigs.PATTERN_TERMINAL_SHOW_SLOTS_DEFAULT.get()));
        row++;

        // 按钮：保存、返回
        int btnW = 100;
        int gap = 8;
        int buttonsY = y + row * rowHeight + 18;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> saveAndClose())
                .pos(centerX - btnW - gap/2, buttonsY)
                .size(btnW, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .pos(centerX + gap/2, buttonsY)
                .size(btnW, 20)
                .build());
    }

    private void saveAndClose() {
        // 读取与校验
        int pageMul = clamp(parseIntOrDefault(pageMultiplierBox.getValue(), ModConfigs.PAGE_MULTIPLIER.get()), 1, 64);
        double maxRange = clamp(parseDoubleOrDefault(wirelessMaxRangeBox.getValue(), ModConfigs.WIRELESS_MAX_RANGE.get()), 1.0, 4096.0);
        boolean crossDim = crossDimToggle.getValue();
        boolean providerRoundRobin = providerRoundRobinToggle.getValue();
        int smartMaxMul = clamp(parseIntOrDefault(smartScalingMaxMulBox.getValue(), ModConfigs.SMART_SCALING_MAX_MULTIPLIER.get()), 0, 1048576);
        boolean showEncoder = showEncoderToggle.getValue();
        boolean patternShowSlots = patternTerminalShowSlotsToggle.getValue();

        // 应用到 Forge 配置值
        ModConfigs.PAGE_MULTIPLIER.set(pageMul);
        ModConfigs.WIRELESS_MAX_RANGE.set(maxRange);
        ModConfigs.WIRELESS_CROSS_DIM_ENABLE.set(crossDim);
        ModConfigs.PROVIDER_ROUND_ROBIN_ENABLE.set(providerRoundRobin);
        ModConfigs.SMART_SCALING_MAX_MULTIPLIER.set(smartMaxMul);
        ModConfigs.SHOW_ENCOD_PATTERN_PLAYER.set(showEncoder);
        ModConfigs.PATTERN_TERMINAL_SHOW_SLOTS_DEFAULT.set(patternShowSlots);

        // Forge 会在合适的时机写回到配置文件；部分改动可能需要重启游戏或世界才完全生效
        onClose();
    }

    // Helper to create a boolean on/off CycleButton which shows localized on/off text
    private CycleButton<Boolean> createToggle(int x, int y, int width, int height, boolean initial) {
        CycleButton<Boolean> btn = CycleButton.onOffBuilder(initial)
                .create(x, y, width, height, Component.empty(), (b, v) -> b.setMessage(Component.translatable(v ? "config.extendedae_plus.state_on" : "config.extendedae_plus.state_off")));
        btn.setMessage(Component.translatable(initial ? "config.extendedae_plus.state_on" : "config.extendedae_plus.state_off"));
        return btn;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int y = this.height / 6 + 24;
        int rowHeight = 26;
        int labelColor = 0xFFFFFF;
        int leftX = centerX - 170; // 标签左列位置

        // 标题
        g.drawCenteredString(this.font, this.title, centerX, y - 28, 0xFFFFFF);

        // 每行标签
        g.drawString(this.font, Component.translatable("config.extendedae_plus.pageMultiplier_with_range"), leftX, y + 0 * rowHeight + 6, labelColor, false);
        g.drawString(this.font, Component.translatable("config.extendedae_plus.wirelessMaxRange_with_range"), leftX, y + 1 * rowHeight + 6, labelColor, false);
        g.drawString(this.font, Component.translatable("config.extendedae_plus.wirelessCrossDimEnable"), leftX, y + 2 * rowHeight + 6, labelColor, false);
        g.drawString(this.font, Component.translatable("config.extendedae_plus.providerRoundRobinEnable"), leftX, y + 3 * rowHeight + 6, labelColor, false);
        g.drawString(this.font, Component.translatable("config.extendedae_plus.smartScalingMaxMultiplier_with_range"), leftX, y + 4 * rowHeight + 6, labelColor, false);
        g.drawString(this.font, Component.translatable("config.extendedae_plus.showEncoderPatternPlayer"), leftX, y + 5 * rowHeight + 6, labelColor, false);
        g.drawString(this.font, Component.translatable("config.extendedae_plus.patternTerminalShowSlotsDefault"), leftX, y + 6 * rowHeight + 6, labelColor, false);
    }

    private static int parseIntOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static double parseDoubleOrDefault(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
