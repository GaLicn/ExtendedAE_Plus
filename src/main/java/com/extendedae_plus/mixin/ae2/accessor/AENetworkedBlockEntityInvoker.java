package com.extendedae_plus.mixin.ae2.accessor;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 AE 节点连接面刷新，供原版矩阵玻璃加入超级结构时使用。 */
@Mixin(value = AENetworkedBlockEntity.class, remap = false)
public interface AENetworkedBlockEntityInvoker {

    @Invoker(value = "onGridConnectableSidesChanged", remap = false)
    void eap$refreshGridConnectableSides();
}
