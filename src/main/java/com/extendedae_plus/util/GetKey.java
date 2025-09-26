package com.extendedae_plus.util;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public class GetKey {
    private final String keyType;
    private String mainDescription = ExtendedAEPlus.MODID;
    private String additionalKey = ".";
    private Object[] args;

    public static final String CTAB = "ctab";
    public static final String GUI = "gui";
    public static final String MESSAGE = "message";
    public static final String CONFIG = "config";
    public static final String TOOLTIP = "tooltip";

    public GetKey() {
        this.keyType = "";
    }

    public GetKey(String keyType) {
        this.keyType = keyType.toLowerCase() + '.';
    }

    public GetKey(DeferredItem<?> item) {
        this.keyType = "";
        this.mainDescription = item.asItem().getDescriptionId().toLowerCase();
    }

    public GetKey(DeferredHolder<FluidType, ?> fluid) {
        this.keyType = "";
        this.mainDescription = fluid.get().getDescriptionId().toLowerCase();
        this.additionalKey = "";
    }

    public GetKey item(ItemStack item) {
        this.mainDescription = item.getDescriptionId().toLowerCase();
        return this;
    }

    public GetKey item(Item item) {
        this.mainDescription = item.getDescriptionId().toLowerCase();
        return this;
    }

    public GetKey item(DeferredItem<?> item) {
        this.mainDescription = item.asItem().getDescriptionId().toLowerCase();
        return this;
    }

    public GetKey addStr(String additionalKey) {
        if (!this.additionalKey.endsWith("."))
            this.additionalKey += ".";
        this.additionalKey += additionalKey;
        return this;
    }

    public GetKey obj(Object... args) {
        this.args = args;
        return this;
    }

    public String buildRaw() {
        return keyType + mainDescription + additionalKey;
    }

    public Component build() {
        if (args == null) return Component.translatable(keyType + mainDescription + additionalKey);
        return Component.translatable(keyType + mainDescription + additionalKey, args);
    }

    public String getDescriptionID(ItemStack itemStack) {
        return "item.fish_things." + BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();
    }
}
