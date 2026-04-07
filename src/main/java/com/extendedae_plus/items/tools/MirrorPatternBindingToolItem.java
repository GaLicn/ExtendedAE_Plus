package com.extendedae_plus.items.tools;

import appeng.blockentity.crafting.PatternProviderBlockEntity;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MirrorPatternBindingToolItem extends Item {
    private static final String TAG_SELECTED_MASTER = "selectedMaster";
    private static final String TAG_SELECTED_RANGE_START = "selectedRangeStart";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS = "pos";

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

        if (blockEntity instanceof PatternProviderBlockEntity && !(blockEntity instanceof MirrorPatternProviderBlockEntity)) {
            if (player != null && player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    setSelectedMaster(stack, GlobalPos.of(level.dimension(), context.getClickedPos()));
                    clearSelectedRangeStart(stack);
                    player.displayClientMessage(
                            Component.translatable(
                                    "extendedae_plus.message.mirror_binding_tool.selected",
                                    context.getClickedPos().getX(),
                                    context.getClickedPos().getY(),
                                    context.getClickedPos().getZ()),
                            true);
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.translatable("item.extendedae_plus.mirror_pattern_binding_tool.tip.select"));
        tooltipComponents.add(Component.translatable("item.extendedae_plus.mirror_pattern_binding_tool.tip.bind"));
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

    private static void setSelectedMaster(ItemStack stack, GlobalPos master) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(TAG_SELECTED_MASTER, createGlobalPosTag(master));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearSelectedMaster(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_SELECTED_MASTER);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void setSelectedRangeStart(ItemStack stack, GlobalPos start) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(TAG_SELECTED_RANGE_START, createGlobalPosTag(start));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearSelectedRangeStart(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_SELECTED_RANGE_START);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Nullable
    private static GlobalPos getSelectedMaster(ItemStack stack) {
        return getStoredGlobalPos(stack, TAG_SELECTED_MASTER);
    }

    @Nullable
    private static GlobalPos getSelectedRangeStart(ItemStack stack) {
        return getStoredGlobalPos(stack, TAG_SELECTED_RANGE_START);
    }

    private static CompoundTag createGlobalPosTag(GlobalPos globalPos) {
        var selectedTag = new CompoundTag();
        selectedTag.putString(TAG_DIMENSION, globalPos.dimension().location().toString());
        selectedTag.putLong(TAG_POS, globalPos.pos().asLong());
        return selectedTag;
    }

    @Nullable
    private static GlobalPos getStoredGlobalPos(ItemStack stack, String tagKey) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(tagKey, Tag.TAG_COMPOUND)) {
            return null;
        }

        var selectedTag = tag.getCompound(tagKey);
        if (!selectedTag.contains(TAG_DIMENSION) || !selectedTag.contains(TAG_POS)) {
            return null;
        }

        return GlobalPos.of(
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(selectedTag.getString(TAG_DIMENSION))),
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

        var bindResult = bindMirrorsInRange(level, rangeStart.pos(), clickedPos, selectedMaster);
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
                        bindResult.totalMirrors(),
                        bindResult.boundMirrors(),
                        bindResult.failedMirrors()),
                true);
    }

    private static RangeBindResult bindMirrorsInRange(Level level, BlockPos start, BlockPos end, GlobalPos selectedMaster) {
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
