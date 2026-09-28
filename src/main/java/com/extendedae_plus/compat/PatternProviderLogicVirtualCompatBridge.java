package com.extendedae_plus.compat;

import appeng.api.networking.IManagedGridNode;

public interface PatternProviderLogicVirtualCompatBridge {
    boolean eap$compatIsVirtualCraftingEnabled();

    IManagedGridNode eap$compatGetMainNode();
}
