package com.extendedae_plus.wireless;

import appeng.api.networking.IGridNode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 无线端点最小接口。
 * 你的无线收发器方块实体需实现该接口，
 * 以便无线逻辑能够获取世界、位置与 AE2 节点。
 */
public interface IWirelessEndpoint {
    /** 返回方块所在的服务端世界（避免与 BlockEntity#getLevel 冲突） */
    ServerLevel getServerLevel();

    /** 返回方块位置 */
    BlockPos getBlockPos();

    /** 返回可用于 AE2 连接的节点（通常为主节点） */
    IGridNode getGridNode();

    /** 是否已移除/销毁（端点视角），用于在卸载或破坏时停止连接 */
    boolean isEndpointRemoved();
}
