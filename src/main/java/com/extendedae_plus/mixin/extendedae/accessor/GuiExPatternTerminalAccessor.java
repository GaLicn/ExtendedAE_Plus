package com.extendedae_plus.mixin.extendedae.accessor;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import com.glodblock.github.extendedae.client.button.HighlightButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import com.google.common.collect.HashMultimap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;
import java.util.HashMap;

@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiExPatternTerminal.class, remap = false)
public interface GuiExPatternTerminalAccessor {
    @Accessor("scrollbar")
    Scrollbar getScrollbar();

    @Accessor("visibleRows")
    int getVisibleRows();

    @Accessor("rows")
    ArrayList<?> getRows();

    @Accessor("searchField")
    AETextField getSearchField();

    @Accessor("byGroup")
    HashMultimap<PatternContainerGroup, PatternContainerRecord> eap$getByGroup();

    @Accessor("infoMap")
    HashMap<Long, GuiExPatternTerminal.PatternProviderInfo> eap$getInfoMap();

    @Accessor("highlightBtns")
    HashMap<Integer, HighlightButton> eap$getHighlightButtons();

    @Invoker("refreshList")
    void eap$refreshList();

    @Invoker("resetScrollbar")
    void eap$resetScrollbar();
}
