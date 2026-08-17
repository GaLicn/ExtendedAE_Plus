package com.extendedae_plus.api.bridge;

import appeng.api.inventories.InternalInventory;
import appeng.api.util.IConfigManager;
import appeng.block.crafting.PushDirection;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * 为镜像样板供应器统一暴露不同供应器实现的只读同步数据。
 */
public interface MirrorPatternProviderMasterBridge {
    InternalInventory eap$getMirrorPatternInventory();

    IConfigManager eap$getMirrorConfigManager();

    int eap$getMirrorPriority();

    EnumSet<Direction> eap$getMirrorTargets();

    @Nullable
    Component eap$getMirrorCustomName();

    boolean eap$supportsMirrorPushDirection();

    @Nullable
    PushDirection eap$getMirrorPushDirection();

    /** 未提供版本号时返回 {@link Long#MIN_VALUE}，镜像将进行内容比对。 */
    long eap$getMirrorPatternSyncVersion();

    int eap$getMirrorUnlockedPatternSlots();
}
