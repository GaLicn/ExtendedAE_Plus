package com.extendedae_plus.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

/**
 * GroupHeader选择按钮
 * 用于在样板访问终端中标记GroupHeaderRow的Ischooiceable状态
 */
public class GroupHeaderChoiceButton extends IconButton {
    
    private boolean isChoiceable = false;
    private final String groupName;
    private final Runnable onChoiceChanged;
    
    public GroupHeaderChoiceButton(String groupName, Runnable onChoiceChanged) {
        super(button -> {
            GroupHeaderChoiceButton choiceButton = (GroupHeaderChoiceButton) button;
            choiceButton.toggleChoice();
        });
        this.groupName = groupName;
        this.onChoiceChanged = onChoiceChanged;
        this.setHalfSize(true); // 使用小尺寸按钮
        this.setMessage(Component.translatable("gui.extendedae_plus.group_header.choice"));
    }
    
    public void toggleChoice() {
        this.isChoiceable = !this.isChoiceable;
        if (this.onChoiceChanged != null) {
            this.onChoiceChanged.run();
        }
    }
    
    public void setChoiceable(boolean isChoiceable) {
        this.isChoiceable = isChoiceable;
    }
    
    public boolean isChoiceable() {
        return this.isChoiceable;
    }
    
    public String getGroupName() {
        return this.groupName;
    }
    
    @Override
    protected Icon getIcon() {
        return this.isChoiceable ? Icon.WHITELIST : Icon.BLACKLIST;
    }
    
    @Override
    public Component getMessage() {
        return this.isChoiceable 
            ? Component.translatable("gui.extendedae_plus.group_header.choiceable")
            : Component.translatable("gui.extendedae_plus.group_header.not_choiceable");
    }
}
