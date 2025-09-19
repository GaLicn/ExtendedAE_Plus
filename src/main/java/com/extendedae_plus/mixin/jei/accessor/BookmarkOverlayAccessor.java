package com.extendedae_plus.mixin.jei.accessor;

import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookmarkOverlay.class)
public interface BookmarkOverlayAccessor {
    @Accessor(value = "bookmarkList", remap = false)
    BookmarkList eap$getBookmarkList();
}
