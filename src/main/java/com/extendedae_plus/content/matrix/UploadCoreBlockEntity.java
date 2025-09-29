package com.extendedae_plus.content.matrix;

import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ExtendedAE_Plus: 装配矩阵上传核心方块实体。
 * 作为矩阵内部功能块，仅用于标记该矩阵允许被自动上传（工具类会在集群中查找此实体）。
 */
public class UploadCoreBlockEntity extends TileAssemblerMatrixFunction {

    public UploadCoreBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType<?>) com.extendedae_plus.init.ModBlockEntities.UPLOAD_CORE_BE.get(), pos, state);
    }

    @Override
    public void add(ClusterAssemblerMatrix c) {
        // 无需修改集群，仅作为存在性标记。
        // 若后续需要限制为"最多一个"，可在 ExtendedAE_Plus 工具类或事件中做校验与提示。
    }
}
