package com.extendedae_plus.mixin.guideme;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static guideme.compiler.tags.MdxAttrs.getRequiredItem;
import static guideme.compiler.tags.MdxAttrs.getRequiredItemAndId;

/**
 * This class contains code back-ported from GuideMe version 20.1.14.
 * <p>
 * Original Copyright (c) Gimpansor and the GuideMe contributors.
 * Original source: <a href="https://github.com/AppliedEnergistics/GuideME">GuideME</a>
 * <p>
 * This back-ported code is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0)
 * as per the terms of the original work.
 * <p>
 * Modifications (if any) are made solely for the purpose of compatibility with earlier versions
 * and are also licensed under LGPL-3.0.
 */
@Mixin(value = MdxAttrs.class, remap = false)
public abstract class MdxAttrsMixin {

    @Inject(method = "getRequiredItemStack", at = @At("HEAD"), cancellable = true)
    private static void onGetRequiredItemStack(PageCompiler compiler, LytErrorSink errorSink, MdxJsxElementFields el, CallbackInfoReturnable<ItemStack> cir) {
        var item = getRequiredItem(compiler, errorSink, el, "id");
        if (item == null) {
            // Fallback: 用新方法
            var result = getRequiredItemStackAndId(compiler, errorSink, el);
            ItemStack stack = (result != null) ? result.getRight() : null;  // 正确访问Pair
            cir.setReturnValue(stack);  // 设置返回值
            cir.cancel();  // 阻止原逻辑
        }
    }

    @Unique
    private static Pair<ResourceLocation, ItemStack> getRequiredItemStackAndId(PageCompiler compiler,
                                                                               LytErrorSink errorSink,
                                                                               MdxJsxElementFields el) {
        var itemAndId = getRequiredItemAndId(compiler, errorSink, el, "id");
        if (itemAndId == null) {
            return null;
        }

        var tag = MdxAttrs.getCompoundTag(compiler, errorSink, el, "tag", null);

        var stack = new ItemStack(itemAndId.getRight());
        stack.setTag(tag);
        return Pair.of(itemAndId.getKey(), stack);
    }
}
