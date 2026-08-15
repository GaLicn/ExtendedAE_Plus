package com.extendedae_plus.items.tools;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.me.helpers.PlayerSource;
import com.extendedae_plus.content.matrix.supermatrix.UltimateSuperAssemblerMatrixStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.ArrayList;

/** 仅供创造模式快速放置终极超级装配矩阵的工具。 */
public class UltimateSuperAssemblerMatrixBuilderItem extends Item {
    private static final String TAG_SELECTED_ORIGIN = "selectedOrigin";
    private static final String TAG_BOUND_INTERFACE = "boundInterface";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS = "pos";

    public UltimateSuperAssemblerMatrixBuilderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        var stack = context.getItemInHand();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || player == null) {
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown() && level.getBlockEntity(context.getClickedPos()) instanceof InterfaceBlockEntity) {
            var interfacePos = context.getClickedPos();
            setBoundInterface(stack, GlobalPos.of(level.dimension(), interfacePos));
            player.displayClientMessage(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.interface_bound",
                    interfacePos.getX(), interfacePos.getY(), interfacePos.getZ()), true);
            return InteractionResult.SUCCESS;
        }

        var clickedOrigin = context.getClickedPos().relative(context.getClickedFace());
        // 首次点击锁定起点，避免大型结构的预览随准星移动而难以确认落点。
        if (player.isShiftKeyDown() || getSelectedOrigin(stack) == null) {
            setSelectedOrigin(stack, GlobalPos.of(level.dimension(), clickedOrigin));
            player.displayClientMessage(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.selected",
                    clickedOrigin.getX(), clickedOrigin.getY(), clickedOrigin.getZ()), true);
            return InteractionResult.SUCCESS;
        }

        var origin = getSelectedOrigin(stack);
        if (origin == null || !origin.dimension().equals(level.dimension())) {
            setSelectedOrigin(stack, GlobalPos.of(level.dimension(), clickedOrigin));
            player.displayClientMessage(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.selected",
                    clickedOrigin.getX(), clickedOrigin.getY(), clickedOrigin.getZ()), true);
            return InteractionResult.SUCCESS;
        }

        var placementOrigin = origin.pos();
        var obstruction = UltimateSuperAssemblerMatrixStructure.findFirstObstruction(serverLevel, placementOrigin);
        if (obstruction != null) {
            player.displayClientMessage(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.blocked_at",
                    obstruction.getX(), obstruction.getY(), obstruction.getZ()), true);
            return InteractionResult.FAIL;
        }

        var requirements = UltimateSuperAssemblerMatrixStructure.getRequiredItems(serverLevel);
        IGrid grid = null;
        if (!player.getAbilities().instabuild) {
            grid = getBoundInterfaceGrid(serverLevel, stack);
            if (grid == null) {
                player.sendSystemMessage(Component.translatable(
                        "item.extendedae_plus.ultimate_super_assembler_matrix_builder.interface_unavailable"));
                return InteractionResult.FAIL;
            }
            var missing = findMissingMaterials(grid.getStorageService().getInventory(), requirements, player);
            if (!missing.isEmpty()) {
                reportMissingMaterials(player, missing);
                return InteractionResult.FAIL;
            }
            if (!extractMaterials(grid.getStorageService().getInventory(), requirements, player)) {
                player.sendSystemMessage(Component.translatable(
                        "item.extendedae_plus.ultimate_super_assembler_matrix_builder.materials_changed"));
                return InteractionResult.FAIL;
            }
        }

        if (!UltimateSuperAssemblerMatrixStructure.placeIfClear(serverLevel, placementOrigin)) {
            if (grid != null) {
                refundMaterials(grid.getStorageService().getInventory(), requirements, player);
            }
            player.displayClientMessage(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.blocked"), true);
            return InteractionResult.FAIL;
        }
        clearSelectedOrigin(stack);
        player.displayClientMessage(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.placed"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide && getSelectedOrigin(stack) != null) {
            // 潜行右键空气仅取消已锁定的起点，不触发搭建。
            clearSelectedOrigin(stack);
            player.displayClientMessage(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.selection_cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.tooltip.select"));
        tooltipComponents.add(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.tooltip.confirm"));
        tooltipComponents.add(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.tooltip.reselect"));
        tooltipComponents.add(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.tooltip.bind_interface"));
        tooltipComponents.add(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.tooltip.preview"));
        var origin = getSelectedOrigin(stack);
        if (origin != null) {
            var pos = origin.pos();
            tooltipComponents.add(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.tooltip.selected",
                    pos.getX(), pos.getY(), pos.getZ()));
        }
        var boundInterface = getBoundInterface(stack);
        if (boundInterface != null) {
            var pos = boundInterface.pos();
            tooltipComponents.add(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.tooltip.interface_bound",
                    pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    public static GlobalPos getSelectedOrigin(ItemStack stack) {
        return getStoredPosition(stack, TAG_SELECTED_ORIGIN);
    }

    private static GlobalPos getBoundInterface(ItemStack stack) {
        return getStoredPosition(stack, TAG_BOUND_INTERFACE);
    }

    private static GlobalPos getStoredPosition(ItemStack stack, String tagKey) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(tagKey, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag originTag = tag.getCompound(tagKey);
        if (!originTag.contains(TAG_DIMENSION, Tag.TAG_STRING) || !originTag.contains(TAG_POS, Tag.TAG_LONG)) {
            return null;
        }
        return GlobalPos.of(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(originTag.getString(TAG_DIMENSION))),
                BlockPos.of(originTag.getLong(TAG_POS)));
    }

    private static void setSelectedOrigin(ItemStack stack, GlobalPos origin) {
        setStoredPosition(stack, TAG_SELECTED_ORIGIN, origin);
    }

    private static void setBoundInterface(ItemStack stack, GlobalPos interfacePos) {
        setStoredPosition(stack, TAG_BOUND_INTERFACE, interfacePos);
    }

    private static void setStoredPosition(ItemStack stack, String tagKey, GlobalPos origin) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var originTag = new CompoundTag();
        originTag.putString(TAG_DIMENSION, origin.dimension().location().toString());
        originTag.putLong(TAG_POS, origin.pos().asLong());
        tag.put(tagKey, originTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearSelectedOrigin(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_SELECTED_ORIGIN);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static IGrid getBoundInterfaceGrid(ServerLevel level, ItemStack stack) {
        var interfacePos = getBoundInterface(stack);
        if (interfacePos == null) {
            return null;
        }
        var interfaceLevel = level.getServer().getLevel(interfacePos.dimension());
        if (interfaceLevel == null
                || !(interfaceLevel.getBlockEntity(interfacePos.pos()) instanceof InterfaceBlockEntity interfaceBlock)) {
            return null;
        }
        return interfaceBlock.getMainNode().getGrid();
    }

    private static List<MissingMaterial> findMissingMaterials(MEStorage storage,
            List<UltimateSuperAssemblerMatrixStructure.RequiredItem> requirements, Player player) {
        var missing = new ArrayList<MissingMaterial>();
        var source = new PlayerSource(player);
        for (var requirement : requirements) {
            long available = storage.extract(requirement.key(), requirement.amount(), Actionable.SIMULATE, source);
            if (available < requirement.amount()) {
                missing.add(new MissingMaterial(requirement, requirement.amount() - available));
            }
        }
        return missing;
    }

    private static boolean extractMaterials(MEStorage storage,
            List<UltimateSuperAssemblerMatrixStructure.RequiredItem> requirements, Player player) {
        var extracted = new ArrayList<UltimateSuperAssemblerMatrixStructure.RequiredItem>();
        var source = new PlayerSource(player);
        for (var requirement : requirements) {
            long amount = storage.extract(requirement.key(), requirement.amount(), Actionable.MODULATE, source);
            if (amount != requirement.amount()) {
                if (amount > 0) {
                    storage.insert(requirement.key(), amount, Actionable.MODULATE, source);
                }
                refundMaterials(storage, extracted, player);
                return false;
            }
            extracted.add(requirement);
        }
        return true;
    }

    private static void refundMaterials(MEStorage storage,
            List<UltimateSuperAssemblerMatrixStructure.RequiredItem> requirements, Player player) {
        var source = new PlayerSource(player);
        for (var requirement : requirements) {
            storage.insert(requirement.key(), requirement.amount(), Actionable.MODULATE, source);
        }
    }

    private static void reportMissingMaterials(Player player, List<MissingMaterial> missing) {
        player.sendSystemMessage(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.materials_missing"));
        for (var material : missing) {
            player.sendSystemMessage(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.material_missing_entry",
                    material.requirement().key().getItem().getDescription(), material.missingAmount()));
        }
    }

    private record MissingMaterial(UltimateSuperAssemblerMatrixStructure.RequiredItem requirement, long missingAmount) {
    }
}
