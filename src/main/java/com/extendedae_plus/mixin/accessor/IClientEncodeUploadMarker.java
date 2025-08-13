package com.extendedae_plus.mixin.accessor;

/**
 * 客户端在点击“编写样板”时记录一个待上传标记，
 * 当客户端检测到已编码样板槽位被服务器回传填充后，再发送上传请求。
 */
public interface IClientEncodeUploadMarker {
    boolean epp$getClientUploadAfterEncode();
    void epp$setClientUploadAfterEncode(boolean v);
}
