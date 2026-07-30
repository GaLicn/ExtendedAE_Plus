package com.extendedae_plus.content.cutter;

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

/** 单方块超级电路切片机。 */
public class SuperCircuitCutterBlock extends AEBaseEntityBlock<SuperCircuitCutterBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public SuperCircuitCutterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WORKING, false));
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
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, SuperCircuitCutterBlockEntity blockEntity) {
        return state.setValue(WORKING, blockEntity.isWorking());
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        return openMenu(level, pos, player);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        openMenu(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private InteractionResult openMenu(Level level, BlockPos pos, Player player) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            MenuOpener.open(ModMenuTypes.CIRCUIT_CUTTER_PLUS.get(), player, MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
