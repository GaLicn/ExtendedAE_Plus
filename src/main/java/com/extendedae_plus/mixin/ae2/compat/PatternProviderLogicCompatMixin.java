package com.extendedae_plus.mixin.ae2.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.inv.AppEngInternalInventory;
import com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import com.extendedae_plus.api.bridge.PatternProviderPageUnlockBridge;
import com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge;
import com.extendedae_plus.compat.DynamicSizeInternalInventory;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.mixin.ae2.accessor.CraftingCpuLogicAccessor;
import com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobAccessor;
import com.extendedae_plus.util.Logger;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * PatternProviderLogic的兼容性Mixin
 * 优先级设置为500，在appflux之前应用
 * 根据appflux是否存在来决定是否实现IUpgradeableObject接口
 */
@Mixin(value = PatternProviderLogic.class, priority = 500, remap = false)
public abstract class PatternProviderLogicCompatMixin implements IUpgradeableObject, IInterfaceWirelessLinkBridge,
        PatternProviderLogicVirtualCompatBridge, PatternProviderPageUnlockBridge {

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

    /** 频道卡连接状态统一由控制器持有。 */
    @Unique
    private ChannelCardConnectionController eap$channelController;

    @Final
    @Shadow
    private PatternProviderLogicHost host;

    @Final
    @Shadow
    private IManagedGridNode mainNode;

    @Final
    @Shadow
    private IActionSource actionSource;

    @Final
    @Shadow
    private AppEngInternalInventory patternInventory;

    @Unique
    private boolean eap$compatVirtualCraftingEnabled = false;

    // 旧倍率页升级后，保留前四页的可用状态，并将超额样板安全暂存到独立 NBT。
    @Unique
    private boolean eap$legacyPatternMigrationComplete;
    @Unique
    private int eap$legacyUnlockedPatternSlots;
    @Unique
    private final List<LegacyPatternStack> eap$legacyPatternOverflow = new ArrayList<>();

    @Shadow
    public abstract IGrid getGrid();

    @Shadow
    public abstract InternalInventory getPatternInv();

    @Shadow
    public abstract void updatePatterns();

    @Unique
    private InternalInventory eap$exposedPatternInventory;

    @Inject(method = "getPatternInv", at = @At("RETURN"), cancellable = true)
    private void eap$exposeDynamicPatternInventory(CallbackInfoReturnable<InternalInventory> cir) {
        if (!this.eap$isExtendedPatternProviderHost()) {
            return;
        }
        if (this.eap$exposedPatternInventory == null) {
            // 转发库存保持身份不变，size() 则随扩容卡实时变化。
            this.eap$exposedPatternInventory = new DynamicSizeInternalInventory(
                    this.patternInventory,
                    this::eap$getExposedPatternSlots);
        }
        cir.setReturnValue(this.eap$exposedPatternInventory);
    }

    @Unique
    private void eap$compatOnUpgradesChanged() {
        try {
            this.eap$compatNotifyHostChanged();
            eap$compatSyncVirtualCraftingState();
            this.updatePatterns();
            this.eap$getChannelCardController().onUpgradesChanged();
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("兼容性升级变更处理失败", e);
        }
    }

    @Unique
    private void eap$compatSyncVirtualCraftingState() {
        boolean hasCard = false;
        var inventory = eap$compatGetEffectiveUpgradeInventory();
        if (inventory != null) {
            for (ItemStack stack : inventory) {
                if (!stack.isEmpty() && stack.getItem() == ModItems.VIRTUAL_CRAFTING_CARD.get()) {
                    hasCard = true;
                    break;
                }
            }
        }
        eap$compatVirtualCraftingEnabled = hasCard;
    }

    @Unique
    private void eap$compatTryVirtualCompletion(IPatternDetails patternDetails) {
        if (!eap$compatVirtualCraftingEnabled) {
            return;
        }

        var be = this.host.getBlockEntity();
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide) {
            return;
        }

        var grid = getGrid();
        if (grid == null) {
            return;
        }

        var craftingService = grid.getCraftingService();
        if (craftingService == null) {
            return;
        }

        for (ICraftingCPU cpu : craftingService.getCpus()) {
            if (!cpu.isBusy()) {
                continue;
            }
            if (cpu instanceof CraftingCPUCluster cluster) {
                if (cluster.craftingLogic instanceof CraftingCpuLogicAccessor logicAccessor) {
                    var job = logicAccessor.extendedae_plus$getJob();
                    if (job instanceof ExecutingCraftingJobAccessor accessor) {
                        var tasks = accessor.extendedae_plus$getTasks();
                        var progress = tasks.get(patternDetails);
                        if (eap$compatShouldFinishWholeJob(tasks, progress)) {
                            cluster.updateOutput(null);
                            try {
                                logicAccessor.extendedae_plus$invokeFinishJob(true);
                            } catch (Throwable ignored) {
                                cluster.cancelJob();
                            }
                            break;
                        }
                    }
                }
            }
        }
    }
    
    @Unique
    private void eap$compatOnExternalUpgradesChanged() {
        try {
            eap$compatSyncVirtualCraftingState();
            this.updatePatterns();
            this.eap$getChannelCardController().onUpgradesChanged();
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("监听appflux升级变化失败", e);
        }
    }

    // 兼容较新的 appflux 升级变化回调命名
    @Inject(method = "af_onUpgradesChanged", at = @At("TAIL"), remap = false, require = 0)
    private void eap$onAppfluxUpgradesChanged(CallbackInfo ci) {
        eap$compatOnExternalUpgradesChanged();
    }

    // 兼容旧命名，避免不同 appflux 版本导致注入失效
    @Inject(method = "af_$onUpgradesChanged", at = @At("TAIL"), remap = false, require = 0)
    private void eap$onLegacyAppfluxUpgradesChanged(CallbackInfo ci) {
        eap$compatOnExternalUpgradesChanged();
    }

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$compatInitUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        try {

            boolean upgradeSlots = UpgradeSlotCompat.shouldManageLocalUpgradeInventory();
            boolean channelCard = UpgradeSlotCompat.shouldEnableChannelCard();

            if (upgradeSlots) {
                this.eap$compatUpgrades = UpgradeInventories.forMachine(
                        host.getTerminalIcon().getItem(),
                        UpgradeSlotCompat.getPatternProviderLocalUpgradeSlots(host),
                        this::eap$compatOnUpgradesChanged
                );
            } else if (!channelCard) {
                this.eap$compatUpgrades = UpgradeInventories.empty();
            }
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("兼容性升级初始化失败", e);
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$compatSaveUpgrades(CompoundTag tag, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                this.eap$compatUpgrades.writeToNBT(tag, "compat_upgrades");
            }
            this.eap$writeLegacyPatternMigration(tag);
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("兼容性升级保存失败", e);
        }
    }

    @Inject(method = "readFromNBT", at = @At("HEAD"))
    private void eap$readLegacyPatternMigration(CompoundTag tag, CallbackInfo ci) {
        try {
            if (this.eap$isExtendedPatternProviderHost()) {
                this.eap$readLegacyPatternMigrationData(tag);
            }
        } catch (Throwable t) {
            Logger.EAP$LOGGER.error("迁移旧倍率页样板失败", t);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$compatLoadUpgrades(CompoundTag tag, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                this.eap$compatUpgrades.readFromNBT(tag, "compat_upgrades");
            }

            this.eap$getChannelCardController().onLoaded();
            eap$compatSyncVirtualCraftingState();
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("兼容性升级加载失败", e);
        }
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$compatDropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                for (var stack : this.eap$compatUpgrades) {
                    if (!stack.isEmpty()) {
                        drops.add(stack);
                    }
                }
            }
            for (var legacyPattern : this.eap$legacyPatternOverflow) {
                if (!legacyPattern.stack().isEmpty()) {
                    drops.add(legacyPattern.stack().copy());
                }
            }
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("兼容性升级掉落失败", e);
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void eap$compatClearUpgrades(CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                this.eap$compatUpgrades.clear();
            }
            this.eap$legacyPatternOverflow.clear();
            this.eap$legacyUnlockedPatternSlots = 0;
            if (UpgradeSlotCompat.shouldEnableChannelCard()) {
                eap$compatVirtualCraftingEnabled = false;
            }
            this.eap$getChannelCardController().onUnloaded();
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("兼容性升级清理失败", e);
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            return this.eap$compatUpgrades != null ? this.eap$compatUpgrades : UpgradeInventories.empty();
        } else {
            return eap$compatGetEffectiveUpgradeInventory();
        }
    }

    @Override
    public boolean eap$compatIsVirtualCraftingEnabled() {
        return this.eap$compatVirtualCraftingEnabled;
    }

    @Override
    public IGrid eap$compatGetGrid() {
        return this.getGrid();
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
            int size = this.patternInventory.size();
            return Math.max(1, (size + EAP$SLOTS_PER_PAGE - 1) / EAP$SLOTS_PER_PAGE);
        }

        return Math.max(1, (this.eap$getUnlockedPatternSlots() + EAP$SLOTS_PER_PAGE - 1) / EAP$SLOTS_PER_PAGE);
    }

    @Override
    public int eap$getUnlockedPatternSlots() {
        int size = this.patternInventory.size();
        if (!this.eap$isExtendedPatternProviderHost()) {
            return size;
        }

        return this.eap$getExposedPatternSlots();
    }

    @Unique
    private int eap$getExposedPatternSlots() {
        int cardUnlockedSlots = UpgradeSlotCompat.getUnlockedExtendedPatternProviderSlots(
                this.eap$compatGetEffectiveUpgradeInventory());
        return Math.min(this.patternInventory.size(), Math.max(cardUnlockedSlots, this.eap$legacyUnlockedPatternSlots));
    }

    @Override
    public int eap$getLegacyUnlockedPatternSlots() {
        return this.eap$legacyUnlockedPatternSlots;
    }

    @Unique
    private void eap$readLegacyPatternMigrationData(CompoundTag tag) {
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
                ItemStack stack = ItemStack.of(patternTag);
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
                ItemStack stack = ItemStack.of(patternTag);
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
    private void eap$writeLegacyPatternMigration(CompoundTag tag) {
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
            CompoundTag patternTag = legacyPattern.stack().save(new CompoundTag());
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
    private boolean eap$compatShouldFinishWholeJob(
            Map<IPatternDetails, com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobTaskProgressAccessor> tasks,
            com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobTaskProgressAccessor matchedProgress) {
        if (matchedProgress == null || matchedProgress.extendedae_plus$getValue() > 1) {
            return false;
        }

        for (var entry : tasks.entrySet()) {
            var taskProgress = entry.getValue();
            if (taskProgress == null) {
                continue;
            }

            long remaining = taskProgress.extendedae_plus$getValue();
            if (taskProgress == matchedProgress) {
                remaining -= 1;
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void eap$compatOnPushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, CallbackInfoReturnable<Boolean> cir) {
        eap$compatTryVirtualCompletion(patternDetails);
    }

    @Unique
    private IUpgradeInventory eap$compatGetEffectiveUpgradeInventory() {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            return this.eap$compatUpgrades != null ? this.eap$compatUpgrades : UpgradeInventories.empty();
        }

        if (!UpgradeSlotCompat.shouldListenToAppfluxUpgrades()) {
            return this.eap$compatUpgrades != null ? this.eap$compatUpgrades : UpgradeInventories.empty();
        }

        IUpgradeInventory inventory = UpgradeSlotCompat.getPatternProviderAppfluxUpgrades(this);
        if (inventory != null) {
            return inventory;
        }

        return this.eap$compatUpgrades != null ? this.eap$compatUpgrades : UpgradeInventories.empty();
    }

    @Inject(method = "onMainNodeStateChanged", at = @At("TAIL"))
    private void eap$compatOnMainNodeStateChangedTail(CallbackInfo ci) {
        this.eap$getChannelCardController().onNodeChanged();
    }

    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        if (this.eap$channelController == null) {
            this.eap$channelController = new ChannelCardConnectionController(
                    this::eap$compatGetEffectiveUpgradeInventory,
                    this::eap$getFallbackOwner,
                    () -> new GenericNodeEndpointImpl(this.host::getBlockEntity, this.mainNode::getNode),
                    this::eap$compatNotifyHostChanged,
                    this::eap$wakeNode,
                    this::eap$isClientSide);
            if (this.host.getBlockEntity() != null) {
                ChannelCardConnectionController.register(this.host.getBlockEntity(), this.eap$channelController);
            }
        }
        return this.eap$channelController;
    }

    @Unique
    private boolean eap$isClientSide() {
        var blockEntity = this.host.getBlockEntity();
        return blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide;
    }

    @Unique
    private void eap$wakeNode() {
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Unique
    private java.util.UUID eap$getFallbackOwner() {
        if (this.mainNode != null && this.mainNode.getNode() != null) {
            return this.mainNode.getNode().getOwningPlayerProfileId();
        }
        return null;
    }

    @Unique
    private void eap$compatNotifyHostChanged() {
        try {
            this.host.saveChanges();
        } catch (Throwable ignored) {
        }

        try {
            if (this.host.getBlockEntity() instanceof AEBaseBlockEntity blockEntity) {
                blockEntity.markForUpdate();
            }
        } catch (Throwable ignored) {
        }
    }
}
