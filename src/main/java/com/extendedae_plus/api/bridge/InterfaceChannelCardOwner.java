package com.extendedae_plus.api.bridge;

import com.extendedae_plus.util.wireless.ChannelCardConnectionController;

import java.util.Set;

/**
 * 由方块实体实现，把频道卡控制器挂到宿主自身的生命周期上。
 *
 * <p>控制器内部的 lambda 捕获了宿主方块实体（如 {@code () -> this.host.getBlockEntity()}），
 * 所以不能用「静态表 + 方块实体作键」存放：那会形成「值强引用键」的环，WeakHashMap 的键
 * 永远弱可达、条目永不回收，每个条目会钉死一整个 Level。改为把集合挂在宿主实例上，
 * 引用环就只被宿主自己持有，宿主不可达时整环一起被回收。</p>
 */
public interface InterfaceChannelCardOwner {

    /** 返回该宿主当前的控制器集合，惰性创建且不返回 null。 */
    Set<ChannelCardConnectionController> eap$getChannelCardControllers();

    /** 宿主从世界移除时调用：关闭其全部控制器。 */
    default void eap$unloadChannelCardControllers() {
        var controllers = eap$getChannelCardControllers();
        ChannelCardConnectionController[] snapshot;
        synchronized (controllers) {
            if (controllers.isEmpty()) {
                return;
            }
            snapshot = controllers.toArray(new ChannelCardConnectionController[0]);
        }
        // 只关闭不清空：区块卸载后会重新加载再卸载，清空会让第二次卸载静默跳过。
        // 集合随宿主实例一起被回收，部件被拆掉时由 unregister 精确摘除，不会无限增长。
        for (var controller : snapshot) {
            controller.onUnloaded();
        }
    }
}
