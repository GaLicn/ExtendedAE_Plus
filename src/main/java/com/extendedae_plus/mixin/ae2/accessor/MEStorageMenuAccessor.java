package com.extendedae_plus.mixin.ae2.accessor;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.menu.me.common.MEStorageMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MEStorageMenu.class)
public interface MEStorageMenuAccessor {
    @Accessor(value = "storage", remap = false)
    @Nullable
    MEStorage getStorage();

    @Accessor(value = "powerSource", remap = false)
    @Nullable
    IEnergySource getPowerSource();

    @Accessor(value = "hasPower", remap = false)
    boolean getHasPower();

    // Access client-side config manager mirror used for syncing settings
    @Accessor(value = "clientCM", remap = false)
    IConfigManager getClientCM();

    // Access server-side config manager
    @Accessor(value = "serverCM", remap = false)
    @Nullable
    IConfigManager getServerCM();
}
