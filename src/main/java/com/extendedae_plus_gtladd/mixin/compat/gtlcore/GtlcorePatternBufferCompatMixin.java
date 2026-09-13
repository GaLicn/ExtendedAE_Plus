package com.extendedae_plus_gtladd.mixin.compat.gtlcore;

import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus_gtladd.util.EapScaledPatternCompat;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 使用原始样板身份查询 GTLCore 的槽位表，同时不改变传给缓冲槽位的缩放输入计数器。
 */
@Mixin(value = MEPatternBufferPartMachineBase.class, priority = 4000, remap = false)
public abstract class GtlcorePatternBufferCompatMixin {

    @ModifyArg(
            method = "pushPattern",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferPartMachineBase;getSlotIndexForPattern(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Integer;",
                    remap = false
            ),
            index = 0,
            require = 0
    )
    private IPatternDetails eap$useOriginalPatternForSlotLookup(IPatternDetails pattern) {
        return EapScaledPatternCompat.unwrap(pattern);
    }
}
