package com.extendedae_plus.mixin.ae2.accessor;

import appeng.api.networking.IGrid;
import appeng.menu.me.crafting.CraftingCPUMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CraftingCPUMenu.class)
public interface CraftingCPUMenuAccessor {
    @Accessor("grid")
    IGrid getGrid();
}
