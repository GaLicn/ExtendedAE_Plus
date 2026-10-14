package com.extendedae_plus.mixin.ae2;

import appeng.blockentity.AEBaseBlockEntity;
import com.extendedae_plus.api.bridge.InterfaceChannelCardOwner;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 让每个 AE 方块实体自带频道卡控制器集合，取代原先以方块实体为键的静态 WeakHashMap。
 *
 * <p>挂在 {@link AEBaseBlockEntity} 上即可覆盖全部宿主：网络方块实体
 * （{@code AENetworkBlockEntity} 子类）与缆线总线（{@code CableBusBlockEntity}）都继承它。
 * 集合随宿主实例存活，不再需要任何静态根，也就不会钉住已经离开世界的 Level。</p>
 */
@Mixin(value = AEBaseBlockEntity.class, remap = false)
public abstract class BlockEntityChannelCardOwnerMixin implements InterfaceChannelCardOwner {

    @Unique
    private Set<ChannelCardConnectionController> eap$channelCardControllers;

    @Override
    public Set<ChannelCardConnectionController> eap$getChannelCardControllers() {
        var controllers = this.eap$channelCardControllers;
        if (controllers == null) {
            // 单个方块实体上的控制器数量极少，身份集足够且避免 equals/hashCode 开销。
            controllers = Collections.newSetFromMap(new IdentityHashMap<>());
            this.eap$channelCardControllers = controllers;
        }
        return controllers;
    }
}
