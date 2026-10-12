package com.extendedae_plus.api.upload;

import com.extendedae_plus.config.PatternUploadPriority;

/**
 * 图样编码后自动上传到矩阵/ECO 的客户端偏好。
 *
 * <p>这些配置项位于客户端配置（{@code extendedae_plus-client.toml}），专用服务器不会加载，
 * 必须由客户端在点击「编码」按钮时随包上报（见 {@code EncodeWithShiftFlagC2SPacket}）。
 * 多人游戏中每个玩家各自生效。</p>
 *
 * @param ecoAutoUploadEnabled 是否允许写入 ECO 合成系统（仅当 NeoECOAE 模组存在时生效）
 * @param uploadPriority       矩阵/ECO 的尝试顺序
 */
public record UploadPreferences(boolean ecoAutoUploadEnabled, PatternUploadPriority uploadPriority) {

    /**
     * 客户端未上报时的兜底默认，与客户端配置项默认值一致（ECO 开启、矩阵优先）。
     */
    public static UploadPreferences defaults() {
        return new UploadPreferences(true, PatternUploadPriority.MATRIX);
    }
}
