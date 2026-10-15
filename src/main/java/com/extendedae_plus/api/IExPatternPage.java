package com.extendedae_plus.api;

/**
 * 由 GuiExPatternProviderMixin 实现，用于在客户端侧提供当前页号与最大页数，避免反射读取 AE2 内部字段失败。
 */
public interface IExPatternPage {
    int eap$getCurrentPage();

    void eap$setCurrentPage(int page);

    int eap$getMaxPage();
}