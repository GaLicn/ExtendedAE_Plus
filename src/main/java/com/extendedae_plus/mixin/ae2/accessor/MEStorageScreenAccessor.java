package com.extendedae_plus.mixin.ae2.accessor;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.widgets.AETextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MEStorageScreen.class, remap = false)
public interface MEStorageScreenAccessor {
    @Accessor("searchField")
    AETextField eap$getSearchField();

    @Invoker("setSearchText")
    void eap$setSearchText(String text);
}
