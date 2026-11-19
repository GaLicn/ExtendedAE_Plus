package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.menu.me.common.GridInventoryEntry;
import com.extendedae_plus.api.client.IRepoUpdateFreezeAccess;
import com.google.common.collect.BiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(value = Repo.class, remap = false)
public abstract class RepoUpdateFreezeMixin implements IRepoUpdateFreezeAccess {
    @Shadow
    @Final
    private BiMap<Long, GridInventoryEntry> entries;

    @Shadow
    @Final
    private ArrayList<GridInventoryEntry> view;

    @Shadow
    @Final
    private ArrayList<GridInventoryEntry> pinnedRow;

    @Unique
    private boolean eap$updatesFrozen;

    @Unique
    private static boolean eap$updatesFrozenPreference;

    @Unique
    private final Set<AEKey> eap$visibleKeysAtFreezeStart = new HashSet<>();

    @Unique
    private final Set<AEKey> eap$sortedKeysDuringFreeze = new HashSet<>();

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void eap$restoreFreezePreference(IScrollSource src, ISortSource sortSrc, CallbackInfo ci) {
        if (!eap$updatesFrozenPreference) {
            return;
        }

        this.eap$updatesFrozen = true;
        this.eap$visibleKeysAtFreezeStart.addAll(this.eap$getVisibleKeys());
        ((Repo) (Object) this).setPaused(true);
    }

    @Override
    @Unique
    public boolean eap$isUpdatesFrozen() {
        return this.eap$updatesFrozen;
    }

    @Override
    @Unique
    public void eap$setUpdatesFrozen(boolean frozen) {
        if (this.eap$updatesFrozen == frozen) {
            return;
        }

        this.eap$updatesFrozen = frozen;
        eap$updatesFrozenPreference = frozen;
        if (frozen) {
            this.eap$visibleKeysAtFreezeStart.clear();
            this.eap$visibleKeysAtFreezeStart.addAll(this.eap$getVisibleKeys());
            this.eap$sortedKeysDuringFreeze.clear();
        } else {
            this.eap$visibleKeysAtFreezeStart.clear();
            this.eap$sortedKeysDuringFreeze.clear();
            return;
        }

        ((Repo) (Object) this).setPaused(frozen);
    }

    @Inject(method = "handleUpdate", at = @At("TAIL"), remap = false)
    private void eap$sortOnceForNewKeys(boolean fullUpdate, List<GridInventoryEntry> entries,
            CallbackInfo ci) {
        if (!this.eap$updatesFrozen) {
            return;
        }

        boolean hasNewVisibleKey = false;
        for (GridInventoryEntry update : entries) {
            GridInventoryEntry current = this.entries.get(update.getSerial());
            if (current == null) {
                continue;
            }

            AEKey key = current.getWhat();
            if (key != null && !this.eap$visibleKeysAtFreezeStart.contains(key)
                    && this.eap$sortedKeysDuringFreeze.add(key)) {
                hasNewVisibleKey = true;
            }
        }

        if (hasNewVisibleKey) {
            Repo repo = (Repo) (Object) this;
            repo.setPaused(false);
            repo.setPaused(true);
        }
    }

    @Unique
    private Set<AEKey> eap$getVisibleKeys() {
        Set<AEKey> keys = new HashSet<>();
        for (GridInventoryEntry entry : this.view) {
            if (entry.getWhat() != null) {
                keys.add(entry.getWhat());
            }
        }
        for (GridInventoryEntry entry : this.pinnedRow) {
            if (entry.getWhat() != null) {
                keys.add(entry.getWhat());
            }
        }
        return keys;
    }
}
