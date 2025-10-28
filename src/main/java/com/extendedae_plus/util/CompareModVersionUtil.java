package com.extendedae_plus.util;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.Optional;

//此类代码借鉴于gtlcore
public class CompareModVersionUtil {
    private static final ModList modList = ModList.get();
    private static final LoadingModList loadingModList = LoadingModList.get();

    public static boolean compareTo(String modId, String version) {
        Optional<String> versionStr;
        if (modList == null) {
            versionStr = loadingModList
                    .getMods()
                    .stream()
                    .filter(mi -> mi.getModId()
                            .equals(modId))
                    .findFirst()
                    .map(mi -> mi.getVersion()
                            .toString());
        } else {
            versionStr = modList.getModContainerById(modId)
                    .map(mc -> mc.getModInfo()
                            .getVersion()
                            .toString());
        }
        return versionStr
                .map(v -> new ComparableVersion(v).compareTo(new ComparableVersion(version)) >= 0)
                .orElse(false);
    }
}
