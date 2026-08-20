package com.extendedae_plus.client.screen;

import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 可视化管理配方类型到供应器搜索词的自定义映射。 */
public class RecipeTypeMappingScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    // AE2 文本框背景纹理宽度固定为 128，输入框不能直接拉伸到整栏。
    private static final int AE_TEXT_FIELD_WIDTH = 128;
    // 使用 AE2 原生文本框的 12px 高度。
    private static final int AE_SEARCH_FIELD_HEIGHT = 12;

    private final Screen parent;
    private final ScreenStyle aeStyle;
    private final List<ExtendedAEPatternUploadUtil.RecipeTypeMapping> mappings = new ArrayList<>();
    private final List<ExtendedAEPatternUploadUtil.RecipeTypeMapping> filteredMappings = new ArrayList<>();

    private AETextField filterInput;
    private AETextField keyInput;
    private AETextField valueInput;
    private String selectedKey;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;
    private int page;
    private int pageSize = 6;
    private boolean needsRefresh;

    public RecipeTypeMappingScreen(Screen parent) {
        super(Component.translatable("extendedae_plus.screen.mapping_management.title"));
        this.parent = parent;
        this.aeStyle = StyleManager.loadStyleDoc("/screens/common/common.json");
        this.reloadMappings(false);
    }

    @Override
    protected void init() {
        boolean filterWasFocused = this.filterInput != null && this.filterInput.isFocused();
        boolean keyWasFocused = this.keyInput != null && this.keyInput.isFocused();
        boolean valueWasFocused = this.valueInput != null && this.valueInput.isFocused();
        this.clearWidgets();

        int panelWidth = Math.min(600, this.width - 20);
        int panelHeight = Math.min(390, this.height - 20);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int innerX = panelX + 12;
        int innerWidth = panelWidth - 24;

        this.pageSize = Math.max(1, (panelHeight - 166) / ROW_HEIGHT);
        this.page = Math.min(this.page, Math.max(0, (this.filteredMappings.size() - 1) / this.pageSize));

        int filterX = panelX + (panelWidth - AE_TEXT_FIELD_WIDTH) / 2;
        this.filterInput = this.prepareInput(this.filterInput, filterX, panelY + 28,
                AE_TEXT_FIELD_WIDTH, AE_SEARCH_FIELD_HEIGHT,
                "extendedae_plus.screen.mapping_management.filter");
        this.filterInput.setResponder(value -> {
            this.page = 0;
            this.applyFilter();
            this.needsRefresh = true;
        });
        this.addRenderableWidget(this.filterInput);
        if (filterWasFocused) {
            this.setFocused(this.filterInput);
        }

        int saveWidth = 76;
        int gap = 5;
        int inputWidth = Math.min(AE_TEXT_FIELD_WIDTH,
                Math.max(70, (innerWidth - saveWidth - gap * 2) / 2));
        int formWidth = inputWidth * 2 + saveWidth + gap * 2;
        int formX = panelX + (panelWidth - formWidth) / 2;
        int inputY = panelY + 52;
        this.keyInput = this.prepareInput(this.keyInput, formX, inputY, inputWidth, AE_SEARCH_FIELD_HEIGHT,
                "extendedae_plus.screen.mapping_management.key");
        this.valueInput = this.prepareInput(this.valueInput, formX + inputWidth + gap, inputY, inputWidth,
                AE_SEARCH_FIELD_HEIGHT,
                "extendedae_plus.screen.mapping_management.value");
        this.addRenderableWidget(this.keyInput);
        this.addRenderableWidget(this.valueInput);
        if (keyWasFocused) {
            this.setFocused(this.keyInput);
        } else if (valueWasFocused) {
            this.setFocused(this.valueInput);
        }
        this.addRenderableWidget(new AE2Button(
                formX + inputWidth * 2 + gap * 2, inputY, saveWidth, 20,
                Component.translatable("extendedae_plus.screen.mapping_management.save"),
                button -> this.saveMapping()));

        int rowsY = panelY + 82;
        int start = this.page * this.pageSize;
        int end = Math.min(start + this.pageSize, this.filteredMappings.size());
        for (int index = start; index < end; index++) {
            ExtendedAEPatternUploadUtil.RecipeTypeMapping mapping = this.filteredMappings.get(index);
            String prefix = mapping.key().equals(this.selectedKey) ? "▶ " : "";
            String label = prefix + mapping.key() + "  →  " + mapping.value();
            this.addRenderableWidget(new AE2Button(
                    innerX, rowsY + (index - start) * ROW_HEIGHT, innerWidth, 20,
                    Component.literal(label), button -> this.selectMapping(mapping)));
        }

        int navY = panelY + panelHeight - 54;
        int navOffset = 90;
        Button previous = new AE2Button(
                panelX + panelWidth / 2 - navOffset - 12, navY, 24, 20,
                Component.literal("<"), button -> this.changePage(-1));
        Button next = new AE2Button(
                panelX + panelWidth / 2 + navOffset - 12, navY, 24, 20,
                Component.literal(">"), button -> this.changePage(1));
        previous.active = this.page > 0;
        next.active = (this.page + 1) * this.pageSize < this.filteredMappings.size();
        this.addRenderableWidget(previous);
        this.addRenderableWidget(next);

        int actionY = panelY + panelHeight - 28;
        int actionGap = 5;
        int actionWidth = (innerWidth - actionGap * 3) / 4;
        this.addRenderableWidget(new AE2Button(
                innerX, actionY, actionWidth, 20,
                Component.translatable("extendedae_plus.screen.mapping_management.new"),
                button -> this.clearSelection()));
        Button delete = new AE2Button(
                innerX + actionWidth + actionGap, actionY, actionWidth, 20,
                Component.translatable("extendedae_plus.screen.mapping_management.delete"),
                button -> this.deleteSelectedMapping());
        delete.active = this.selectedKey != null;
        this.addRenderableWidget(delete);
        this.addRenderableWidget(new AE2Button(
                innerX + (actionWidth + actionGap) * 2, actionY, actionWidth, 20,
                Component.translatable("extendedae_plus.screen.mapping_management.reload"),
                button -> this.reloadMappings(true)));
        this.addRenderableWidget(new AE2Button(
                innerX + (actionWidth + actionGap) * 3, actionY, actionWidth, 20,
                Component.translatable("gui.back"), button -> this.onClose()));
    }

    private AETextField prepareInput(AETextField input, int x, int y, int width, int height, String narrationKey) {
        String value = input == null ? "" : input.getValue();
        var result = new AETextField(this.aeStyle, this.font, x, y, width, height);
        // 与 AE2 终端一致，避免原版 EditBox 的黑色背景覆盖 AE2 纹理。
        result.setBordered(false);
        result.setMaxLength(256);
        result.setValue(value);
        result.setPlaceholder(Component.translatable(narrationKey));
        return result;
    }

    private void reloadMappings(boolean showStatus) {
        ExtendedAEPatternUploadUtil.loadRecipeTypeNames();
        this.mappings.clear();
        this.mappings.addAll(ExtendedAEPatternUploadUtil.getRecipeTypeMappings());
        this.applyFilter();
        this.needsRefresh = true;
        if (showStatus) {
            this.setStatus("extendedae_plus.screen.mapping_management.reloaded", 0xFF55FF55);
        }
    }

    private void applyFilter() {
        String query = this.filterInput == null ? "" : this.filterInput.getValue().trim().toLowerCase(Locale.ROOT);
        this.filteredMappings.clear();
        for (ExtendedAEPatternUploadUtil.RecipeTypeMapping mapping : this.mappings) {
            if (query.isEmpty()
                    || mapping.key().toLowerCase(Locale.ROOT).contains(query)
                    || mapping.value().toLowerCase(Locale.ROOT).contains(query)) {
                this.filteredMappings.add(mapping);
            }
        }
    }

    private void selectMapping(ExtendedAEPatternUploadUtil.RecipeTypeMapping mapping) {
        this.selectedKey = mapping.key();
        this.keyInput.setValue(mapping.key());
        this.valueInput.setValue(mapping.value());
        this.needsRefresh = true;
    }

    private void clearSelection() {
        this.selectedKey = null;
        this.keyInput.setValue("");
        this.valueInput.setValue("");
        this.setFocused(this.keyInput);
        this.needsRefresh = true;
    }

    private void saveMapping() {
        String key = this.keyInput.getValue().trim();
        String value = this.valueInput.getValue().trim();
        if (key.isEmpty() || value.isEmpty()) {
            this.setStatus("extendedae_plus.screen.mapping_management.required", 0xFFFF5555);
            return;
        }

        String previousKey = this.selectedKey;
        if (!ExtendedAEPatternUploadUtil.addOrUpdateRecipeTypeMapping(key, value)) {
            this.setStatus("extendedae_plus.screen.mapping_management.save_failed", 0xFFFF5555);
            return;
        }
        if (previousKey != null && !previousKey.equalsIgnoreCase(key)) {
            ExtendedAEPatternUploadUtil.removeRecipeTypeMapping(previousKey);
        }

        this.selectedKey = key;
        this.reloadMappings(false);
        this.selectedKey = this.mappings.stream()
                .map(ExtendedAEPatternUploadUtil.RecipeTypeMapping::key)
                .filter(savedKey -> savedKey.equalsIgnoreCase(key))
                .findFirst()
                .orElse(key);
        this.setStatus("extendedae_plus.screen.mapping_management.saved", 0xFF55FF55);
    }

    private void deleteSelectedMapping() {
        if (this.selectedKey == null) {
            return;
        }
        if (!ExtendedAEPatternUploadUtil.removeRecipeTypeMapping(this.selectedKey)) {
            this.setStatus("extendedae_plus.screen.mapping_management.delete_failed", 0xFFFF5555);
            return;
        }

        this.selectedKey = null;
        this.keyInput.setValue("");
        this.valueInput.setValue("");
        this.reloadMappings(false);
        this.setStatus("extendedae_plus.screen.mapping_management.deleted", 0xFF55FF55);
    }

    private void changePage(int delta) {
        int nextPage = this.page + delta;
        if (nextPage < 0 || nextPage * this.pageSize >= this.filteredMappings.size()) {
            return;
        }
        this.page = nextPage;
        this.needsRefresh = true;
    }

    private void setStatus(String translationKey, int color) {
        this.status = Component.translatable(translationKey);
        this.statusColor = color;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 显式转发 AETextField 点击，确保点击纹理边缘也能获得焦点。
        if (this.filterInput != null && this.filterInput.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.filterInput);
            return true;
        }
        if (this.keyInput != null && this.keyInput.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.keyInput);
            return true;
        }
        if (this.valueInput != null && this.valueInput.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.valueInput);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.filterInput != null && this.filterInput.isFocused()
                && keyCode == 65 && hasControlDown()) {
            // Ctrl+A 只修改选区，不触发列表重建，避免全选状态被刷新清掉。
            this.filterInput.selectAll();
            return true;
        }
        if (this.filterInput != null && this.filterInput.isFocused()
                && this.filterInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.keyInput != null && this.keyInput.isFocused()
                && this.keyInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.valueInput != null && this.valueInput.isFocused()
                && this.valueInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.filterInput != null && this.filterInput.isFocused()
                && this.filterInput.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (this.keyInput != null && this.keyInput.isFocused()
                && this.keyInput.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (this.valueInput != null && this.valueInput.isFocused()
                && this.valueInput.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.needsRefresh) {
            this.needsRefresh = false;
            this.init();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(600, this.width - 20);
        int panelHeight = Math.min(390, this.height - 20);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        // 仅使用原版模糊背景，避免额外绘制不透明面板遮住世界背景。
        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 9, 0xFFFFFFFF);
        Component pageText = Component.translatable("extendedae_plus.screen.mapping_management.page",
                this.filteredMappings.isEmpty() ? 0 : this.page + 1,
                Math.max(1, (this.filteredMappings.size() + this.pageSize - 1) / this.pageSize),
                this.filteredMappings.size());
        graphics.drawCenteredString(this.font, pageText, this.width / 2, panelY + panelHeight - 48, 0xFFB0B0B0);
        if (!this.status.getString().isEmpty()) {
            graphics.drawString(this.font, this.status, panelX + 12, panelY + panelHeight - 66, this.statusColor, false);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
