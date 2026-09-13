package com.extendedae_plus.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;

/**
 * Applied Flux 能量存储兼容层。
 *
 * <p>AppFlux 是可选模组，调用方必须先确认 AppFlux 已加载，再进入本兼容类。</p>
 */
public final class AppliedFluxCompat {
    private AppliedFluxCompat() {
    }

    /**
     * 尝试从 ME 网络中提取 FE，并将 FE 转换为实体加速器所需的 AE 数量。
     *
     * <p>先模拟提取，确认数量足够后才实际扣除，避免能源不足时部分扣除。
     * 1 AE 按 2 FE 换算，需求量向上取整。</p>
     */
    public static boolean tryExtractFE(
            IEnergyService energyService,
            MEStorage storage,
            double requiredPower,
            IActionSource source
    ) {
        if (energyService == null || storage == null || requiredPower <= 0) {
            return false;
        }

        long feRequired = toFE(requiredPower);
        if (feRequired <= 0) {
            return false;
        }

        try {
            FluxKey key = FluxKey.of(EnergyType.FE);
            long simulated = StorageHelper.poweredExtraction(
                    energyService,
                    storage,
                    key,
                    feRequired,
                    source,
                    Actionable.SIMULATE
            );
            if (simulated < feRequired) {
                return false;
            }

            long extracted = StorageHelper.poweredExtraction(
                    energyService,
                    storage,
                    key,
                    feRequired,
                    source,
                    Actionable.MODULATE
            );
            return extracted >= feRequired;
        } catch (Throwable ignored) {
            // AppFlux API 发生变化或未完整加载时，交由 AE 能量路径处理。
            return false;
        }
    }

    private static long toFE(double requiredPower) {
        double requiredFE = Math.ceil(requiredPower * 2.0D);
        if (!Double.isFinite(requiredFE) || requiredFE > Long.MAX_VALUE) {
            return 0;
        }
        return (long) requiredFE;
    }
}
