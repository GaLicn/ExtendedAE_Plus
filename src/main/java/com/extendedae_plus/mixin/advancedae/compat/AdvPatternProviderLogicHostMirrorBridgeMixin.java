package com.extendedae_plus.mixin.advancedae.compat;

import appeng.api.inventories.InternalInventory;
import appeng.api.util.IConfigManager;
import appeng.block.crafting.PushDirection;
import appeng.parts.AEBasePart;
import com.extendedae_plus.api.bridge.MirrorPatternProviderMasterBridge;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.EnumSet;

/** 将 AdvancedAE 的高级样板供应器映射为镜像供应器可同步的主机。 */
@Mixin(value = AdvPatternProviderLogicHost.class, remap = false)
public interface AdvPatternProviderLogicHostMirrorBridgeMixin extends MirrorPatternProviderMasterBridge {
    @Override
    default InternalInventory eap$getMirrorPatternInventory() {
        return ((AdvPatternProviderLogicHost) (Object) this).getLogic().getPatternInv();
    }

    @Override
    default IConfigManager eap$getMirrorConfigManager() {
        return ((AdvPatternProviderLogicHost) (Object) this).getConfigManager();
    }

    @Override
    default int eap$getMirrorPriority() {
        return ((AdvPatternProviderLogicHost) (Object) this).getPriority();
    }

    @Override
    default EnumSet<Direction> eap$getMirrorTargets() {
        return ((AdvPatternProviderLogicHost) (Object) this).getTargets();
    }

    @Override
    default @Nullable Component eap$getMirrorCustomName() {
        // 部件名称保存在部件自身，方块名称由方块实体保存。
        if ((Object) this instanceof AEBasePart part) {
            return part.getCustomName();
        }
        if (((AdvPatternProviderLogicHost) (Object) this).getBlockEntity() instanceof Nameable nameable) {
            return nameable.getCustomName();
        }
        return null;
    }

    @Override
    default boolean eap$supportsMirrorPushDirection() {
        return !((Object) this instanceof AEBasePart);
    }

    @Override
    default @Nullable PushDirection eap$getMirrorPushDirection() {
        var targets = this.eap$getMirrorTargets();
        Direction target = targets.size() == 1 ? targets.iterator().next() : null;
        return PushDirection.fromDirection(target);
    }

    @Override
    default long eap$getMirrorPatternSyncVersion() {
        // AdvancedAE 未提供库存变更版本号，镜像按内容进行轻量比对。
        return Long.MIN_VALUE;
    }

    @Override
    default int eap$getMirrorUnlockedPatternSlots() {
        return this.eap$getMirrorPatternInventory().size();
    }
}
