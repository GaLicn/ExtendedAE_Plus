package com.extendedae_plus.api.upload;

import net.minecraft.world.inventory.Slot;

/**
 * 第三方样板编码终端菜单接入接口。
 * 实现后即可获得 EAEP 的「上传到供应器」链路支持。
 * <p>
 * 供应器枚举所需的 AE 网络从{@code AEBaseMenu.getTarget()} 获取，
 * 实现方菜单必需继承 {@code AEBaseMenu}。
 */
public interface IPatternUploadMenu {

    /**
     * 当前已编码样板的槽位。服务端据此读取样板、并在上传成功后清空/减少数量。
     */
    Slot getEncodedPatternSlot();
}
