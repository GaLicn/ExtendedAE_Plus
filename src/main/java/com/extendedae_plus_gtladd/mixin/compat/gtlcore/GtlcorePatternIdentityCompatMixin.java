package com.extendedae_plus_gtladd.mixin.compat.gtlcore;

import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus_gtladd.util.EapScaledPatternCompat;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 让 GTLCore 基于身份的供应器识别 EAEP 的样板包装器。
 * 只解包样板身份，输入计数器仍保持倍增后的数量。
 */
@Mixin(value = MEMolecularAssemblerIOPartMachine.class, priority = 4000, remap = false)
public abstract class GtlcorePatternIdentityCompatMixin {

    @ModifyVariable(method = "pushPattern", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private IPatternDetails eap$unwrapPatternIdentity(IPatternDetails pattern) {
        return EapScaledPatternCompat.unwrap(pattern);
    }
}
