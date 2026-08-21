package com.extendedae_plus.content.matrix.supermatrix;

import com.extendedae_plus.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public class SuperAssemblerMatrixFrameBlockEntity extends SuperAssemblerMatrixBlockEntity {

    public SuperAssemblerMatrixFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPER_ASSEMBLER_MATRIX_FRAME_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide()) {
            this.refreshLoadedFrameModels();
        }
    }

    @Override
    public ModelData getModelData() {
        if (this.level == null) {
            return ModelData.EMPTY;
        }
        // 优化渲染器从方块实体读取模型数据，确保连接纹理不会退化成单方块外观。
        return MatrixFrameModelData.create(this.level, this.worldPosition, ModelData.EMPTY);
    }

    private void refreshLoadedFrameModels() {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    var neighborPos = this.worldPosition.offset(x, y, z);
                    if (!this.level.hasChunkAt(neighborPos)
                            || !(this.level.getBlockEntity(neighborPos)
                                    instanceof SuperAssemblerMatrixFrameBlockEntity frame)) {
                        continue;
                    }
                    // 后加载的区块需要反向刷新已加载邻居，补齐跨区块连接纹理。
                    frame.requestModelDataUpdate();
                    var state = frame.getBlockState();
                    this.level.sendBlockUpdated(neighborPos, state, state, 0);
                }
            }
        }
    }
}
