package com.extendedae_plus.content.ae2;

import appeng.api.config.Settings;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import com.extendedae_plus.api.bridge.PatternProviderLogicSyncBridge;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class MirrorPatternProviderBlockEntity extends PatternProviderBlockEntity {
    private static final String TAG_MASTER = "mirrorMaster";
    private static final String TAG_MASTER_DIMENSION = "dimension";
    private static final String TAG_MASTER_POS = "pos";
    private static final String TAG_MASTER_SIDE = "side";
    private static final int FAST_SYNC_INTERVAL = 2;
    private static final int STABLE_SYNC_INTERVAL = 20;
    private static final int UNLOADED_MASTER_RETRY_INTERVAL = 40;
    private static final int AE2_PATTERN_SLOTS = 9;
    private static final int EXTENDED_PATTERN_PROVIDER_BASE_SLOTS = 36;
    private static final InternalInventory DISABLED_PATTERN_INVENTORY = new AppEngInternalInventory(0);
    private static final long UNKNOWN_PATTERN_SYNC_VERSION = Long.MIN_VALUE;

    @Nullable
    private ResourceKey<Level> masterDimension;

    @Nullable
    private BlockPos masterPos;

    @Nullable
    private Direction masterSide;

    private long nextSyncTick = Long.MIN_VALUE;
    private long lastSyncedPatternVersion = UNKNOWN_PATTERN_SYNC_VERSION;
    private boolean needsUnboundPatternCleanup;

    public record MasterLocation(ResourceKey<Level> dimension, BlockPos pos, @Nullable Direction side) {
        public MasterLocation {
            pos = pos.immutable();
        }

        public GlobalPos asGlobalPos() {
            return GlobalPos.of(this.dimension, this.pos);
        }
    }

    private record ResolvedMaster(MasterLocation location, PatternProviderLogicHost host) {
    }

    public MirrorPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MIRROR_PATTERN_PROVIDER_BE.get(), pos, blockState);
    }

    @Override
    public boolean isVisibleInTerminal() {
        return false;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return DISABLED_PATTERN_INVENTORY;
    }

    @Override
    protected PatternProviderLogic createLogic() {
        return new MirrorLogic(this.getMainNode(), this, getMirrorPatternSlotCapacity());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MirrorPatternProviderBlockEntity blockEntity) {
        if (level instanceof ServerLevel serverLevel) {
            blockEntity.serverTick(serverLevel);
        }
    }

    private void serverTick(ServerLevel level) {
        if (level.getGameTime() < this.nextSyncTick) {
            return;
        }

        this.nextSyncTick = level.getGameTime() + this.syncBoundMaster();
    }

    @Override
    public void onReady() {
        super.onReady();
        if (this.getLevel() instanceof ServerLevel serverLevel) {
            this.scheduleImmediateSync();
            this.nextSyncTick = serverLevel.getGameTime() + this.syncBoundMaster();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);

        if (this.masterDimension != null && this.masterPos != null) {
            var masterTag = new CompoundTag();
            masterTag.putString(TAG_MASTER_DIMENSION, this.masterDimension.location().toString());
            masterTag.putLong(TAG_MASTER_POS, this.masterPos.asLong());
            if (this.masterSide != null) {
                masterTag.putString(TAG_MASTER_SIDE, this.masterSide.getSerializedName());
            }
            data.put(TAG_MASTER, masterTag);
        } else {
            data.remove(TAG_MASTER);
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);

        this.masterDimension = null;
        this.masterPos = null;
        this.masterSide = null;
        this.scheduleImmediateSync();
        this.invalidatePatternSyncState();
        this.needsUnboundPatternCleanup = true;
        if (data.contains(TAG_MASTER, Tag.TAG_COMPOUND)) {
            var masterTag = data.getCompound(TAG_MASTER);
            if (masterTag.contains(TAG_MASTER_DIMENSION, Tag.TAG_STRING) && masterTag.contains(TAG_MASTER_POS, Tag.TAG_LONG)) {
                this.masterDimension = ResourceKey.create(
                        Registries.DIMENSION,
                        ResourceLocation.parse(masterTag.getString(TAG_MASTER_DIMENSION)));
                this.masterPos = BlockPos.of(masterTag.getLong(TAG_MASTER_POS));
                if (masterTag.contains(TAG_MASTER_SIDE, Tag.TAG_STRING)) {
                    this.masterSide = Direction.byName(masterTag.getString(TAG_MASTER_SIDE));
                }
                this.needsUnboundPatternCleanup = false;
            }
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        var patternInventory = this.getPatternInventory();
        var mirroredPatterns = patternInventory.toItemContainerContents();

        patternInventory.fromItemContainerContents(ItemContainerContents.EMPTY);
        this.getLogic().updatePatterns();
        this.getLogic().addDrops(drops);

        patternInventory.fromItemContainerContents(mirroredPatterns);
        this.getLogic().updatePatterns();
    }

    @Override
    public void clearContent() {
        this.getLogic().clearContent();
    }

    public boolean tryBindToAdjacentMaster() {
        if (!(this.getLevel() instanceof ServerLevel)) {
            return false;
        }

        var master = this.findAdjacentMaster();
        if (master == null) {
            return false;
        }

        this.syncFromMaster(master);
        return true;
    }

    public boolean bindToMaster(GlobalPos master) {
        return this.bindToMaster(new MasterLocation(master.dimension(), master.pos(), null));
    }

    public boolean bindToMaster(MasterLocation master) {
        if (!(this.getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }

        if (master.pos().equals(this.getBlockPos()) && master.dimension().equals(serverLevel.dimension())) {
            return false;
        }

        var resolvedMaster = this.resolveMaster(serverLevel, master);
        if (resolvedMaster != null) {
            this.syncFromMaster(resolvedMaster);
            return true;
        }

        var masterLevel = serverLevel.getServer().getLevel(master.dimension());
        if (masterLevel != null && masterLevel.hasChunkAt(master.pos())) {
            return false;
        }

        var changed = this.setBoundMaster(master);
        if (changed) {
            this.flushStateChanges();
        }
        this.scheduleImmediateSync();
        return true;
    }

    @Nullable
    public PatternProviderLogicHost getMaster() {
        if (!(this.getLevel() instanceof ServerLevel serverLevel)) {
            return null;
        }

        var master = this.resolveBoundMaster(serverLevel);
        return master != null ? master.host() : null;
    }

    public Component createBoundMessage() {
        if (this.masterPos != null) {
            return this.appendMasterSide(Component.translatable(
                    "extendedae_plus.message.mirror_pattern_provider.bound",
                    this.masterPos.getX(),
                    this.masterPos.getY(),
                    this.masterPos.getZ()));
        }

        return Component.translatable("extendedae_plus.message.mirror_pattern_provider.missing_master");
    }

    public Component getStatusMessage() {
        if (this.masterPos != null) {
            return this.appendMasterSide(Component.translatable(
                    "extendedae_plus.message.mirror_pattern_provider.following",
                    this.masterPos.getX(),
                    this.masterPos.getY(),
                    this.masterPos.getZ()));
        }

        return Component.translatable("extendedae_plus.message.mirror_pattern_provider.missing_master");
    }

    public boolean hasMasterBinding() {
        return this.masterDimension != null && this.masterPos != null;
    }

    public boolean unbindFromMaster() {
        if (!(this.getLevel() instanceof ServerLevel)) {
            return false;
        }

        if (!this.hasMasterBinding()) {
            return false;
        }

        var changed = this.clearMasterBinding(true);
        if (changed) {
            this.flushStateChanges();
        }
        this.scheduleImmediateSync();
        return true;
    }

    public Component createUnboundMessage() {
        return Component.translatable("extendedae_plus.message.mirror_pattern_provider.unbound");
    }

    private int syncBoundMaster() {
        if (this.masterDimension == null || this.masterPos == null) {
            if (this.needsUnboundPatternCleanup) {
                this.needsUnboundPatternCleanup = false;
                if (this.clearMirroredPatterns()) {
                    this.flushStateChanges();
                }
            }
            return UNLOADED_MASTER_RETRY_INTERVAL;
        }

        if (!(this.getLevel() instanceof ServerLevel serverLevel)) {
            return UNLOADED_MASTER_RETRY_INTERVAL;
        }

        var master = this.resolveBoundMaster(serverLevel);
        if (master != null) {
            return this.syncFromMaster(master) ? FAST_SYNC_INTERVAL : STABLE_SYNC_INTERVAL;
        }

        if (this.shouldClearBrokenBinding()) {
            if (this.clearMasterBinding(true)) {
                this.flushStateChanges();
                return FAST_SYNC_INTERVAL;
            }
            return STABLE_SYNC_INTERVAL;
        }

        return UNLOADED_MASTER_RETRY_INTERVAL;
    }

    private boolean shouldClearBrokenBinding() {
        if (this.masterDimension == null || this.masterPos == null) {
            return false;
        }

        if (!(this.getLevel() instanceof ServerLevel serverLevel)) {
            return true;
        }

        var target = this.getBoundMasterLocation();
        if (target == null) {
            return false;
        }

        var masterLevel = target.dimension() == serverLevel.dimension()
                ? serverLevel
                : serverLevel.getServer().getLevel(target.dimension());
        if (masterLevel == null || !masterLevel.hasChunkAt(target.pos())) {
            return false;
        }

        return this.resolveMaster(serverLevel, target) == null;
    }

    private boolean clearMasterBinding(boolean clearMirroredPatterns) {
        var hadBinding = this.masterDimension != null || this.masterPos != null;

        this.masterDimension = null;
        this.masterPos = null;
        this.masterSide = null;
        this.invalidatePatternSyncState();
        this.needsUnboundPatternCleanup = false;

        var changed = hadBinding;
        if (clearMirroredPatterns) {
            changed |= this.clearMirroredPatterns();
            changed |= this.resetMirroredSettingsToInitialState();
        }

        return changed;
    }

    private boolean setBoundMaster(MasterLocation master) {
        var changed = !Objects.equals(this.masterDimension, master.dimension())
                || !Objects.equals(this.masterPos, master.pos())
                || !Objects.equals(this.masterSide, master.side());

        this.masterDimension = master.dimension();
        this.masterPos = master.pos();
        this.masterSide = master.side();
        this.needsUnboundPatternCleanup = false;
        if (changed) {
            this.invalidatePatternSyncState();
        }
        return changed;
    }

    @Nullable
    private ResolvedMaster findAdjacentMaster() {
        var level = this.getLevel();
        if (level == null) {
            return null;
        }

        for (var direction : Direction.values()) {
            var adjacent = level.getBlockEntity(this.getBlockPos().relative(direction));
            var master = resolveMaster(adjacent, null);
            if (master == null) {
                master = resolveMaster(adjacent, direction.getOpposite());
            }
            if (master != null) {
                return master;
            }
        }

        return null;
    }

    private boolean syncFromMaster(ResolvedMaster master) {
        var bindingChanged = this.setBoundMaster(master.location());
        var changed = bindingChanged;
        changed |= this.syncMirroredSettings(master.host());
        changed |= this.syncMirroredPatterns(master.host(), bindingChanged);

        if (changed) {
            this.flushStateChanges();
        }

        return changed;
    }

    private boolean syncMirroredSettings(PatternProviderLogicHost master) {
        if (!this.hasDifferentMirroredSettings(master)) {
            return false;
        }

        var builder = DataComponentMap.builder();
        var customName = getCustomName(master);
        if (customName != null) {
            builder.set(AEComponents.EXPORTED_CUSTOM_NAME, customName);
        }
        builder.set(AEComponents.EXPORTED_SETTINGS, master.getConfigManager().exportSettings());
        builder.set(AEComponents.EXPORTED_PRIORITY, master.getPriority());
        if (supportsPushDirectionState(master)) {
            builder.set(AEComponents.EXPORTED_PUSH_DIRECTION, getPushDirection(master));
        }
        this.importSettings(SettingsFrom.MEMORY_CARD, builder.build(), null);
        return true;
    }

    private boolean resetMirroredSettingsToInitialState() {
        var defaultState = this.getBlockState().getBlock().defaultBlockState();
        var defaultMirror = new MirrorPatternProviderBlockEntity(this.getBlockPos(), defaultState);
        return this.syncMirroredSettings(defaultMirror);
    }

    private boolean hasDifferentMirroredSettings(PatternProviderLogicHost master) {
        var mirrorLogic = this.getLogic();
        var masterLogic = master.getLogic();

        return !Objects.equals(this.getCustomName(), getCustomName(master))
                || mirrorLogic.getPriority() != masterLogic.getPriority()
                || !Objects.equals(
                        mirrorLogic.getConfigManager().exportSettings(),
                        masterLogic.getConfigManager().exportSettings())
                || supportsPushDirectionState(master)
                && this.getBlockState().getValue(PatternProviderBlock.PUSH_DIRECTION) != getPushDirection(master);
    }

    private boolean syncMirroredPatterns(PatternProviderLogicHost master, boolean forceSync) {
        var masterPatternVersion = getPatternSyncVersion(master);
        if (!forceSync && masterPatternVersion != UNKNOWN_PATTERN_SYNC_VERSION
                && masterPatternVersion == this.lastSyncedPatternVersion) {
            return false;
        }

        var mirrorInventory = this.getPatternInventory();
        var masterInventory = asPatternInventory(master.getLogic().getPatternInv());
        var mirrorSize = mirrorInventory.size();
        var masterSize = masterInventory.size();
        var changed = false;

        for (int slot = 0; slot < mirrorSize; slot++) {
            var desiredStack = slot < masterSize ? masterInventory.getStackInSlot(slot) : ItemStack.EMPTY;
            var currentStack = mirrorInventory.getStackInSlot(slot);
            if (!sameStack(desiredStack, currentStack)) {
                mirrorInventory.setItemDirect(slot, desiredStack.isEmpty() ? ItemStack.EMPTY : desiredStack.copy());
                changed = true;
            }
        }

        if (changed) {
            this.getLogic().updatePatterns();
        }

        if (masterPatternVersion != UNKNOWN_PATTERN_SYNC_VERSION) {
            this.lastSyncedPatternVersion = masterPatternVersion;
        } else if (changed) {
            this.invalidatePatternSyncState();
        }

        return changed;
    }

    private boolean clearMirroredPatterns() {
        var patternInventory = this.getPatternInventory();
        var changed = false;

        for (int slot = 0; slot < patternInventory.size(); slot++) {
            if (!patternInventory.getStackInSlot(slot).isEmpty()) {
                patternInventory.setItemDirect(slot, ItemStack.EMPTY);
                changed = true;
            }
        }

        if (changed) {
            this.getLogic().updatePatterns();
        }

        return changed;
    }

    private AppEngInternalInventory getPatternInventory() {
        return ((MirrorLogic) this.getLogic()).getActualPatternInventory();
    }

    private static AppEngInternalInventory asPatternInventory(Object inventory) {
        return (AppEngInternalInventory) inventory;
    }

    private void flushStateChanges() {
        this.saveChanges();
        this.markForClientUpdate();
    }

    private void scheduleImmediateSync() {
        this.nextSyncTick = Long.MIN_VALUE;
    }

    private void invalidatePatternSyncState() {
        this.lastSyncedPatternVersion = UNKNOWN_PATTERN_SYNC_VERSION;
    }

    private static int getMirrorPatternSlotCapacity() {
        int pageMultiplier = 1;
        try {
            pageMultiplier = ModConfigs.PAGE_MULTIPLIER.get();
        } catch (Throwable ignored) {
        }

        pageMultiplier = Math.max(1, Math.min(64, pageMultiplier));
        return Math.max(AE2_PATTERN_SLOTS, EXTENDED_PATTERN_PROVIDER_BASE_SLOTS * pageMultiplier);
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        if (left.isEmpty() && right.isEmpty()) {
            return true;
        }

        return ItemStack.isSameItemSameComponents(left, right) && left.getCount() == right.getCount();
    }

    private static long getPatternSyncVersion(PatternProviderLogicHost master) {
        if (master.getLogic() instanceof PatternProviderLogicSyncBridge bridge) {
            return bridge.eap$getPatternSyncVersion();
        }

        return UNKNOWN_PATTERN_SYNC_VERSION;
    }

    @Nullable
    private MasterLocation getBoundMasterLocation() {
        if (this.masterDimension == null || this.masterPos == null) {
            return null;
        }
        return new MasterLocation(this.masterDimension, this.masterPos, this.masterSide);
    }

    @Nullable
    private ResolvedMaster resolveBoundMaster(ServerLevel serverLevel) {
        var target = this.getBoundMasterLocation();
        return target != null ? this.resolveMaster(serverLevel, target) : null;
    }

    @Nullable
    private ResolvedMaster resolveMaster(ServerLevel serverLevel, MasterLocation target) {
        var masterLevel = target.dimension() == serverLevel.dimension()
                ? serverLevel
                : serverLevel.getServer().getLevel(target.dimension());
        if (masterLevel == null || !masterLevel.hasChunkAt(target.pos())) {
            return null;
        }

        return resolveMaster(masterLevel.getBlockEntity(target.pos()), target.side());
    }

    @Nullable
    private static ResolvedMaster resolveMaster(@Nullable BlockEntity blockEntity, @Nullable Direction partSide) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return null;
        }

        if (partSide == null && blockEntity instanceof PatternProviderLogicHost host && isValidMasterHost(host)) {
            var level = blockEntity.getLevel();
            if (level == null) {
                return null;
            }
            return new ResolvedMaster(
                    new MasterLocation(level.dimension(), blockEntity.getBlockPos(), null),
                    host);
        }

        if (partSide != null && blockEntity instanceof CableBusBlockEntity cableBus) {
            return resolvePartMaster(cableBus, partSide);
        }

        return null;
    }

    @Nullable
    private static ResolvedMaster resolvePartMaster(CableBusBlockEntity cableBus, Direction side) {
        var part = cableBus.getCableBus().getPart(side);
        if (part instanceof AEBasePart basePart && isValidMasterHost(basePart)) {
            var level = cableBus.getLevel();
            if (level == null) {
                return null;
            }
            return new ResolvedMaster(
                    new MasterLocation(level.dimension(), cableBus.getBlockPos(), side),
                    (PatternProviderLogicHost) basePart);
        }

        return null;
    }

    @Nullable
    private static Component getCustomName(PatternProviderLogicHost host) {
        if (host instanceof PatternProviderBlockEntity blockEntity) {
            return blockEntity.getCustomName();
        }
        if (host instanceof AEBasePart part) {
            return part.getCustomName();
        }
        return null;
    }

    private static PushDirection getPushDirection(PatternProviderLogicHost host) {
        Direction target = null;
        var targets = host.getTargets();
        if (targets.size() == 1) {
            target = targets.iterator().next();
        }
        return PushDirection.fromDirection(target);
    }

    private static boolean supportsPushDirectionState(PatternProviderLogicHost host) {
        return host instanceof PatternProviderBlockEntity;
    }

    private Component appendMasterSide(MutableComponent component) {
        if (this.masterSide != null) {
            component.append(Component.literal(" [" + this.masterSide.getSerializedName() + "]"));
        }
        return component;
    }

    private static boolean isValidMasterHost(Object host) {
        if (!(host instanceof PatternProviderLogicHost)) {
            return false;
        }

        if (host instanceof MirrorPatternProviderBlockEntity) {
            return false;
        }

        if (host instanceof BlockEntity blockEntity) {
            return !blockEntity.isRemoved();
        }

        return host instanceof AEBasePart part && part.getBlockEntity() != null && !part.getBlockEntity().isRemoved();
    }

    private static final class MirrorLogic extends PatternProviderLogic {
        private MirrorLogic(IManagedGridNode mainNode, MirrorPatternProviderBlockEntity mirrorHost,
                int patternInventorySize) {
            super(mainNode, mirrorHost, patternInventorySize);
        }

        @Override
        public InternalInventory getPatternInv() {
            return DISABLED_PATTERN_INVENTORY;
        }

        private AppEngInternalInventory getActualPatternInventory() {
            return (AppEngInternalInventory) super.getPatternInv();
        }
    }
}
