package com.extendedae_plus.mixin.autopattern;

import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingTreeNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CraftingTreeNode.class)
public interface CraftingTreeNodeAccessor {
    @Accessor("what")
    AEKey eap$getWhat();
}
