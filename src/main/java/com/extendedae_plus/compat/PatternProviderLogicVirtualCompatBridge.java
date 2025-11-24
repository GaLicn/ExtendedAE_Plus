package com.extendedae_plus.compat;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;

public interface PatternProviderLogicVirtualCompatBridge {
    boolean eap$compatIsVirtualCraftingEnabled();

    IGrid eap$compatGetGrid();

    IManagedGridNode eap$compatGetMainNode();
}
