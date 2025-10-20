package com.extendedae_plus.mixin.guideme;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.ItemLinkCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.LytErrorSink;
import guideme.document.flow.LytFlowLink;
import guideme.document.flow.LytFlowParent;
import guideme.document.flow.LytTooltipSpan;
import guideme.document.interaction.ItemTooltip;
import guideme.indices.ItemIndex;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
@Mixin(value = ItemLinkCompiler.class, remap = false)
public abstract class ItemLinkCompilerMixin {
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void onCompile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el, CallbackInfo ci) {
        System.out.println("加载guideme.compiler.tags.ItemLinkCompilerMixin.onCompile");
        var itemAndId = this.getRequiredItemStackAndId(compiler, parent, el);
        if (itemAndId == null) {
            ci.cancel();
            return;
        }

        var id = itemAndId.getLeft();
        var stack = itemAndId.getRight();
        var linksTo = compiler.getIndex(ItemIndex.class).get(id);

        if (linksTo == null && id.getNamespace().equals(compiler.getPageId().getNamespace())) {
            parent.append(compiler.createErrorFlowContent("No page found for item " + id, el));
            ci.cancel();
            return;
        }

        if (linksTo == null || (linksTo.anchor() == null && compiler.getPageId().equals(linksTo.pageId()))) {
            var span = new LytTooltipSpan();
            span.modifyStyle(style -> style.italic(true));
            span.appendComponent(stack.getHoverName());
            span.setTooltip(new ItemTooltip(stack));
            parent.append(span);
        } else {
            var link = new LytFlowLink();
            link.setClickCallback(screen -> screen.navigateTo(linksTo));
            link.appendComponent(stack.getHoverName());
            link.setTooltip(new ItemTooltip(stack));
            parent.append(link);
        }

        ci.cancel(); // 阻止原逻辑执行
    }

    @Unique
    public Pair<ResourceLocation, ItemStack> getRequiredItemStackAndId(PageCompiler compiler,
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
