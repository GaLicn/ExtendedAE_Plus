package com.extendedae_plus.mixin.jei.accessor;

import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

// TMRV 会冒充 jei，但其类路径上没有 mezz.jei.gui.*；@Pseudo 让目标缺失时静默跳过而不是报错。
@Pseudo
@Mixin(BookmarkOverlay.class)
public interface BookmarkOverlayAccessor {
    @Accessor("bookmarkList")
    BookmarkList eap$getBookmarkList();
}
