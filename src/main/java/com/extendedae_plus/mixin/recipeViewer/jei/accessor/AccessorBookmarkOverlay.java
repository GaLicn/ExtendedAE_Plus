package com.extendedae_plus.mixin.recipeViewer.jei.accessor;

import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookmarkOverlay.class)
public interface AccessorBookmarkOverlay {
    @Accessor(value = "bookmarkList", remap = false)
    BookmarkList eap$getBookmarkList();
}