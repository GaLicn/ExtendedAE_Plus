package com.extendedae_plus.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;

@Mixin(MEStorageMenu.class)
public interface MEStorageMenuAccessor {
    @Accessor("storage")
    @Nullable
    MEStorage getStorage();

    @Accessor("powerSource")
    @Nullable
    IEnergySource getPowerSource();

    @Accessor("hasPower")
    boolean getHasPower();
}
