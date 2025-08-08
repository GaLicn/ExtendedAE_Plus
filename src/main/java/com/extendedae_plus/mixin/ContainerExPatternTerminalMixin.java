package com.extendedae_plus.mixin;

import appeng.menu.guisync.GuiSync;
import com.glodblock.github.extendedae.container.ContainerExPatternTerminal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerExPatternTerminal.class)
public abstract class ContainerExPatternTerminalMixin {

    @GuiSync(11452)
    @Unique
    public boolean hidePatternSlots = false;

    @Unique
    public boolean isHidePatternSlots() {
        return this.hidePatternSlots;
    }

    @Unique
    public void setHidePatternSlots(boolean hide) {
        this.hidePatternSlots = hide;
    }

    @Unique
    public void toggleHidePatternSlots() {
        this.hidePatternSlots = !this.hidePatternSlots;
    }
} 