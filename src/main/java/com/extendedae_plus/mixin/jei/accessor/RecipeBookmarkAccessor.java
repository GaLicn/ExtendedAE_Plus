package com.extendedae_plus.mixin.jei.accessor;

import mezz.jei.gui.bookmarks.RecipeBookmark;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

// 同 BookmarkOverlayAccessor：TMRV 下目标类不存在，必须静默跳过。
// JEI 1.21.1 的 RecipeBookmark 把 recipeUid 作为私有 final 字段保存，没有公开 getter，
// 用 @Accessor 读取比 setAccessible 反射私有字段稳定得多（不受字段名以外的实现变化影响）。
@Pseudo
@Mixin(RecipeBookmark.class)
public interface RecipeBookmarkAccessor {
	@Accessor("recipeUid")
	ResourceLocation eap$getRecipeUid();
}
