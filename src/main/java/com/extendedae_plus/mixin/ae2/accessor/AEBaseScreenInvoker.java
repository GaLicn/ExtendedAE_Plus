package com.extendedae_plus.mixin.ae2.accessor;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.AEBaseMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AEBaseScreen.class)
public interface AEBaseScreenInvoker<T extends AEBaseMenu> {
    // 空接口：避免在 AEBaseScreen 上声明不存在方法的 Invoker 导致编译错误
}
