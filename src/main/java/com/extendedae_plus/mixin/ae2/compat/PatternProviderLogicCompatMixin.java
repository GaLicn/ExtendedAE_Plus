package com.extendedae_plus.mixin.ae2.compat;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.api.bridge.CompatUpgradeProvider;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.api.bridge.PatternProviderPageUnlockBridge;
import com.extendedae_plus.api.bridge.PatternProviderLogicSyncBridge;
import com.extendedae_plus.api.bridge.PatternProviderLogicUpgradeCompatBridge;
import com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.mixin.appflux.accessor.PatternProviderLogicAppfluxAccessor;
import com.extendedae_plus.util.ExtendedAELogger;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 样板供应器频道卡兼容实现：
 * - 未安装 appflux 时，提供升级槽并读取频道卡；
 * - 安装 appflux 时，优先从 appflux 提供的升级槽读取频道卡；
 * - 建立到无线主站的网格连接。
 */
@Mixin(value = PatternProviderLogic.class, priority = 900, remap = false)
public abstract class PatternProviderLogicCompatMixin implements CompatUpgradeProvider, InterfaceWirelessLinkBridge, PatternProviderLogicVirtualCompatBridge, PatternProviderLogicUpgradeCompatBridge, PatternProviderPageUnlockBridge {

    @Unique
    private static final String EAP$LEGACY_PATTERN_MIGRATION_TAG = "eap_legacy_pattern_migration";
    @Unique
    private static final String EAP$LEGACY_PATTERN_OVERFLOW_TAG = "overflow";
    @Unique
    private static final String EAP$LEGACY_PATTERN_UNLOCKED_SLOTS_TAG = "unlocked_slots";
    @Unique
    private static final String EAP$LEGACY_PATTERN_ORIGINAL_SLOT_TAG = "OriginalSlot";
    @Unique
    private static final int EAP$SLOTS_PER_PAGE = 36;

    @Unique
    private IUpgradeInventory eap$compatUpgrades = UpgradeInventories.empty();

    /** 频道卡连接统一由控制器管理。 */
    @Unique
    private ChannelCardConnectionController eap$channelController;

    @Unique
    private boolean eap$compatVirtualCraftingEnabled = false;

    // 旧倍率页升级后，保留前四页的可用状态，并将超额样板安全暂存到独立 NBT。
    @Unique
    private boolean eap$legacyPatternMigrationComplete;
    @Unique
    private int eap$legacyUnlockedPatternSlots;
    @Unique
    private final List<LegacyPatternStack> eap$legacyPatternOverflow = new ArrayList<>();

    @Final
    @Shadow
    private PatternProviderLogicHost host;

    @Final
    @Shadow
    private IManagedGridNode mainNode;

    @Final
    @Shadow
    private IActionSource actionSource;

    @Shadow
    public abstract InternalInventory getPatternInv();

