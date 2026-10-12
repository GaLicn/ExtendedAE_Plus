package com.extendedae_plus.api.upload;

/**
 * 图样编码终端菜单接收客户端「编码」请求的同步接口。
 */
public interface IPatternEncodeRequestSync {

    /**
     * 暂存客户端随「编码」按钮上报的请求，供服务端 {@code encode()} 之后消费。
     */
    void eap$clientSetEncodeRequest(EncodeRequest request);

    /**
     * 消费暂存的编码请求并复位；客户端未上报时返回 {@code null}。
     */
    EncodeRequest eap$consumeEncodeRequest();

    /**
     * 一次「编码」动作的客户端上报内容。
     *
     * <p>Shift 状态与唯一匹配判定都依赖客户端环境（语言、客户端配置），只能由客户端上报。</p>
     *
     * @param shiftDown      点击时是否按住 Shift（按住则不自动上传）
     * @param uniqueProvider 客户端判定唯一匹配的供应器名称，即服务端下发的原始名称（Component JSON）；未命中为 {@code null}
     */
    record EncodeRequest(boolean shiftDown, String uniqueProvider) {
    }
}
