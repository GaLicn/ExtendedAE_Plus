package com.extendedae_plus.mixin.minecraft.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link Minecraft#pickBlock()} 的访问器。
 *
 * <p>该方法为私有，强制下单组合键的键盘入口无法直接调用。经本访问器调用
 * 可完整走一次原版 {@code pickBlock}，使键盘入口与鼠标中键入口同构，判定逻辑无须
 * 从 {@code PickFromWirelessMixin} 的注入点复制出来。</p>
 *
 * <p>调用点须将 {@link Minecraft} 实例强转为本接口。Mixin 在加载期把该接口织入目标类，
 * 故该强转在运行时成立；方法名由 Mixin 负责重映射，生产环境混淆后依然有效。</p>
 */
@Mixin(Minecraft.class)
public interface MinecraftPickBlockAccessor {

    /**
     * 以原版路径执行一次取方块。
     */
    @Invoker("pickBlock")
    void extendedae_plus$pickBlock();
}
