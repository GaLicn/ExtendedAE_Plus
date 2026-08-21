package com.extendedae_plus.content.matrix.supermatrix;

import com.extendedae_plus.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class MatrixFrameModelData {
    public static final ModelProperty<Connections> CONNECTIONS = new ModelProperty<>();
    private static final Connections EMPTY_CONNECTIONS = new Connections();

    private MatrixFrameModelData() {
    }

    public static ModelData create(BlockAndTintGetter level, BlockPos pos, ModelData baseModelData) {
        if (baseModelData.has(CONNECTIONS)) {
            return baseModelData;
        }

        var connections = new Connections();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if ((x != 0 || y != 0 || z != 0)
                            && level.getBlockState(pos.offset(x, y, z))
                                    .is(ModBlocks.SUPER_ASSEMBLER_MATRIX_FRAME.get())) {
                        connections.set(x, y, z);
                    }
                }
            }
        }
        return baseModelData.derive().with(CONNECTIONS, connections).build();
    }

    public static Connections getOrEmpty(ModelData modelData) {
        var connections = modelData.get(CONNECTIONS);
        return connections != null ? connections : EMPTY_CONNECTIONS;
    }

    public static final class Connections {
        private final boolean[][][] connections = new boolean[3][3][3];

        private void set(int x, int y, int z) {
            this.connections[x + 1][y + 1][z + 1] = true;
        }

        public boolean contains(Direction direction) {
            return this.contains(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        }

        public boolean contains(Direction first, Direction second) {
            return this.contains(
                    first.getStepX() + second.getStepX(),
                    first.getStepY() + second.getStepY(),
                    first.getStepZ() + second.getStepZ());
        }

        private boolean contains(int x, int y, int z) {
            return this.connections[x + 1][y + 1][z + 1];
        }
    }
}
