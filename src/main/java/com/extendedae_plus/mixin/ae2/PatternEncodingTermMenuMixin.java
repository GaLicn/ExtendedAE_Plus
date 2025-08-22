package com.extendedae_plus.mixin.ae2;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import com.extendedae_plus.mixin.ae2.accessor.MEStorageMenuAccessor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternEncodingTermMenu.class)
public abstract class PatternEncodingTermMenuMixin {

    // 防止重复执行
    @Unique
    private boolean eap$blankAutoFilled = false;

    @Shadow
    private RestrictedInputSlot blankPatternSlot;

    @Unique
    private void eap$tryFill(IPatternTerminalMenuHost host, Inventory ip) {
        try {
            var self = (PatternEncodingTermMenu) (Object) this;
            var player = ip.player;
            // 仅在服务器端执行
            if (ip.player.level().isClientSide()) {
                return;
            }
            // 必须可与网络交互
            var acc = (MEStorageMenuAccessor) (Object) ((MEStorageMenu) self);
            MEStorage storage = acc.getStorage();
            IEnergySource power = acc.getPowerSource();
            boolean hasPower = acc.getHasPower();
            boolean canInteract = storage != null && power != null && hasPower; // 等价于 canInteractWithGrid()
            if (!canInteract) {
                return;
            }
            if (storage == null || power == null) {
                return;
            }

            InternalInventory blankInv = host.getLogic().getBlankPatternInv();
            var current = blankInv.getStackInSlot(0);
            int limit = blankInv.getSlotLimit(0);
            int space = Math.max(0, limit - current.getCount());
            space = Math.min(space, AEItems.BLANK_PATTERN.asItem().getMaxStackSize());
            if (space <= 0) {
                return; // 已满，无需填充
            }

            AEKey blankKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
            long extracted = StorageHelper.poweredExtraction(power, storage, blankKey, space,
                    self.getActionSource());
            if (extracted <= 0) {
                return; // 网络无可用空白样板
            }

            int toInsert = (int) Math.min(extracted, space);
            var stackInSlot = this.blankPatternSlot.getItem();
            if (stackInSlot.isEmpty()) {
                this.blankPatternSlot.set(AEItems.BLANK_PATTERN.stack(toInsert));
            } else {
                stackInSlot.grow(toInsert);
                this.blankPatternSlot.set(stackInSlot);
            }
            long leftover = extracted - toInsert;
            if (leftover > 0) {
                StorageHelper.poweredInsert(power, storage, blankKey, leftover, self.getActionSource());
            }
        } catch (Throwable t) {
            // swallow errors to avoid noisy logs in production
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V",
            at = @At("TAIL"))
    private void eap$autoFillBlankPattern(MenuType<?> menuType, int id, Inventory ip,
                                          IPatternTerminalMenuHost host, boolean bindInventory,
                                          CallbackInfo ci) {
        eap$tryFill(host, ip);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;)V",
            at = @At("TAIL"))
    private void eap$autoFillCtor3(int id, Inventory ip, IPatternTerminalMenuHost host, CallbackInfo ci) {
        eap$tryFill(host, ip);
    }

    // 在首次 broadcastChanges 后再尝试一次，避免构造时网络未激活
    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void eap$retryFillAfterPower(CallbackInfo ci) {
        if (this.eap$blankAutoFilled) {
            return;
        }
        // 仅在服务器端执行
        var self = (PatternEncodingTermMenu) (Object) this;
        var player = self.getPlayerInventory().player;
        var acc = (MEStorageMenuAccessor) (Object) ((MEStorageMenu) self);
        MEStorage storage = acc.getStorage();
        IEnergySource power = acc.getPowerSource();
        boolean hasPower = acc.getHasPower();
        if (player.level().isClientSide()) {
            return;
        }
        boolean canInteract = storage != null && power != null && hasPower;
        if (!canInteract) {
            return;
        }
        // 通过 host 获取 blankPatternInv
        var host = ((IPatternTerminalMenuHost) self.getTarget());
        InternalInventory blankInv = host.getLogic().getBlankPatternInv();
        var current = blankInv.getStackInSlot(0);
        int limit = blankInv.getSlotLimit(0);
        int space = Math.max(0, limit - current.getCount());
        space = Math.min(space, AEItems.BLANK_PATTERN.asItem().getMaxStackSize());
        if (space <= 0) {
            this.eap$blankAutoFilled = true;
            return;
        }

        AEKey blankKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
        long extracted = StorageHelper.poweredExtraction(power, storage, blankKey, space,
                self.getActionSource());
        if (extracted <= 0) {
            return;
        }
        int toInsert = (int) Math.min(extracted, space);
        var stackInSlot = this.blankPatternSlot.getItem();
        if (stackInSlot.isEmpty()) {
            this.blankPatternSlot.set(AEItems.BLANK_PATTERN.stack(toInsert));
        } else {
            stackInSlot.grow(toInsert);
            this.blankPatternSlot.set(stackInSlot);
        }
        long leftover = extracted - toInsert;
        if (leftover > 0) {
            StorageHelper.poweredInsert(power, storage, blankKey, leftover, self.getActionSource());
        }
        this.eap$blankAutoFilled = true;
    }
}
