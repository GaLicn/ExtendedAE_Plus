package com.extendedae_plus.mixin.ae2.accessor;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatternEncodingTermMenu.class)
public interface PatternEncodingTermMenuAccessor {
    @Accessor(value = "encodedPatternSlot",remap = false)
    RestrictedInputSlot eap$getEncodedPatternSlot();

    // 不提供 blankPatternSlot：GTO 所用的 AE2 分支（15.267.x）已将该字段移入
    // PatternEncodingLogic#getBlankPatternInv()，声明此 accessor 会让整个接口应用失败。
    // 需要空白样板库存的调用方请走 IPatternTerminalMenuHost#getLogic()。

    @Accessor(value = "encodedInputsInv", remap = false)
    ConfigInventory eap$getEncodedInputsInv();

    @Accessor(value = "encodedOutputsInv", remap = false)
    ConfigInventory eap$getEncodedOutputsInv();
}
