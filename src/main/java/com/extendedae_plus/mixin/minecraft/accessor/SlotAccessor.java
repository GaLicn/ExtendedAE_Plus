package com.extendedae_plus.mixin.minecraft.accessor;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 为分页布局提供修改 Minecraft 槽位坐标的访问器。 */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void eap$setX(int x);

    @Mutable
    @Accessor("y")
    void eap$setY(int y);
}
