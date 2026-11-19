package com.extendedae_plus.api.client;

public interface IRepoUpdateFreezeAccess {
    boolean eap$isUpdatesFrozen();

    void eap$setUpdatesFrozen(boolean frozen);
}
