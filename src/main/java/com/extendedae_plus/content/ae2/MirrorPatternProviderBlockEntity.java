package com.extendedae_plus.content.ae2;

import appeng.api.config.Setting;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.IConfigManager;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import appeng.util.CustomNameUtil;
import appeng.util.inv.AppEngInternalInventory;
import com.extendedae_plus.api.bridge.MirrorPatternProviderMasterBridge;
import com.extendedae_plus.api.bridge.PatternProviderPageUnlockBridge;
import com.extendedae_plus.api.bridge.PatternProviderLogicSyncBridge;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    private record ResolvedMaster(MasterLocation location, MirrorPatternProviderMasterBridge host) {
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

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            MirrorPatternProviderBlockEntity blockEntity) {
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
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);

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
    public void loadTag(CompoundTag data) {
        super.loadTag(data);

        this.masterDimension = null;
        this.masterPos = null;
        this.masterSide = null;
        this.scheduleImmediateSync();
        this.invalidatePatternSyncState();
        this.needsUnboundPatternCleanup = true;
        if (data.contains(TAG_MASTER, Tag.TAG_COMPOUND)) {
            var masterTag = data.getCompound(TAG_MASTER);
            if (masterTag.contains(TAG_MASTER_DIMENSION, Tag.TAG_STRING)
                    && masterTag.contains(TAG_MASTER_POS, Tag.TAG_LONG)) {
                this.masterDimension = ResourceKey.create(Registries.DIMENSION,
                        new ResourceLocation(masterTag.getString(TAG_MASTER_DIMENSION)));
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
        var snapshot = this.copyInventoryContents(patternInventory);

        this.clearInventory(patternInventory);
        this.getLogic().updatePatterns();
        super.addAdditionalDrops(level, pos, drops);

        this.restoreInventoryContents(patternInventory, snapshot);
        this.getLogic().updatePatterns();
    }

    @Override
    public void clearContent() {
        this.getLogic().clearContent();
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
        var master = this.getMirrorMaster();
        return master instanceof Ae2MasterAdapter adapter ? adapter.host : null;
    }

    /** 返回当前实际主机；高级样板供应器会通过兼容桥接暴露在这里。 */
    @Nullable
    public MirrorPatternProviderMasterBridge getMirrorMaster() {
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

    private boolean syncMirroredSettings(MirrorPatternProviderMasterBridge master) {
        if (!this.hasDifferentMirroredSettings(master)) {
            return false;
        }

        var changed = false;
        var masterName = master.eap$getMirrorCustomName();
        if (!Objects.equals(this.getCustomName(), masterName)) {
            // 仅导入名称，避免内存卡格式携带整套样板库存。
            var nameTag = new CompoundTag();
            CustomNameUtil.setCustomName(nameTag, masterName);
            super.importSettings(SettingsFrom.MEMORY_CARD, nameTag, null);
            changed = true;
        }

        changed |= syncSharedConfigSettings(master.eap$getMirrorConfigManager(), this.getLogic().getConfigManager());

        if (this.getLogic().getPriority() != master.eap$getMirrorPriority()) {
            this.getLogic().setPriority(master.eap$getMirrorPriority());
            changed = true;
        }

        if (master.eap$supportsMirrorPushDirection()) {
            var masterPushDirection = master.eap$getMirrorPushDirection();
            if (this.getBlockState().getValue(PatternProviderBlock.PUSH_DIRECTION) != masterPushDirection) {
                var level = this.getLevel();
                if (level != null) {
                    level.setBlockAndUpdate(this.getBlockPos(), this.getBlockState().setValue(
                            PatternProviderBlock.PUSH_DIRECTION, masterPushDirection));
                    changed = true;
                }
            }
        }

        return changed;
    }

    private boolean resetMirroredSettingsToInitialState() {
        var defaultState = this.getBlockState().getBlock().defaultBlockState();
        var defaultMirror = new MirrorPatternProviderBlockEntity(this.getBlockPos(), defaultState);
        return this.syncMirroredSettings(new Ae2MasterAdapter(defaultMirror));
    }

    private boolean hasDifferentMirroredSettings(MirrorPatternProviderMasterBridge master) {
        var mirrorLogic = this.getLogic();
        return !Objects.equals(this.getCustomName(), master.eap$getMirrorCustomName())
                || mirrorLogic.getPriority() != master.eap$getMirrorPriority()
                || !haveSameSharedConfigSettings(mirrorLogic.getConfigManager(), master.eap$getMirrorConfigManager())
                || master.eap$supportsMirrorPushDirection()
                && this.getBlockState().getValue(PatternProviderBlock.PUSH_DIRECTION) != master.eap$getMirrorPushDirection();
    }

    private boolean syncMirroredPatterns(MirrorPatternProviderMasterBridge master, boolean forceSync) {
        var masterPatternVersion = master.eap$getMirrorPatternSyncVersion();
        if (!forceSync && masterPatternVersion != UNKNOWN_PATTERN_SYNC_VERSION
                && masterPatternVersion == this.lastSyncedPatternVersion) {
            return false;
        }

        var mirrorInventory = this.getPatternInventory();
        var masterInventory = master.eap$getMirrorPatternInventory();
        var masterUnlockedSlots = master.eap$getMirrorUnlockedPatternSlots();
        var mirrorSize = mirrorInventory.size();
        var masterSize = masterInventory.size();
        var changed = false;

        for (int slot = 0; slot < mirrorSize; slot++) {
            var desiredStack = slot < masterUnlockedSlots && slot < masterSize
                    ? masterInventory.getStackInSlot(slot)
                    : ItemStack.EMPTY;
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

    private ItemStack[] copyInventoryContents(AppEngInternalInventory inventory) {
        var contents = new ItemStack[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) {
            contents[slot] = inventory.getStackInSlot(slot).copy();
        }
        return contents;
    }

    private void restoreInventoryContents(AppEngInternalInventory inventory, ItemStack[] contents) {
        this.clearInventory(inventory);
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setItemDirect(slot, contents[slot].copy());
        }
    }

    private void clearInventory(AppEngInternalInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setItemDirect(slot, ItemStack.EMPTY);
        }
    }

    private void flushStateChanges() {
        this.saveChanges();
        this.markForUpdate();
    }

    private void scheduleImmediateSync() {
        this.nextSyncTick = Long.MIN_VALUE;
    }

    private void invalidatePatternSyncState() {
        this.lastSyncedPatternVersion = UNKNOWN_PATTERN_SYNC_VERSION;
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

        MirrorPatternProviderMasterBridge host = null;
        if (partSide == null && blockEntity instanceof PatternProviderLogicHost ae2Host) {
            host = new Ae2MasterAdapter(ae2Host);
        } else if (partSide == null && blockEntity instanceof MirrorPatternProviderMasterBridge bridge) {
            host = bridge;
        }
        if (host != null && isValidMasterHost(host)) {
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
        MirrorPatternProviderMasterBridge host = null;
        if (part instanceof AEBasePart basePart && basePart instanceof PatternProviderLogicHost ae2Host) {
            host = new Ae2MasterAdapter(ae2Host);
        } else if (part instanceof MirrorPatternProviderMasterBridge bridge) {
            host = bridge;
        }
        if (host != null && isValidMasterHost(host)) {
            var level = cableBus.getLevel();
            if (level == null) {
                return null;
            }
            return new ResolvedMaster(
                    new MasterLocation(level.dimension(), cableBus.getBlockPos(), side),
                    host);
        }

        return null;
    }

    /**
     * AdvancedAE 含有普通供应器不支持的配置项；仅比较双方共有项，避免无意义的周期性重载。
     */
    private static boolean haveSameSharedConfigSettings(IConfigManager mirror, IConfigManager master) {
        for (var setting : master.getSettings()) {
            if (mirror.hasSetting(setting)
                    && !Objects.equals(getConfigValue(mirror, setting), getConfigValue(master, setting))) {
                return false;
            }
        }

        return true;
    }

    private static boolean syncSharedConfigSettings(IConfigManager source, IConfigManager target) {
        var changed = false;
        for (var setting : source.getSettings()) {
            if (target.hasSetting(setting) && !Objects.equals(getConfigValue(source, setting), getConfigValue(target, setting))) {
                copyConfigValue(source, target, setting);
                changed = true;
            }
        }
        return changed;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Enum<?> getConfigValue(IConfigManager manager, Setting setting) {
        return manager.getSetting(setting);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void copyConfigValue(IConfigManager source, IConfigManager target, Setting setting) {
        target.putSetting(setting, source.getSetting(setting));
    }

    private Component appendMasterSide(MutableComponent component) {
        if (this.masterSide != null) {
            component.append(Component.literal(" [" + this.masterSide.getSerializedName() + "]"));
        }
        return component;
    }

    private static int getMirrorPatternSlotCapacity() {
        return Math.max(AE2_PATTERN_SLOTS, UpgradeSlotCompat.getExtendedPatternProviderPatternCapacity());
    }

    private static int getUnlockedPatternSlots(PatternProviderLogic logic) {
        if (logic instanceof PatternProviderPageUnlockBridge bridge && bridge.eap$isExtendedPatternProviderHost()) {
            return Math.max(0, bridge.eap$getUnlockedPatternSlots());
        }

        var inventory = logic.getPatternInv();
        return inventory == null ? 0 : inventory.size();
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        if (left.isEmpty() && right.isEmpty()) {
            return true;
        }

        return ItemStack.isSameItemSameTags(left, right) && left.getCount() == right.getCount();
    }

    private static boolean isValidMasterHost(Object host) {
        if (host instanceof Ae2MasterAdapter adapter) {
            return isValidMasterHost(adapter.host);
        }

        if (!(host instanceof MirrorPatternProviderMasterBridge) && !(host instanceof PatternProviderLogicHost)) {
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

    /** 普通 AE2 供应器适配到统一镜像主机接口。 */
    private static final class Ae2MasterAdapter implements MirrorPatternProviderMasterBridge {
        private final PatternProviderLogicHost host;

        private Ae2MasterAdapter(PatternProviderLogicHost host) {
            this.host = host;
        }

        @Override
        public InternalInventory eap$getMirrorPatternInventory() {
            return host.getLogic().getPatternInv();
        }

        @Override
        public IConfigManager eap$getMirrorConfigManager() {
            return host.getConfigManager();
        }

        @Override
        public int eap$getMirrorPriority() {
            return host.getPriority();
        }

        @Override
        public java.util.EnumSet<Direction> eap$getMirrorTargets() {
            return host.getTargets();
        }

        @Override
        public @Nullable Component eap$getMirrorCustomName() {
            if (host instanceof PatternProviderBlockEntity blockEntity) {
                return blockEntity.getCustomName();
            }
            if (host instanceof AEBasePart part) {
                return part.getCustomName();
            }
            return null;
        }

        @Override
        public boolean eap$supportsMirrorPushDirection() {
            return host instanceof PatternProviderBlockEntity;
        }

        @Override
        public @Nullable PushDirection eap$getMirrorPushDirection() {
            Direction target = null;
            var targets = host.getTargets();
            if (targets.size() == 1) {
                target = targets.iterator().next();
            }
            return PushDirection.fromDirection(target);
        }

        @Override
        public long eap$getMirrorPatternSyncVersion() {
            return host.getLogic() instanceof PatternProviderLogicSyncBridge bridge
                    ? bridge.eap$getPatternSyncVersion() : UNKNOWN_PATTERN_SYNC_VERSION;
        }

        @Override
        public int eap$getMirrorUnlockedPatternSlots() {
            var logic = host.getLogic();
            if (logic instanceof PatternProviderPageUnlockBridge bridge && bridge.eap$isExtendedPatternProviderHost()) {
                return Math.max(0, bridge.eap$getUnlockedPatternSlots());
            }
            var inventory = logic.getPatternInv();
            return inventory == null ? 0 : inventory.size();
        }
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
