package com.extendedae_plus.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.AEBaseMenu;

@Mixin(AEBaseScreen.class)
public interface AEBaseScreenInvoker<T extends AEBaseMenu> {
    // 空接口：避免在 AEBaseScreen 上声明不存在方法的 Invoker 导致编译错误
}
