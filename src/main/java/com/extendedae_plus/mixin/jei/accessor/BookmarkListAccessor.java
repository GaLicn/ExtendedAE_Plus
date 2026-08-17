package com.extendedae_plus.mixin.jei.accessor;

import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookmarkList.class)
public interface BookmarkListAccessor {
	@Accessor("bookmarkFactory")
	BookmarkFactory eap$getBookmarkFactory();
}
