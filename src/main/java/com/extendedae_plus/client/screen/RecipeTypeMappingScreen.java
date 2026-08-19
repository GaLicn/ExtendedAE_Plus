package com.extendedae_plus.client.screen;

import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 可视化管理配方类型到供应器搜索词的自定义映射。 */
public class RecipeTypeMappingScreen extends Screen {
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final List<ExtendedAEPatternUploadUtil.RecipeTypeMapping> mappings = new ArrayList<>();
    private final List<ExtendedAEPatternUploadUtil.RecipeTypeMapping> filteredMappings = new ArrayList<>();

    private EditBox filterInput;
    private EditBox keyInput;
    private EditBox valueInput;
    private String selectedKey;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;
    private int page;
    private int pageSize = 6;
    private boolean needsRefresh;

    public RecipeTypeMappingScreen(Screen parent) {
        super(Component.translatable("extendedae_plus.screen.mapping_management.title"));
        this.parent = parent;
        this.reloadMappings(false);
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int panelWidth = Math.min(600, this.width - 20);
        int panelHeight = Math.min(390, this.height - 20);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int innerX = panelX + 12;
        int innerWidth = panelWidth - 24;

        this.pageSize = Math.max(1, (panelHeight - 166) / ROW_HEIGHT);
        this.page = Math.min(this.page, Math.max(0, (this.filteredMappings.size() - 1) / this.pageSize));

        this.filterInput = this.prepareInput(this.filterInput, innerX, panelY + 28, innerWidth, 20,
                "extendedae_plus.screen.mapping_management.filter");
        this.filterInput.setResponder(value -> {
            this.page = 0;
            this.applyFilter();
            this.needsRefresh = true;
        });
        this.addRenderableWidget(this.filterInput);

        int saveWidth = 76;
        int gap = 5;
        int inputWidth = Math.max(70, (innerWidth - saveWidth - gap * 2) / 2);
        int inputY = panelY + 64;
        this.keyInput = this.prepareInput(this.keyInput, innerX, inputY, inputWidth, 20,
                "extendedae_plus.screen.mapping_management.key");
        this.valueInput = this.prepareInput(this.valueInput, innerX + inputWidth + gap, inputY, inputWidth, 20,
                "extendedae_plus.screen.mapping_management.value");
        this.addRenderableWidget(this.keyInput);
        this.addRenderableWidget(this.valueInput);
        this.addRenderableWidget(Button.builder(Component.translatable("extendedae_plus.screen.mapping_management.save"),
                        button -> this.saveMapping())
                .bounds(innerX + inputWidth * 2 + gap * 2, inputY, saveWidth, 20)
                .build());

        int rowsY = panelY + 94;
        int start = this.page * this.pageSize;
        int end = Math.min(start + this.pageSize, this.filteredMappings.size());
        for (int index = start; index < end; index++) {
            ExtendedAEPatternUploadUtil.RecipeTypeMapping mapping = this.filteredMappings.get(index);
            String prefix = mapping.key().equals(this.selectedKey) ? "▶ " : "";
            String label = prefix + mapping.key() + "  →  " + mapping.value();
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> this.selectMapping(mapping))
                    .bounds(innerX, rowsY + (index - start) * ROW_HEIGHT, innerWidth, 20)
                    .build());
        }

        int navY = panelY + panelHeight - 54;
        Button previous = Button.builder(Component.literal("<"), button -> this.changePage(-1))
                .bounds(panelX + panelWidth / 2 - 62, navY, 24, 20)
                .build();
        Button next = Button.builder(Component.literal(">"), button -> this.changePage(1))
                .bounds(panelX + panelWidth / 2 + 38, navY, 24, 20)
                .build();
        previous.active = this.page > 0;
        next.active = (this.page + 1) * this.pageSize < this.filteredMappings.size();
        this.addRenderableWidget(previous);
        this.addRenderableWidget(next);

        int actionY = panelY + panelHeight - 28;
        int actionGap = 5;
        int actionWidth = (innerWidth - actionGap * 3) / 4;
        this.addRenderableWidget(Button.builder(Component.translatable("extendedae_plus.screen.mapping_management.new"),
                        button -> this.clearSelection())
                .bounds(innerX, actionY, actionWidth, 20)
                .build());
        Button delete = Button.builder(Component.translatable("extendedae_plus.screen.mapping_management.delete"),
                        button -> this.deleteSelectedMapping())
                .bounds(innerX + actionWidth + actionGap, actionY, actionWidth, 20)
                .build();
        delete.active = this.selectedKey != null;
        this.addRenderableWidget(delete);
        this.addRenderableWidget(Button.builder(Component.translatable("extendedae_plus.screen.mapping_management.reload"),
                        button -> this.reloadMappings(true))
                .bounds(innerX + (actionWidth + actionGap) * 2, actionY, actionWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> this.onClose())
                .bounds(innerX + (actionWidth + actionGap) * 3, actionY, actionWidth, 20)
                .build());
    }

    private EditBox prepareInput(EditBox input, int x, int y, int width, int height, String narrationKey) {
        if (input == null) {
            input = new EditBox(this.font, x, y, width, height, Component.translatable(narrationKey));
            input.setMaxLength(256);
        } else {
            input.setX(x);
            input.setY(y);
            input.setWidth(width);
        }
        return input;
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
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0101010);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, 0xFF808080);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 9, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.translatable("extendedae_plus.screen.mapping_management.key"),
                panelX + 12, panelY + 53, 0xFFB0B0B0, false);
        graphics.drawString(this.font, Component.translatable("extendedae_plus.screen.mapping_management.value"),
                panelX + 17 + this.keyInput.getWidth(), panelY + 53, 0xFFB0B0B0, false);

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
