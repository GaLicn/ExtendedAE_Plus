package com.extendedae_plus.content.matrix.supermatrix;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface SuperAssemblerMatrixPart {

    BlockPos eap$getSuperMatrixPos();

    @Nullable
    Level eap$getSuperMatrixLevel();

    @Nullable
    SuperAssemblerMatrixCluster eap$getSuperMatrixCluster();

    void eap$setSuperMatrixCluster(@Nullable SuperAssemblerMatrixCluster cluster);

    void eap$updateSuperMatrixStatus();

    default void eap$breakSuperMatrixCluster() {
        var cluster = this.eap$getSuperMatrixCluster();
        if (cluster != null) {
            cluster.destroy();
        }
    }

    default void eap$destroySuperMatrixClusterQuietly() {
        var cluster = this.eap$getSuperMatrixCluster();
        if (cluster != null) {
            // 区块卸载时先静默拆掉超级矩阵引用，避免 AE 节点销毁时继续追踪整组多方块。
            cluster.destroyQuietly();
        }
    }
}
