package com.extendedae_plus.items.tools;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.AEBasePart;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity.MasterLocation;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MirrorPatternBindingToolItem extends Item {
    private static final String TAG_SELECTED_MASTER = "selectedMaster";
    private static final String TAG_SELECTED_RANGE_START = "selectedRangeStart";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS = "pos";
    private static final String TAG_SIDE = "side";

    public MirrorPatternBindingToolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return this.handleBlockUse(context, stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return this.handleBlockUse(context, context.getItemInHand());
    }

    private InteractionResult handleBlockUse(UseOnContext context, ItemStack stack) {
        var level = context.getLevel();
        var player = context.getPlayer();
        var blockEntity = level.getBlockEntity(context.getClickedPos());

        var clickedMaster = getClickedMaster(level, context);
        if (clickedMaster != null) {
            if (player != null && player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    setSelectedMaster(stack, clickedMaster);
                    clearSelectedRangeStart(stack);
                    player.displayClientMessage(createSelectedMessage(clickedMaster), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            return InteractionResult.PASS;
        }

        if (blockEntity instanceof MirrorPatternProviderBlockEntity mirror) {
            if (player != null && player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    this.handleRangeBinding(level, context.getClickedPos(), stack, player);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (!level.isClientSide) {
                if (mirror.hasMasterBinding()) {
                    if (player != null && mirror.unbindFromMaster()) {
                        player.displayClientMessage(mirror.createUnboundMessage(), true);
                    }
                    return InteractionResult.SUCCESS;
                }

                var selectedMaster = getSelectedMaster(stack);
                if (selectedMaster == null) {
                    if (player != null) {
                        player.displayClientMessage(
                                Component.translatable("extendedae_plus.message.mirror_binding_tool.no_selection"),
                                true);
                    }
                    return InteractionResult.SUCCESS;
                }

                if (mirror.bindToMaster(selectedMaster)) {
                    if (player != null) {
                        player.displayClientMessage(mirror.createBoundMessage(), true);
                    }
                } else if (player != null) {
                    player.displayClientMessage(
                            Component.translatable("extendedae_plus.message.mirror_binding_tool.bind_failed"),
                            true);
                }
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.translatable("item.extendedae_plus.mirror_pattern_binding_tool.tip.select"));
        tooltipComponents.add(Component.translatable("item.extendedae_plus.mirror_pattern_binding_tool.tip.bind"));
        tooltipComponents.add(Component.translatable("item.extendedae_plus.mirror_pattern_binding_tool.tip.unbind"));
        tooltipComponents.add(Component.translatable("item.extendedae_plus.mirror_pattern_binding_tool.tip.range"));

        var selectedMaster = getSelectedMaster(stack);
        if (selectedMaster != null) {
            var pos = selectedMaster.pos();
            tooltipComponents.add(Component.translatable(
                    "item.extendedae_plus.mirror_pattern_binding_tool.selected",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()));
            tooltipComponents.add(Component.translatable(
                    "item.extendedae_plus.mirror_pattern_binding_tool.dimension",
                    selectedMaster.dimension().location().toString()));
            if (selectedMaster.side() != null) {
                tooltipComponents.add(Component.literal("方向: " + selectedMaster.side().getSerializedName()));
            }
        } else {
            tooltipComponents.add(Component.translatable("item.extendedae_plus.mirror_pattern_binding_tool.unset"));
        }

        var selectedRangeStart = getSelectedRangeStart(stack);
        if (selectedRangeStart != null) {
            var pos = selectedRangeStart.pos();
            tooltipComponents.add(Component.translatable(
                    "item.extendedae_plus.mirror_pattern_binding_tool.range_start",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()));
        }
    }

    private static void setSelectedMaster(ItemStack stack, MasterLocation master) {
        var tag = stack.getOrCreateTag();
        tag.put(TAG_SELECTED_MASTER, createMasterTag(master));
    }

    private static void clearSelectedMaster(ItemStack stack) {
        var tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_SELECTED_MASTER);
        }
    }

    private static void setSelectedRangeStart(ItemStack stack, GlobalPos start) {
        var tag = stack.getOrCreateTag();
        tag.put(TAG_SELECTED_RANGE_START, createGlobalPosTag(start));
    }

    private static void clearSelectedRangeStart(ItemStack stack) {
        var tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_SELECTED_RANGE_START);
        }
    }

    @Nullable
    private static MasterLocation getSelectedMaster(ItemStack stack) {
        return getStoredMasterLocation(stack, TAG_SELECTED_MASTER);
    }

    @Nullable
    private static GlobalPos getSelectedRangeStart(ItemStack stack) {
        return getStoredGlobalPos(stack, TAG_SELECTED_RANGE_START);
    }

    private static CompoundTag createMasterTag(MasterLocation master) {
        var selectedTag = createGlobalPosTag(master.asGlobalPos());
        if (master.side() != null) {
            selectedTag.putString(TAG_SIDE, master.side().getSerializedName());
        }
        return selectedTag;
    }

    private static CompoundTag createGlobalPosTag(GlobalPos globalPos) {
        var selectedTag = new CompoundTag();
        selectedTag.putString(TAG_DIMENSION, globalPos.dimension().location().toString());
        selectedTag.putLong(TAG_POS, globalPos.pos().asLong());
        return selectedTag;
    }

    @Nullable
    private static MasterLocation getStoredMasterLocation(ItemStack stack, String tagKey) {
        var globalPos = getStoredGlobalPos(stack, tagKey);
        if (globalPos == null) {
            return null;
        }

        var tag = stack.getTag();
        if (tag == null) {
            return null;
        }

        var selectedTag = tag.getCompound(tagKey);
        Direction side = null;
        if (selectedTag.contains(TAG_SIDE, Tag.TAG_STRING)) {
            side = Direction.byName(selectedTag.getString(TAG_SIDE));
        }

        return new MasterLocation(globalPos.dimension(), globalPos.pos(), side);
    }

    @Nullable
    private static GlobalPos getStoredGlobalPos(ItemStack stack, String tagKey) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(tagKey, Tag.TAG_COMPOUND)) {
            return null;
        }

        var selectedTag = tag.getCompound(tagKey);
        if (!selectedTag.contains(TAG_DIMENSION, Tag.TAG_STRING) || !selectedTag.contains(TAG_POS, Tag.TAG_LONG)) {
            return null;
        }

        return GlobalPos.of(
                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(selectedTag.getString(TAG_DIMENSION))),
                BlockPos.of(selectedTag.getLong(TAG_POS)));
    }

    private void handleRangeBinding(Level level, BlockPos clickedPos, ItemStack stack, Player player) {
        var selectedMaster = getSelectedMaster(stack);
        if (selectedMaster == null) {
            player.displayClientMessage(
                    Component.translatable("extendedae_plus.message.mirror_binding_tool.no_selection"),
                    true);
            return;
        }

        var rangeStart = getSelectedRangeStart(stack);
        if (rangeStart == null || !rangeStart.dimension().equals(level.dimension())) {
            setSelectedRangeStart(stack, GlobalPos.of(level.dimension(), clickedPos));
            player.displayClientMessage(
                    Component.translatable(
                            "extendedae_plus.message.mirror_binding_tool.range_start_selected",
                            clickedPos.getX(),
                            clickedPos.getY(),
                            clickedPos.getZ()),
                    true);
            return;
        }

        var rangeEnd = clickedPos.immutable();
        var bindResult = bindMirrorsInRange(level, rangeStart.pos(), rangeEnd, selectedMaster);
        clearSelectedRangeStart(stack);

        if (bindResult.totalMirrors() == 0) {
            player.displayClientMessage(
                    Component.translatable("extendedae_plus.message.mirror_binding_tool.range_no_mirror"),
                    true);
            return;
        }

        player.displayClientMessage(
                Component.translatable(
                        "extendedae_plus.message.mirror_binding_tool.range_bound",
                        rangeStart.pos().getX(),
                        rangeStart.pos().getY(),
                        rangeStart.pos().getZ(),
                        rangeEnd.getX(),
                        rangeEnd.getY(),
                        rangeEnd.getZ(),
                        bindResult.totalMirrors(),
                        bindResult.boundMirrors(),
                        bindResult.failedMirrors()),
                true);
    }

    private static RangeBindResult bindMirrorsInRange(Level level, BlockPos start, BlockPos end, MasterLocation selectedMaster) {
        int totalMirrors = 0;
        int boundMirrors = 0;

        var minX = Math.min(start.getX(), end.getX());
        var minY = Math.min(start.getY(), end.getY());
        var minZ = Math.min(start.getZ(), end.getZ());
        var maxX = Math.max(start.getX(), end.getX());
        var maxY = Math.max(start.getY(), end.getY());
        var maxZ = Math.max(start.getZ(), end.getZ());

        for (var pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MirrorPatternProviderBlockEntity mirror) {
                totalMirrors++;
                if (mirror.bindToMaster(selectedMaster)) {
                    boundMirrors++;
                }
            }
        }

        return new RangeBindResult(totalMirrors, boundMirrors, totalMirrors - boundMirrors);
    }

    @Nullable
    private static MasterLocation getClickedMaster(Level level, UseOnContext context) {
        var pos = context.getClickedPos();
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || blockEntity instanceof MirrorPatternProviderBlockEntity) {
            return null;
        }

        if (blockEntity instanceof PatternProviderLogicHost) {
            return new MasterLocation(level.dimension(), pos, null);
        }

        if (blockEntity instanceof CableBusBlockEntity cableBus) {
            Vec3 hitVec = context.getClickLocation();
            Vec3 hitInBlock = new Vec3(hitVec.x - pos.getX(), hitVec.y - pos.getY(), hitVec.z - pos.getZ());
            var selectedPart = cableBus.getCableBus().selectPartLocal(hitInBlock).part;
            if (selectedPart instanceof AEBasePart basePart
                    && selectedPart instanceof PatternProviderLogicHost) {
                return new MasterLocation(level.dimension(), pos, basePart.getSide());
            }
        }

        return null;
    }

    private static Component createSelectedMessage(MasterLocation master) {
        return appendSide(
                Component.translatable(
                        "extendedae_plus.message.mirror_binding_tool.selected",
                        master.pos().getX(),
                        master.pos().getY(),
                        master.pos().getZ()),
                master.side());
    }

    private static Component appendSide(MutableComponent message, @Nullable Direction side) {
        if (side != null) {
            message.append(Component.literal(" [" + side.getSerializedName() + "]"));
        }
        return message;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        var stack = player.getItemInHand(usedHand);

        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            if (getSelectedMaster(stack) != null || getSelectedRangeStart(stack) != null) {
                clearSelectedMaster(stack);
                clearSelectedRangeStart(stack);
                player.displayClientMessage(
                        Component.translatable("extendedae_plus.message.mirror_binding_tool.cleared"),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable("extendedae_plus.message.mirror_binding_tool.no_selection"),
                        true);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private record RangeBindResult(int totalMirrors, int boundMirrors, int failedMirrors) {
    }
}