    @Shadow
    public abstract void updatePatterns();

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$compatInit(IManagedGridNode mainNode, PatternProviderLogicHost host, int size, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                this.eap$compatUpgrades = UpgradeInventories.forMachine(
                        host.getTerminalIcon().getItem(),
                        UpgradeSlotCompat.getPatternProviderLocalUpgradeSlots(host),
                        this::eap$compatOnUpgradesChanged);
            } else if (!UpgradeSlotCompat.shouldEnableChannelCard()) {
                this.eap$compatUpgrades = UpgradeInventories.empty();
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 初始化兼容升级槽失败", t);
        }
    }

    @Unique
    private void eap$compatOnUpgradesChanged() {
        try {
            this.eap$compatNotifyHostChanged();
            this.eap$getChannelCardController().onUpgradesChanged();
            this.updatePatterns();
            if ((Object) this instanceof PatternProviderLogicSyncBridge bridge) {
                bridge.eap$markPatternSyncDirty();
            }
            this.eap$compatSyncVirtualCraftingState();
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 兼容升级变更处理失败", t);
        }
    }

    @Override
    public void eap$onCompatUpgradesChangedHook() {
        this.eap$compatOnUpgradesChanged();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$compatWrite(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                this.eap$compatUpgrades.writeToNBT(tag, "compat_upgrades", registries);
            }
            this.eap$writeLegacyPatternMigration(tag, registries);
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 保存兼容升级失败", t);
        }
    }

    @Inject(method = "readFromNBT", at = @At("HEAD"))
    private void eap$readLegacyPatternMigration(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        try {
            if (this.eap$isExtendedPatternProviderHost()) {
                this.eap$readLegacyPatternMigrationData(tag, registries);
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 迁移旧倍率页样板失败", t);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$compatRead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                this.eap$compatUpgrades.readFromNBT(tag, "compat_upgrades", registries);
            }
            // NBT 加载后由统一控制器恢复连接
            this.eap$getChannelCardController().onLoaded();
            this.eap$compatSyncVirtualCraftingState();
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 读取兼容升级失败", t);
        }
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$compatDrops(List<ItemStack> drops, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                for (var s : this.eap$compatUpgrades) {
                    if (!s.isEmpty()) drops.add(s);
                }
            }
            for (var legacyPattern : this.eap$legacyPatternOverflow) {
                if (!legacyPattern.stack().isEmpty()) {
                    drops.add(legacyPattern.stack().copy());
                }
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 掉落兼容升级失败", t);
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void eap$compatClear(CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                this.eap$compatUpgrades.clear();
            }
            this.eap$legacyPatternOverflow.clear();
            this.eap$legacyUnlockedPatternSlots = 0;
            this.eap$getChannelCardController().onUnloaded();
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 清理兼容升级失败", t);
        }
    }

    @Inject(method = "onMainNodeStateChanged()V", at = @At("TAIL"))
    private void eap$compatOnNodeChange(CallbackInfo ci) {
        try {
            this.eap$getChannelCardController().onNodeChanged();
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 主节点状态变更处理失败", t);
        }
    }

    @Override
    public void eap$updateWirelessLink() {
        this.eap$getChannelCardController().updateWirelessLink();
    }

    @Override
    public boolean eap$isWirelessConnected() {
        return this.eap$getChannelCardController().isConnected();
    }

    @Override
    public void eap$setClientWirelessState(boolean connected) {
        this.eap$getChannelCardController().setClientConnected(connected);
    }

    @Override
    public boolean eap$hasTickInitialized() {
        return this.eap$getChannelCardController().isInitialized();
    }

    @Override
    public void eap$setTickInitialized(boolean initialized) {
        this.eap$getChannelCardController().setInitialized(initialized);
    }

    @Override
    public void eap$handleDelayedInit() {
        this.eap$getChannelCardController().tick();
    }

    /**
     * 指示 PatternProviderLogic 的 Ticker 是否需要保持慢速 tick 以维持无线连接。
     * 不再用于轮询升级槽变更（现在由回调处理）
     */
    @Override
    public boolean eap$shouldKeepTicking() {
        try {
            // 仅在服务端保持tick
            if (this.host.getBlockEntity() == null || this.host.getBlockEntity().getLevel() == null || this.host.getBlockEntity().getLevel().isClientSide) {
                return false;
            }
            return this.eap$getChannelCardController().shouldKeepTicking();
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Unique
    private UUID eap$getFallbackOwner() {
        if (this.mainNode != null && this.mainNode.getNode() != null) {
            return this.mainNode.getNode().getOwningPlayerProfileId();
        }
        return null;
    }

    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        if (this.eap$channelController == null) {
            this.eap$channelController = new ChannelCardConnectionController(
                    this::eap$compatGetEffectiveUpgrades,
                    this::eap$getFallbackOwner,
                    () -> new GenericNodeEndpointImpl(
                            () -> this.host.getBlockEntity(),
                            () -> this.mainNode.getNode()),
                    this::eap$compatNotifyHostChanged,
                    () -> this.mainNode.ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node)),
                    () -> {
                        var blockEntity = this.host.getBlockEntity();
                        return blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide;
                    });
            if (this.host.getBlockEntity() != null) {
                ChannelCardConnectionController.register(this.host.getBlockEntity(), this.eap$channelController);
            }
        }
        return this.eap$channelController;
    }

    @Unique
    private void eap$compatNotifyHostChanged() {
        try {
            this.host.saveChanges();
            if (this.host.getBlockEntity() instanceof AEBaseBlockEntity blockEntity) {
                blockEntity.markForUpdate();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 安全地获取AppliedFlux提供的升级槽
     */
    @Unique
    private IUpgradeInventory eap$getAppliedFluxUpgrades() {
        if (!UpgradeSlotCompat.shouldListenToAppfluxUpgrades()) {
            return null;
        }

        try {
            if ((Object) this instanceof IUpgradeableObject upgradeableObject) {
                IUpgradeInventory upgrades = upgradeableObject.getUpgrades();
                if (upgrades != null && upgrades != this.eap$compatUpgrades) {
                    return upgrades;
                }
            }

            IUpgradeInventory upgrades = ((PatternProviderLogicAppfluxAccessor) (Object) this).eap$getAppfluxUpgrades();
            if (upgrades != null && upgrades != this.eap$compatUpgrades) {
                return upgrades;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public IUpgradeInventory eap$getCompatUpgrades() {
        return this.eap$compatUpgrades != null ? this.eap$compatUpgrades : UpgradeInventories.empty();
    }

    @Override
    public boolean eap$compatIsVirtualCraftingEnabled() {
        return this.eap$compatVirtualCraftingEnabled;
    }

    @Override
    public IManagedGridNode eap$compatGetMainNode() {
        return this.mainNode;
    }

    @Override
    public boolean eap$isExtendedPatternProviderHost() {
        return UpgradeSlotCompat.isExtendedPatternProviderHost(this.host);
    }

    @Override
    public int eap$getUnlockedPatternPages() {
        if (!this.eap$isExtendedPatternProviderHost()) {
            int size = this.getPatternInv() != null ? this.getPatternInv().size() : 0;
            return Math.max(1, (size + EAP$SLOTS_PER_PAGE - 1) / EAP$SLOTS_PER_PAGE);
        }

        return Math.max(1, (this.eap$getUnlockedPatternSlots() + EAP$SLOTS_PER_PAGE - 1) / EAP$SLOTS_PER_PAGE);
    }

    @Override
    public int eap$getUnlockedPatternSlots() {
        int size = this.getPatternInv() != null ? this.getPatternInv().size() : 0;
        if (!this.eap$isExtendedPatternProviderHost()) {
            return size;
        }

        int cardUnlockedSlots = UpgradeSlotCompat.getUnlockedExtendedPatternProviderSlots(this.eap$compatGetEffectiveUpgrades());
        return Math.min(size, Math.max(cardUnlockedSlots, this.eap$legacyUnlockedPatternSlots));
    }

    @Override
    public int eap$getLegacyUnlockedPatternSlots() {
        return this.eap$legacyUnlockedPatternSlots;
    }

    @Unique
    private void eap$readLegacyPatternMigrationData(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        this.eap$legacyPatternOverflow.clear();
        this.eap$legacyUnlockedPatternSlots = 0;
        this.eap$legacyPatternMigrationComplete = false;

        if (tag.contains(EAP$LEGACY_PATTERN_MIGRATION_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag migrationTag = tag.getCompound(EAP$LEGACY_PATTERN_MIGRATION_TAG);
            this.eap$legacyPatternMigrationComplete = true;
            this.eap$legacyUnlockedPatternSlots = Math.min(
                    UpgradeSlotCompat.getExtendedPatternProviderPatternCapacity(),
                    Math.max(0, migrationTag.getInt(EAP$LEGACY_PATTERN_UNLOCKED_SLOTS_TAG)));

            for (Tag entry : migrationTag.getList(EAP$LEGACY_PATTERN_OVERFLOW_TAG, Tag.TAG_COMPOUND)) {
                CompoundTag patternTag = (CompoundTag) entry;
                ItemStack stack = ItemStack.parseOptional(registries, patternTag);
                if (!stack.isEmpty()) {
                    this.eap$legacyPatternOverflow.add(new LegacyPatternStack(
                            patternTag.getInt(EAP$LEGACY_PATTERN_ORIGINAL_SLOT_TAG), stack));
                }
            }
            return;
        }

        int maxLegacySlot = -1;
        int maxSupportedSlot = UpgradeSlotCompat.getExtendedPatternProviderPatternCapacity() - 1;
        for (Tag entry : tag.getList(PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS, Tag.TAG_COMPOUND)) {
            CompoundTag patternTag = (CompoundTag) entry;
            int slot = patternTag.getInt("Slot");
            if (slot >= EAP$SLOTS_PER_PAGE) {
                maxLegacySlot = Math.max(maxLegacySlot, slot);
            }
            if (slot > maxSupportedSlot) {
                ItemStack stack = ItemStack.parseOptional(registries, patternTag);
                if (!stack.isEmpty()) {
                    this.eap$legacyPatternOverflow.add(new LegacyPatternStack(slot, stack));
                }
            }
        }

        if (maxLegacySlot >= EAP$SLOTS_PER_PAGE) {
            this.eap$legacyPatternMigrationComplete = true;
            int legacyPages = Math.min(
                    UpgradeSlotCompat.getExtendedPatternProviderTotalPages(),
                    maxLegacySlot / EAP$SLOTS_PER_PAGE + 1);
            this.eap$legacyUnlockedPatternSlots = legacyPages * EAP$SLOTS_PER_PAGE;
        }
    }

    @Unique
    private void eap$writeLegacyPatternMigration(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (!this.eap$legacyPatternMigrationComplete) {
            return;
        }

        CompoundTag migrationTag = new CompoundTag();
        migrationTag.putInt(EAP$LEGACY_PATTERN_UNLOCKED_SLOTS_TAG, this.eap$legacyUnlockedPatternSlots);
        ListTag overflowTag = new ListTag();
        for (var legacyPattern : this.eap$legacyPatternOverflow) {
            if (legacyPattern.stack().isEmpty()) {
                continue;
            }
            CompoundTag patternTag = (CompoundTag) legacyPattern.stack().save(registries, new CompoundTag());
            patternTag.putInt(EAP$LEGACY_PATTERN_ORIGINAL_SLOT_TAG, legacyPattern.originalSlot());
            overflowTag.add(patternTag);
        }
        migrationTag.put(EAP$LEGACY_PATTERN_OVERFLOW_TAG, overflowTag);
        tag.put(EAP$LEGACY_PATTERN_MIGRATION_TAG, migrationTag);
    }

    @Unique
    private record LegacyPatternStack(int originalSlot, ItemStack stack) {
    }

    @Unique
    private void eap$compatSyncVirtualCraftingState() {
        try {
            IUpgradeInventory upgrades = this.eap$compatGetEffectiveUpgrades();
            this.eap$compatVirtualCraftingEnabled = this.eap$compatInventoryContains(upgrades, ModItems.VIRTUAL_CRAFTING_CARD.get());
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 同步虚拟合成卡状态失败", t);
        }
    }

    @Unique
    private IUpgradeInventory eap$compatGetEffectiveUpgrades() {
        IUpgradeInventory upgrades;
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            upgrades = this.eap$compatUpgrades;
        } else {
            upgrades = this.eap$getAppliedFluxUpgrades();
        }

        if (upgrades == null || upgrades == UpgradeInventories.empty()) {
            if (upgrades != this.eap$compatUpgrades && this.eap$compatUpgrades != null) {
                upgrades = this.eap$compatUpgrades;
            } else {
                var fallback = this.eap$getAppliedFluxUpgrades();
                if (fallback != null) {
                    upgrades = fallback;
                }
            }
        }

        return upgrades;
    }

    @Unique
    private boolean eap$compatInventoryContains(IUpgradeInventory inventory, Item item) {
        if (inventory == null) {
            return false;
        }
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }
}
