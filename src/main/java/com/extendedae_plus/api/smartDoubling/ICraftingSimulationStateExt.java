package com.extendedae_plus.api.smartDoubling;

import appeng.crafting.CraftingTreeProcess;

public interface ICraftingSimulationStateExt {
    void setSourceProcess(CraftingTreeProcess process);
    CraftingTreeProcess getSourceProcess();
}