package com.extendedae_plus.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

/**
 * 样板供应器选择按钮
 * 用于在样板访问终端中标记选中的样板供应器
 */
public class PatternProviderSelectionButton extends IconButton {
    
    private boolean selected = false;
    private final long serverId;
    private final Runnable onSelectionChanged;
    
    public PatternProviderSelectionButton(long serverId, Runnable onSelectionChanged) {
        super(button -> {
            PatternProviderSelectionButton selectionButton = (PatternProviderSelectionButton) button;
            selectionButton.toggleSelection();
        });
        this.serverId = serverId;
        this.onSelectionChanged = onSelectionChanged;
        this.setHalfSize(true); // 使用小尺寸按钮
        this.setMessage(Component.translatable("gui.extendedae_plus.pattern_provider.select"));
    }
    
    public void toggleSelection() {
        this.selected = !this.selected;
        if (this.onSelectionChanged != null) {
            this.onSelectionChanged.run();
        }
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    public boolean isSelected() {
        return this.selected;
    }
    
    public long getServerId() {
        return this.serverId;
    }
    
    @Override
    protected Icon getIcon() {
        return this.selected ? Icon.VALID : Icon.INVALID;
    }
    
    @Override
    public Component getMessage() {
        return this.selected 
            ? Component.translatable("gui.extendedae_plus.pattern_provider.selected")
            : Component.translatable("gui.extendedae_plus.pattern_provider.unselected");
    }
}
