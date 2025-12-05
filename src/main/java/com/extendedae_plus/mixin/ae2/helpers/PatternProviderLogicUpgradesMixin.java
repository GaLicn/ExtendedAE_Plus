package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 为样板供应器添加升级槽支持
 * - 没有 AppliedFlux 时：添加 1 个升级槽
 * - 有 AppliedFlux 时：在其基础上再增加 1 个（总共 2 个）
 * 
 * 优先级 1100 确保在 AppliedFlux (默认优先级 1000) 之后执行
 */
@Mixin(value = PatternProviderLogic.class, priority = 1100, remap = false)
public abstract class PatternProviderLogicUpgradesMixin {

    @Final
    @Shadow
    private PatternProviderLogicHost host;
    
    @Final
    @Shadow
    private IManagedGridNode mainNode;
    
    @Unique
    private IUpgradeInventory eap$upgrades = UpgradeInventories.empty();
    
    @Unique
    private boolean eap$hasAppliedFlux = false;
    
    @Unique
    private boolean eap$upgradesInitialized = false;

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        try {
            // 检测是否安装了 AppliedFlux
            this.eap$hasAppliedFlux = ModList.get().isLoaded("appflux");
            
            if (eap$hasAppliedFlux) {
                // AppliedFlux 已安装，尝试获取并扩展其升级槽
                eap$extendAppliedFluxUpgrades();
            } // 未安装 AppliedFlux 的情况由 CompatMixin 负责创建兼容升级槽
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] 初始化失败", t);
        }
    }
    
    @Unique
    private void eap$extendAppliedFluxUpgrades() {
        try {
            // 通过反射直接读取 AppliedFlux Mixin 注入的字段：af_upgrades
            IUpgradeInventory existingUpgrades = null;
            try {
                Field f = this.getClass().getDeclaredField("af_upgrades");
                f.setAccessible(true);
                existingUpgrades = (IUpgradeInventory) f.get(this);
            } catch (Throwable t) {
            }
            
            if (existingUpgrades != null && existingUpgrades != UpgradeInventories.empty()) {
                // AppliedFlux 已经创建了升级槽
                int currentSlots = existingUpgrades.size();
                int targetSlots = 2; // AppliedFlux 1个 + 我们 1个 = 2个
                
                
                if (currentSlots < targetSlots) {
                    // 需要扩展升级槽
                    // 先保存现有物品
                    ItemStack[] savedItems = new ItemStack[currentSlots];
                    for (int i = 0; i < currentSlots; i++) {
                        savedItems[i] = existingUpgrades.getStackInSlot(i).copy();
                    }
                    
                    // 创建新的升级槽（更多槽位）
                    this.eap$upgrades = UpgradeInventories.forMachine(
                        host.getTerminalIcon().getItem(), 
                        targetSlots, 
                        this::eap$onUpgradesChanged
                    );
                    
                    // 恢复原有物品
                    for (int i = 0; i < savedItems.length; i++) {
                        this.eap$upgrades.setItemDirect(i, savedItems[i]);
                    }
                    // 将 AF 的字段指向我们新的升级槽，保持其服务与 NBT 钩子一致
                    try {
                        Field f = this.getClass().getDeclaredField("af_upgrades");
                        f.setAccessible(true);
                        f.set(this, this.eap$upgrades);
                    } catch (Throwable t) {
                    }
                    
                    this.eap$upgradesInitialized = true;
                } else {
                    // AppliedFlux 或其他模组已经提供了足够的槽位
                    this.eap$upgrades = existingUpgrades;
                    this.eap$upgradesInitialized = true;
                }
            } else {
                // AppliedFlux 还没初始化升级槽，或者出了问题，我们创建默认的
                this.eap$upgrades = UpgradeInventories.forMachine(
                    host.getTerminalIcon().getItem(), 
                    2, 
                    this::eap$onUpgradesChanged
                );
                // 同步 AF 字段
                try {
                    Field f = this.getClass().getDeclaredField("af_upgrades");
                    f.setAccessible(true);
                    f.set(this, this.eap$upgrades);
                } catch (Throwable t) {
                }
                this.eap$upgradesInitialized = true;
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] 扩展 AppliedFlux 升级槽失败", t);
            // 失败时创建默认数量
            this.eap$upgrades = UpgradeInventories.forMachine(
                host.getTerminalIcon().getItem(), 
                2, 
                this::eap$onUpgradesChanged
            );
            // 同步 AF 字段（最佳努力）
            try {
                Field f = this.getClass().getDeclaredField("af_upgrades");
                f.setAccessible(true);
                f.set(this, this.eap$upgrades);
            } catch (Throwable ignored) {}
            this.eap$upgradesInitialized = true;
        }
    }
    
    @Unique
    private void eap$onUpgradesChanged() {
        try {
            this.host.saveChanges();
            
            // 如果 AppliedFlux 安装了，也调用其原始的 onUpgradesChanged 方法
            if (eap$hasAppliedFlux) {
                try {
                    Method afMethod = this.getClass().getDeclaredMethod("af_onUpgradesChanged");
                    afMethod.setAccessible(true);
                    afMethod.invoke(this);
                } catch (NoSuchMethodException e) {
                    // AppliedFlux 的方法不存在，这是正常的
                } catch (Throwable t) {
                }
            }
            
            // 通过 Bridge 接口通知 CompatMixin 升级槽已变更
            // 这样无论是否安装 AppFlux，所有升级槽变更都会触发频道卡/虚拟合成卡的重新检测
            if ((Object) this instanceof com.extendedae_plus.api.bridge.PatternProviderLogicUpgradeCompatBridge bridge) {
                bridge.eap$onCompatUpgradesChangedHook();
            }
            
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] onUpgradesChanged 处理失败", t);
        }
    }
    

    
    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$saveUpgrades(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        try {
            // 已安装 AF 时交由 AF 的 mixin 处理，避免重复写入
            if (eap$hasAppliedFlux) {
                return;
            }
            if (eap$upgradesInitialized && this.eap$upgrades != null && this.eap$upgrades != UpgradeInventories.empty()) {
                // 根据是否有 AppliedFlux 使用不同的 NBT 键
                if (eap$hasAppliedFlux) {
                    // AppliedFlux 使用 "upgrades" 键，我们使用 "eap_upgrades" 避免冲突
                    this.eap$upgrades.writeToNBT(tag, "eap_upgrades", registries);
                } else {
                    // 没有 AppliedFlux，使用标准键
                    this.eap$upgrades.writeToNBT(tag, "upgrades", registries);
                }
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] 保存升级槽失败", t);
        }
    }
    
    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$loadUpgrades(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        try {
            // 重新检测 AppliedFlux 状态（可能在世界加载时状态有变化）
            this.eap$hasAppliedFlux = ModList.get().isLoaded("appflux");
            
            if (!eap$upgradesInitialized) {
                // 如果还没初始化，先初始化
                if (eap$hasAppliedFlux) {
                    eap$extendAppliedFluxUpgrades();
                } else {
                    this.eap$upgrades = UpgradeInventories.forMachine(
                        host.getTerminalIcon().getItem(), 
                        1, 
                        this::eap$onUpgradesChanged
                    );
                    this.eap$upgradesInitialized = true;
                }
            }
            
            // 已安装 AF 时，由 AF 自行从 "upgrades" 读取；我们只处理无 AF 情况
            if (!eap$hasAppliedFlux && this.eap$upgrades != null && this.eap$upgrades != UpgradeInventories.empty()) {
                if (tag.contains("upgrades")) {
                    this.eap$upgrades.readFromNBT(tag, "upgrades", registries);
                }
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] 加载升级槽失败", t);
        }
    }
    
    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$dropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        try {
            // AF 已安装时交由其自身处理掉落
            if (eap$hasAppliedFlux) {
                return;
            }
            if (eap$upgradesInitialized && this.eap$upgrades != null) {
                for (var is : this.eap$upgrades) {
                    if (!is.isEmpty()) {
                        drops.add(is);
                    }
                }
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] 添加掉落失败", t);
        }
    }
    
    @Inject(method = "clearContent", at = @At("TAIL"))
    private void eap$clearUpgrades(CallbackInfo ci) {
        try {
            // AF 已安装时交由其自身处理清理
            if (eap$hasAppliedFlux) {
                return;
            }
            if (eap$upgradesInitialized && this.eap$upgrades != null) {
                this.eap$upgrades.clear();
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] 清空升级槽失败", t);
        }
    }
}
