package com.extendedae_plus.mixin.extendedae.container;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import com.glodblock.github.glodium.network.packet.sync.Paras;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(value = ContainerExPatternProvider.class, priority = 3000)
public abstract class ContainerExPatternProviderMixin extends PatternProviderMenu implements IActionHolder {

    @Unique
    private final Map<String, Consumer<Paras>> eap$actions = createHolder();

    public ContainerExPatternProviderMixin(MenuType<? extends PatternProviderMenu> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        // 注册通用动作（供 CGenericPacket 分发）
        this.eap$actions.put("multiply2", p -> eap$modifyPatterns(2, false));
        this.eap$actions.put("divide2", p -> eap$modifyPatterns(2, true));
        this.eap$actions.put("multiply5", p -> eap$modifyPatterns(5, false));
        this.eap$actions.put("divide5", p -> eap$modifyPatterns(5, true));
        this.eap$actions.put("multiply10", p -> eap$modifyPatterns(10, false));
        this.eap$actions.put("divide10", p -> eap$modifyPatterns(10, true));
    }

    @Unique
    private void eap$modifyPatterns(int scale, boolean div) {
        if (scale <= 0) return;
        for (var slot : this.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            var stack = slot.getItem();
            if (stack.getItem() instanceof EncodedPatternItem pattern) {
                var detail = pattern.decode(stack, this.getPlayer().level(), false);
                if (detail instanceof AEProcessingPattern process) {
                    var input = process.getSparseInputs();
                    var output = process.getOutputs();
                    if (eap$checkModify(input, scale, div) && eap$checkModify(output, scale, div)) {
                        var mulInput = new GenericStack[input.length];
                        var mulOutput = new GenericStack[output.length];
                        eap$modifyStacks(input, mulInput, scale, div);
                        eap$modifyStacks(output, mulOutput, scale, div);
                        var newPattern = PatternDetailsHelper.encodeProcessingPattern(mulInput, mulOutput);
                        slot.set(newPattern);
                    }
                }
            }
        }
    }

    @Unique
    private boolean eap$checkModify(GenericStack[] stacks, int scale, boolean div) {
        if (stacks == null) return false;
        if (div) {
            for (var stack : stacks) {
                if (stack != null) {
                    if (stack.amount() % scale != 0) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            for (var stack : stacks) {
                if (stack != null) {
                    long amt = stack.amount();
                    // 先检查乘法是否会导致超出 Long.MAX_VALUE，避免溢出
                    if (amt > Integer.MAX_VALUE / scale) {
                        return false;
                    }
                    // 已移除原有的业务上限检查（999999 * amountPerUnit），仅保留溢出检查
                }
            }
            return true;
        }
    }

    @Unique
    private void eap$modifyStacks(GenericStack[] src, GenericStack[] dst, int scale, boolean div) {
        for (int i = 0; i < src.length; i++) {
            var stack = src[i];
            if (stack != null) {
                long amt = stack.amount();
                long newAmt;
                if (div) {
                    newAmt = amt / scale;
                } else {
                    // 防御性检查：虽然上游已检查过，但在此处再次确保不会溢出
                    if (amt > Integer.MAX_VALUE / scale) {
                        // 遇到潜在溢出时跳过该项（保持原样为 null），调用方已通过 eap$checkModify 避免进入此分支
                        dst[i] = null;
                        continue;
                    }
                    newAmt = amt * scale;
                }
                dst[i] = new GenericStack(stack.what(), newAmt);
            } else {
                dst[i] = null;
            }
        }
    }

    @NotNull
    @Override
    public Map<String, Consumer<Paras>> getActionMap() {
        return this.eap$actions;
    }
}