package com.extendedae_plus.util;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;

public class FluxEnergyHelper {
    /**
     * 尝试从 ME 存储提取 FE 能量。
     * @param energyService AE2 能量服务
     * @param storage ME 存储
     * @param feRequired 所需 FE 量
     * @param source 操作来源
     * @return 提取的 FE 量
     */
    public static long extractFE(
        IEnergyService energyService,
        MEStorage storage,
        long feRequired,
        IActionSource source
    ) {
        FluxKey feKey = FluxKey.of(EnergyType.FE);

        // 模拟提取 FE
        long feExtracted = StorageHelper.poweredExtraction(
            energyService, storage, feKey, feRequired, source, Actionable.SIMULATE
        );

        // 执行实际提取
        if (feExtracted >= feRequired) {
            return StorageHelper.poweredExtraction(
                energyService, storage, feKey, feRequired, source, Actionable.MODULATE
            );
        }
        return 0;
    }
}