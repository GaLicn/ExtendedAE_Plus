package com.extendedae_plus.api.upload;

import net.minecraft.world.inventory.Slot;

/**
 * 第三方样板编码终端菜单接入接口。
 * 实现后即可复用 EAEP 的“上传到供应器”链路。
 */
public interface IPatternUploadMenu {

    /** 当前已编码样板的槽位。 */
    Slot getEncodedPatternSlot();
}
