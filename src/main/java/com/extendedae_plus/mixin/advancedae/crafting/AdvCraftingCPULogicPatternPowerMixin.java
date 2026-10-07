package com.extendedae_plus.mixin.advancedae.crafting;

import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.util.crafting.StrictMolecularAssemblerPattern;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 让 AdvanceAE 量子计算机的超级矩阵倍率样板按单次合成检查和扣除 AE 能量。 */
@Mixin(value = AdvCraftingCPULogic.class, remap = false)
public abstract class AdvCraftingCPULogicPatternPowerMixin {

    /**
     * 当前正在派发的样板。
     *
     * <p>该字段替代 MixinExtras 的 {@code @Local} 取值。{@code @Local} 由 MixinExtras 在注入点
     * 现场解析目标方法的局部变量槽位，并额外生成一层桥接方法承载处理器方法体；当目标方法
     * 被其它模组以 {@code @Overwrite} 整体替换时，桥接方法会残留指向已不存在槽位的读取指令，
     * 使 JVM 在类校验阶段抛出 {@code VerifyError: Bad local variable type} 并中断合成 CPU 构造。
     * 显式捕获不依赖槽位推断，也不再生成桥接方法。
     *
     * <p>字段必须为实例级：处理器方法与目标方法 {@code executeCrafting} 同为实例方法，且
     * 同一 tick 内多台合成 CPU 会各自调用一次 {@code executeCrafting}，静态字段将在两次访问
     * 之间被其它合成 CPU 覆盖。
     */
    @Unique
    private IPatternDetails eap$currentDetails;

    /**
     * 在样板写入局部变量时留存引用，供能量表达式注入点读取。
     *
     * <p>处理器方法不得声明为 {@code static}：{@code @ModifyVariable} 由 Mixin 核心校验处理器
     * 与目标方法的 {@code static} 修饰符一致性，而 {@code executeCrafting} 为实例方法，声明为
     * static 将抛出 {@code InvalidInjectionException} 并使目标类注入整体失败。
     */
    @ModifyVariable(method = "executeCrafting", at = @At("STORE"), remap = false, name = "details")
    private IPatternDetails eap$captureDetails(IPatternDetails details) {
        this.eap$currentDetails = details;
        return details;
    }

    @ModifyExpressionValue(method = "executeCrafting",
            at = @At(value = "INVOKE",
                    target = "Lappeng/crafting/execution/CraftingCpuHelper;calculatePatternPower([Lappeng/api/stacks/KeyCounter;)D"))
    private double eap$useSingleCraftPower(double original) {
        IPatternDetails details = this.eap$currentDetails;
        if (details instanceof ScaledMolecularAssemblerPattern scaled
                && scaled.getOriginal() instanceof StrictMolecularAssemblerPattern
                && scaled.getMultiplier() > 1) {
            // craftingContainer 仍是整批输入，只将能量表达式还原为一次合成的成本。
            return original / scaled.getMultiplier();
        }
        return original;
    }
}
