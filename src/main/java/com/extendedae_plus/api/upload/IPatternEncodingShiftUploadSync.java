package com.extendedae_plus.api.upload;

public interface IPatternEncodingShiftUploadSync {
    /**
     * 由客户端发送的编码指令附带的 Shift 状态。
     */
    void eap$clientSetShiftUpload(boolean shiftDown);

    /**
     * 服务器在处理 encode() 时消费该标记，并在读取后自动复位。
     */
    boolean eap$consumeShiftUploadFlag();
}
