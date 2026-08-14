package com.extendedae_plus.items.tools;

import com.extendedae_plus.content.matrix.supermatrix.UltimateSuperAssemblerMatrixStructure;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** 仅供创造模式快速放置终极超级装配矩阵的工具。 */
public class UltimateSuperAssemblerMatrixBuilderItem extends Item {

    public UltimateSuperAssemblerMatrixBuilderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || player == null || !player.getAbilities().instabuild) {
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        "item.extendedae_plus.ultimate_super_assembler_matrix_builder.creative_only"), true);
            }
            return InteractionResult.FAIL;
        }

        var origin = context.getClickedPos().relative(context.getClickedFace());
        if (!UltimateSuperAssemblerMatrixStructure.placeIfClear(serverLevel, origin)) {
            player.displayClientMessage(Component.translatable(
                    "item.extendedae_plus.ultimate_super_assembler_matrix_builder.blocked"), true);
            return InteractionResult.FAIL;
        }

        player.displayClientMessage(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.placed"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.tooltip.preview"));
        tooltipComponents.add(Component.translatable(
                "item.extendedae_plus.ultimate_super_assembler_matrix_builder.tooltip.creative"));
    }
}
