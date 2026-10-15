package com.extendedae_plus.mixin.ae2.accessor;

import appeng.menu.implementations.PatternAccessTermMenu;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatternAccessTermMenu.class)
public interface PatternAccessTermMenuAccessor {
    /**
     * 菜单内「供应器 ID -> ContainerTracker」的映射。
     *
     * <p>泛型参数用 Object 占位（ContainerTracker 是 AE2 私有内部类），取值后强转为
     * {@link PatternAccessTermContainerTrackerAccessor} 再读取容器。</p>
     */
    @Accessor("byId")
    Long2ObjectOpenHashMap<Object> eap$getById();
}