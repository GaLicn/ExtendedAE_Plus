package com.extendedae_plus.content.crystal;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/** 单方块超级水晶装配器。 */
public class SuperCrystalAssemblerBlock extends AEBaseEntityBlock<SuperCrystalAssemblerBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public SuperCrystalAssemblerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState currentState, SuperCrystalAssemblerBlockEntity blockEntity) {
        return currentState.setValue(WORKING, blockEntity.isWorking());
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        return this.openMenu(level, pos, player);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        var parent = super.useItemOn(heldItem, state, level, pos, player, hand, hitResult);
        if (parent.result() != InteractionResult.PASS) {
            return parent;
        }
        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        this.openMenu(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private InteractionResult openMenu(Level level, BlockPos pos, Player player) {
        var blockEntity = this.getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            MenuOpener.open(ModMenuTypes.CRYSTAL_ASSEMBLER_PLUS.get(), player, MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
