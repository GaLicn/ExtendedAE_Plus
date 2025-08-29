package com.extendedae_plus.mixin.extendedae;

import appeng.api.util.IConfigurableObject;
import appeng.menu.guisync.GuiSync;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import com.glodblock.github.extendedae.container.ContainerExPatternTerminal;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import com.glodblock.github.glodium.network.packet.sync.Paras;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mixin(ContainerExPatternTerminal.class)
public abstract class ContainerExPatternTerminalMixin implements IActionHolder {

    @GuiSync(25564)
    @Unique
    public boolean eap$hidePatternSlots = false;

    @Unique
    public boolean isHidePatternSlots() {
        return this.eap$hidePatternSlots;
    }

    @Unique
    public void setHidePatternSlots(boolean hide) {
        this.eap$hidePatternSlots = hide;
    }

    @Unique
    public void toggleHidePatternSlots() {
        this.eap$hidePatternSlots = !this.eap$hidePatternSlots;
    }

    @Unique
    private Map<String, Consumer<Paras>> eap$actions;

    @Unique
    private Player epp$player;

    @Unique
    private static final Logger EAP_LOGGER = LogManager.getLogger("ExtendedAE_Plus");

    @Inject(method = "<init>*", at = @At("TAIL"))
    private void init(int id, net.minecraft.world.entity.player.Inventory playerInventory, IConfigurableObject host, CallbackInfo ci) {
        if (this.eap$actions == null) {
            this.eap$actions = createHolder();
        }
        this.epp$player = playerInventory.player;
        // 注册上传动作：参数顺序必须与客户端 CGenericPacket 保持一致
        this.eap$actions.put("upload", p -> {
            try {
                Object o0 = p.get(0);
                Object o1 = p.get(1);
                int playerSlotIndex = (o0 instanceof Number) ? ((Number) o0).intValue() : Integer.parseInt(String.valueOf(o0));
                long providerId = (o1 instanceof Number) ? ((Number) o1).longValue() : Long.parseLong(String.valueOf(o1));
                var sp = (ServerPlayer) this.epp$player;
                ExtendedAEPatternUploadUtil.uploadPatternToProvider(sp, playerSlotIndex, providerId);
            } catch (Throwable ignored) {
            }
        });

        // 注册打开UI动作：open_ui(posLong, dimensionId, faceOrdinal?)
        this.eap$actions.put("open_ui", p -> {
            try {
                // 参数解析
                Object po = p.get(0); // BlockPos as long (BlockPos#asLong)
                Object do0 = p.get(1); // Dimension id string (e.g., minecraft:overworld)
                Object fo;
                try {
                    fo = p.get(2); // Optional face ordinal
                } catch (Throwable __ignored) {
                    fo = null;
                }

                long posLong = (po instanceof Number) ? ((Number) po).longValue() : Long.parseLong(String.valueOf(po));
                String dimStr = String.valueOf(do0);
                int faceOrd = -1;
                if (fo != null) {
                    faceOrd = (fo instanceof Number) ? ((Number) fo).intValue() : Integer.parseInt(String.valueOf(fo));
                }

                BlockPos pos = BlockPos.of(posLong);
                ResourceLocation dimId = ResourceLocation.tryParse(dimStr);
                if (dimId == null) {
                    EAP_LOGGER.warn("[EPlus] open_ui: invalid dim '{}'", dimStr);
                    return;
                }
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);

                if (!(this.epp$player instanceof ServerPlayer sp)) {
                    EAP_LOGGER.warn("[EPlus] open_ui: not a ServerPlayer");
                    return;
                }

                ServerLevel level = sp.server.getLevel(dimKey);
                if (level == null) {
                    EAP_LOGGER.warn("[EPlus] open_ui: level null for key {}", dimKey);
                    return;
                }

                EAP_LOGGER.debug("[EPlus] open_ui: pos={}, dim={}, faceOrd={}", pos, dimKey.location(), faceOrd);

                // 目标应为供应器所面向/连接的相邻方块，而非供应器自身
                Direction[] tries = (faceOrd >= 0 && faceOrd < Direction.values().length)
                        ? new Direction[]{Direction.values()[faceOrd]}
                        : Direction.values();

                // 1) 先尝试在相邻方块直接打开 MenuProvider
                for (Direction dir : tries) {
                    BlockPos targetPos = pos.relative(dir);
                    BlockEntity be = level.getBlockEntity(targetPos);
                    if (be instanceof MenuProvider provider) {
                        NetworkHooks.openScreen(sp, provider, targetPos);
                        EAP_LOGGER.debug("[EPlus] open_ui: opened BE MenuProvider at {} (neighbor via {})", targetPos, dir);
                        return;
                    }
                    var state = level.getBlockState(targetPos);
                    MenuProvider provider = state.getMenuProvider(level, targetPos);
                    if (provider != null) {
                        NetworkHooks.openScreen(sp, provider, targetPos);
                        EAP_LOGGER.debug("[EPlus] open_ui: opened State MenuProvider at {} (neighbor via {})", targetPos, dir);
                        return;
                    }
                }

                // 2) 兜底：为避免误触发放置/覆盖，仅在手上至少有一只手为空时，使用 BlockState.use 进行一次“徒手交互”
                boolean hasFace = (faceOrd >= 0 && faceOrd < Direction.values().length);
                boolean anyHandEmpty = sp.getMainHandItem().isEmpty() || sp.getOffhandItem().isEmpty();
                if (anyHandEmpty) {
                    InteractionHand hand = sp.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    if (hasFace) {
                        Direction dir = Direction.values()[faceOrd];
                        BlockPos targetPos = pos.relative(dir);
                        var state2 = level.getBlockState(targetPos);
                        var hit = new BlockHitResult(Vec3.atCenterOf(targetPos), dir.getOpposite(), targetPos, false);
                        InteractionResult r = state2.use(level, sp, hand, hit);
                        EAP_LOGGER.debug("[EPlus] open_ui: fallback(state.use) at {} hit {} (via {}), result={}", targetPos, dir.getOpposite(), dir, r);
                    } else {
                        // 无朝向：优先尝试有方块实体的邻居，否则尝试实心方块邻居，各只尝试一次
                        Direction chosen = null;
                        for (Direction d : Direction.values()) {
                            if (level.getBlockEntity(pos.relative(d)) != null) { chosen = d; break; }
                        }
                        if (chosen == null) {
                            for (Direction d : Direction.values()) {
                                if (!level.getBlockState(pos.relative(d)).isAir()) { chosen = d; break; }
                            }
                        }
                        if (chosen != null) {
                            BlockPos targetPos = pos.relative(chosen);
                            var state2 = level.getBlockState(targetPos);
                            var hit = new BlockHitResult(Vec3.atCenterOf(targetPos), chosen.getOpposite(), targetPos, false);
                            InteractionResult r = state2.use(level, sp, hand, hit);
                            EAP_LOGGER.debug("[EPlus] open_ui: fallback(state.use) at {} hit {} (auto via {}), result={}", targetPos, chosen.getOpposite(), chosen, r);
                        } else {
                            EAP_LOGGER.debug("[EPlus] open_ui: no neighbor candidate for fallback (faceOrd<0)");
                        }
                    }
                } else {
                    EAP_LOGGER.debug("[EPlus] open_ui: skip fallback (hands occupied)");
                }
            } catch (Throwable ignored) {
            }
        });
    }

    @NotNull
    @Override
    public Map<String, Consumer<Paras>> getActionMap() {
        return this.eap$actions;
    }
}