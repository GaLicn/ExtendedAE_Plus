package com.extendedae_plus.mixin.ae2.accessor;

import appeng.helpers.InterfaceLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 AE2 ME 接口的升级变更回调，供扩展升级槽后触发内部重算。 */
@Mixin(value = InterfaceLogic.class, remap = false)
public interface InterfaceLogicInvoker {

    @Invoker(value = "onUpgradesChanged", remap = false)
    void eap$onUpgradesChanged();
}