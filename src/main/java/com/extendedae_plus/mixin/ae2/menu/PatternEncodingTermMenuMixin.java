package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import com.extendedae_plus.client.PatternEncodingTermMenuMixinHelper;
import com.extendedae_plus.config.EAEPConfig;
import com.extendedae_plus.mixin.ae2.accessor.MEStorageMenuAccessor;
import com.extendedae_plus.network.C2SPacketEncodeFinished;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(PatternEncodingTermMenu.class)
public abstract class PatternEncodingTermMenuMixin implements PatternEncodingTermMenuMixinHelper {
    // 防止重复执行
    @Unique
    private boolean eap$blankAutoFilled = false;
    @Shadow @Final
    private RestrictedInputSlot blankPatternSlot;
    @Shadow
    @Final
    private RestrictedInputSlot encodedPatternSlot;
    @Unique
    public boolean eaep$isCtrlPressed = false;

    @Unique
    public void eaep$setCtrlPressed(boolean press) {
        eaep$isCtrlPressed = press;
    }

    @Unique
    private void eap$tryFill(IPatternTerminalMenuHost host, Inventory ip) {
        try {
            var self = (PatternEncodingTermMenu) (Object) this;
            // 仅在服务器端执行
            if (ip.player.level().isClientSide()) {
                return;
            }
            // 必须可与网络交互
            var acc = (MEStorageMenuAccessor) self;
            MEStorage storage = acc.getStorage();
            IEnergySource power = acc.getEnergySource();
            boolean canInteract = storage != null && power != null && self.getLinkStatus().connected();
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
            space = Math.min(space, AEItems.BLANK_PATTERN.stack(1).getMaxStackSize());
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
        var acc = (MEStorageMenuAccessor) self;
        MEStorage storage = acc.getStorage();
        IEnergySource power = acc.getEnergySource();
        var player = self.getPlayerInventory().player;
        if (player.level().isClientSide()) {
            return;
        }
        boolean canInteract = storage != null && power != null && self.getLinkStatus().connected();
        if (!canInteract) {
            return;
        }
        // 通过 host 获取 blankPatternInv
        var host = ((IPatternTerminalMenuHost) self.getTarget());
        InternalInventory blankInv = host.getLogic().getBlankPatternInv();
        var current = blankInv.getStackInSlot(0);
        int limit = blankInv.getSlotLimit(0);
        int space = Math.max(0, limit - current.getCount());
        space = Math.min(space, AEItems.BLANK_PATTERN.stack(1).getMaxStackSize());
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

    @Inject(method = "encode", at = @At("TAIL"))
    private void eaep$onEncode(CallbackInfo ci) {
        if (EAEPConfig.INDEPENDENT_UPLOADING_BUTTON.getAsBoolean()) return;
        var self = (PatternEncodingTermMenu) (Object) this;
        if (self.isClientSide()) return;
        if (!eaep$isCtrlPressed) return;
        eaep$isCtrlPressed = false;
        ItemStack pattern = this.encodedPatternSlot.getItem();
        if (pattern == null || !PatternDetailsHelper.isEncodedPattern(pattern)) return;
        Objects.requireNonNull(self.getPlayer().getServer()).execute(() -> {
            try {
                if (self.getMode() == EncodingMode.PROCESSING)
                    PacketDistributor.sendToPlayer((ServerPlayer) self.getPlayer(), C2SPacketEncodeFinished.INSTANCE);
                else ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToMatrix((ServerPlayer) self.getPlayer(), self);
            } catch (Throwable ignored) {}
        });
    }
}
