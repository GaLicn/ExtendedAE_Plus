package com.extendedae_plus.content.ae2;

import appeng.api.config.Settings;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.block.crafting.PatternProviderBlock;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
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
    private static final int SYNC_INTERVAL = 2;
    private static final int AE2_PATTERN_SLOTS = 9;
    private static final int EXTENDED_PATTERN_PROVIDER_BASE_SLOTS = 36;
    private static final InternalInventory DISABLED_PATTERN_INVENTORY = new AppEngInternalInventory(0);

    @Nullable
    private ResourceKey<Level> masterDimension;

    @Nullable
    private BlockPos masterPos;

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
        if (Math.floorMod(level.getGameTime() + this.getBlockPos().asLong(), SYNC_INTERVAL) != 0) {
            return;
        }

        this.syncBoundMaster();
    }

    @Override
    public void onReady() {
        super.onReady();
        if (this.getLevel() instanceof ServerLevel serverLevel) {
            this.syncBoundMaster();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);

        if (this.masterDimension != null && this.masterPos != null) {
            var masterTag = new CompoundTag();
            masterTag.putString(TAG_MASTER_DIMENSION, this.masterDimension.location().toString());
            masterTag.putLong(TAG_MASTER_POS, this.masterPos.asLong());
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
        if (data.contains(TAG_MASTER, Tag.TAG_COMPOUND)) {
            var masterTag = data.getCompound(TAG_MASTER);
            if (masterTag.contains(TAG_MASTER_DIMENSION, Tag.TAG_STRING) && masterTag.contains(TAG_MASTER_POS, Tag.TAG_LONG)) {
                this.masterDimension = ResourceKey.create(
                        Registries.DIMENSION,
                        ResourceLocation.parse(masterTag.getString(TAG_MASTER_DIMENSION)));
                this.masterPos = BlockPos.of(masterTag.getLong(TAG_MASTER_POS));
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

        this.bindToMaster(master);
        this.syncFromMaster(master);
        return true;
    }

    public boolean bindToMaster(GlobalPos master) {
        if (!(this.getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }

        if (master.pos().equals(this.getBlockPos()) && master.dimension().equals(serverLevel.dimension())) {
            return false;
        }

        var masterLevel = serverLevel.getServer().getLevel(master.dimension());
        if (masterLevel != null && masterLevel.hasChunkAt(master.pos())) {
            var blockEntity = masterLevel.getBlockEntity(master.pos());
            if (isValidMaster(blockEntity)) {
                this.syncFromMaster((PatternProviderBlockEntity) blockEntity);
                return true;
            }
            return false;
        }

        var changed = !Objects.equals(this.masterDimension, master.dimension()) || !Objects.equals(this.masterPos, master.pos());
        this.masterDimension = master.dimension();
        this.masterPos = master.pos().immutable();
        if (changed) {
            this.saveChanges();
            this.markForClientUpdate();
        }
        return true;
    }

    @Nullable
    public PatternProviderBlockEntity getMaster() {
        if (this.masterDimension == null || this.masterPos == null || !(this.getLevel() instanceof ServerLevel serverLevel)) {
            return null;
        }

        ServerLevel masterLevel;
        if (serverLevel.dimension() == this.masterDimension) {
            masterLevel = serverLevel;
        } else {
            masterLevel = serverLevel.getServer().getLevel(this.masterDimension);
        }

        if (masterLevel == null || !masterLevel.hasChunkAt(this.masterPos)) {
            return null;
        }

        var blockEntity = masterLevel.getBlockEntity(this.masterPos);
        return isValidMaster(blockEntity) ? (PatternProviderBlockEntity) blockEntity : null;
    }

    public Component createBoundMessage() {
        if (this.masterPos != null) {
            return Component.translatable(
                    "extendedae_plus.message.mirror_pattern_provider.bound",
                    this.masterPos.getX(),
                    this.masterPos.getY(),
                    this.masterPos.getZ());
        }

        return Component.translatable("extendedae_plus.message.mirror_pattern_provider.missing_master");
    }

    public Component getStatusMessage() {
        if (this.masterPos != null) {
            return Component.translatable(
                    "extendedae_plus.message.mirror_pattern_provider.following",
                    this.masterPos.getX(),
                    this.masterPos.getY(),
                    this.masterPos.getZ());
        }

        return Component.translatable("extendedae_plus.message.mirror_pattern_provider.missing_master");
    }

    private void syncBoundMaster() {
        var master = this.getMaster();
        if (master != null) {
            this.syncFromMaster(master);
            return;
        }

        if (this.shouldClearBrokenBinding()) {
            this.clearMasterBinding(true);
        }
    }

    private boolean shouldClearBrokenBinding() {
        if (this.masterDimension == null || this.masterPos == null || !(this.getLevel() instanceof ServerLevel serverLevel)) {
            return true;
        }

        ServerLevel masterLevel;
        if (serverLevel.dimension() == this.masterDimension) {
            masterLevel = serverLevel;
        } else {
            masterLevel = serverLevel.getServer().getLevel(this.masterDimension);
        }

        if (masterLevel == null || !masterLevel.hasChunkAt(this.masterPos)) {
            return false;
        }

        return !isValidMaster(masterLevel.getBlockEntity(this.masterPos));
    }

    private void clearMasterBinding(boolean clearMirroredPatterns) {
        var hadBinding = this.masterDimension != null || this.masterPos != null;

        this.masterDimension = null;
        this.masterPos = null;

        var changed = hadBinding;
        if (clearMirroredPatterns) {
            changed |= this.clearMirroredPatterns();
        }

        if (changed) {
            this.saveChanges();
            this.markForClientUpdate();
        }
    }

    private boolean bindToMaster(PatternProviderBlockEntity master) {
        var masterLevel = master.getLevel();
        if (masterLevel == null) {
            return false;
        }

        var newDimension = masterLevel.dimension();
        var newPos = master.getBlockPos().immutable();
        var changed = !Objects.equals(this.masterDimension, newDimension) || !Objects.equals(this.masterPos, newPos);

        this.masterDimension = newDimension;
        this.masterPos = newPos;

        if (changed) {
            this.saveChanges();
            this.markForClientUpdate();
        }

        return changed;
    }

    @Nullable
    private PatternProviderBlockEntity findAdjacentMaster() {
        var level = this.getLevel();
        if (level == null) {
            return null;
        }

        for (var direction : Direction.values()) {
            var adjacent = level.getBlockEntity(this.getBlockPos().relative(direction));
            if (isValidMaster(adjacent)) {
                return (PatternProviderBlockEntity) adjacent;
            }
        }

        return null;
    }

    private boolean syncFromMaster(PatternProviderBlockEntity master) {
        var changed = this.bindToMaster(master);
        changed |= this.syncMirroredSettings(master);
        changed |= this.syncMirroredPatterns(master);

        if (changed) {
            this.saveChanges();
            this.markForClientUpdate();
        }

        return changed;
    }

    private boolean syncMirroredSettings(PatternProviderBlockEntity master) {
        if (!this.hasDifferentMirroredSettings(master)) {
            return false;
        }

        var builder = DataComponentMap.builder();
        if (master.getCustomName() != null) {
            builder.set(AEComponents.EXPORTED_CUSTOM_NAME, master.getCustomName());
        }
        builder.set(AEComponents.EXPORTED_SETTINGS, master.getConfigManager().exportSettings());
        builder.set(AEComponents.EXPORTED_PRIORITY, master.getPriority());
        builder.set(AEComponents.EXPORTED_PUSH_DIRECTION, master.getBlockState().getValue(PatternProviderBlock.PUSH_DIRECTION));
        this.importSettings(SettingsFrom.MEMORY_CARD, builder.build(), null);
        return true;
    }

    private boolean hasDifferentMirroredSettings(PatternProviderBlockEntity master) {
        var mirrorLogic = this.getLogic();
        var masterLogic = master.getLogic();

        return !Objects.equals(this.getCustomName(), master.getCustomName())
                || mirrorLogic.getPriority() != masterLogic.getPriority()
                || mirrorLogic.getConfigManager().getSetting(Settings.BLOCKING_MODE)
                != masterLogic.getConfigManager().getSetting(Settings.BLOCKING_MODE)
                || mirrorLogic.getConfigManager().getSetting(Settings.PATTERN_ACCESS_TERMINAL)
                != masterLogic.getConfigManager().getSetting(Settings.PATTERN_ACCESS_TERMINAL)
                || mirrorLogic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE)
                != masterLogic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE)
                || this.getBlockState().getValue(PatternProviderBlock.PUSH_DIRECTION)
                != master.getBlockState().getValue(PatternProviderBlock.PUSH_DIRECTION);
    }

    private boolean syncMirroredPatterns(PatternProviderBlockEntity master) {
        var mirrorInventory = this.getPatternInventory();
        var desiredInventory = this.createDesiredPatternInventory(master);

        if (this.hasSamePatterns(desiredInventory, mirrorInventory)) {
            return false;
        }

        mirrorInventory.fromItemContainerContents(desiredInventory.toItemContainerContents());
        this.getLogic().updatePatterns();
        return true;
    }

    private boolean clearMirroredPatterns() {
        var patternInventory = this.getPatternInventory();
        if (this.isPatternInventoryEmpty(patternInventory)) {
            return false;
        }

        patternInventory.fromItemContainerContents(ItemContainerContents.EMPTY);
        this.getLogic().updatePatterns();
        return true;
    }

    private boolean hasSamePatterns(AppEngInternalInventory masterInventory, AppEngInternalInventory mirrorInventory) {
        if (masterInventory.size() != mirrorInventory.size()) {
            return false;
        }

        for (int slot = 0; slot < masterInventory.size(); slot++) {
            if (!sameStack(masterInventory.getStackInSlot(slot), mirrorInventory.getStackInSlot(slot))) {
                return false;
            }
        }

        return true;
    }

    private boolean isPatternInventoryEmpty(AppEngInternalInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private AppEngInternalInventory getPatternInventory() {
        return ((MirrorLogic) this.getLogic()).getActualPatternInventory();
    }

    private AppEngInternalInventory createDesiredPatternInventory(PatternProviderBlockEntity master) {
        var desiredInventory = new AppEngInternalInventory(this.getPatternInventory().size());
        var masterInventory = asPatternInventory(master.getLogic().getPatternInv());
        var copySlotCount = Math.min(masterInventory.size(), desiredInventory.size());

        for (int slot = 0; slot < copySlotCount; slot++) {
            desiredInventory.setItemDirect(slot, masterInventory.getStackInSlot(slot).copy());
        }

        return desiredInventory;
    }

    private static AppEngInternalInventory asPatternInventory(Object inventory) {
        return (AppEngInternalInventory) inventory;
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

    private static boolean isValidMaster(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof PatternProviderBlockEntity
                && !(blockEntity instanceof MirrorPatternProviderBlockEntity)
                && !blockEntity.isRemoved();
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
