package com.extendedae_plus.mixin.extendedae.container;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.AppEngInternalInventory;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.glodblock.github.extendedae.container.ContainerAssemblerMatrix;

@Mixin(targets = "com.glodblock.github.extendedae.container.ContainerAssemblerMatrix$PatternSlotTracker")
public abstract class ContainerAssemblerMatrixPatternSlotTrackerMixin {

    @Shadow
    @Final
    private InternalInventory server;

    @Shadow
    @Final
    @Mutable
    private InternalInventory client;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void extendedae_plus$resizeClientInventory(TileAssemblerMatrixPattern host, CallbackInfo ci) {
        this.client = new AppEngInternalInventory(this.server.size());
    }
}

