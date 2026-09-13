package com.extendedae_plus_gtladd.mixin.eaep.content;

import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;


@Mixin({LabeledWirelessTransceiverBlock.class})
public class LabeledWirelessTransceiverBlockMixin extends Block {
    public LabeledWirelessTransceiverBlockMixin(BlockBehaviour.Properties arg) {
        super(arg);
    }

    @ModifyArg(
        method = {"<init>(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V"},
        at = @At(
    value = "INVOKE",
    target = "Lnet/minecraft/world/level/block/Block;<init>(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V",
    ordinal = 0
),
        index = 0
    )
    private static BlockBehaviour.Properties modifyProps(BlockBehaviour.Properties original) {
        return original.noOcclusion();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }
}
