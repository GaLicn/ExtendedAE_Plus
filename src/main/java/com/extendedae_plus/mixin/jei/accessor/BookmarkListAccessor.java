package com.extendedae_plus.mixin.jei.accessor;

import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

// 同 BookmarkOverlayAccessor：TMRV 下目标类不存在，必须静默跳过。
@Pseudo
@Mixin(BookmarkList.class)
public interface BookmarkListAccessor {
	@Accessor("bookmarkFactory")
	BookmarkFactory eap$getBookmarkFactory();
}
