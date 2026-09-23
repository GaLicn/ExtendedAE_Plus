package com.extendedae_plus.compat;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.extendedae_plus.util.ModCheckUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * NeoECOAE 样板上传兼容层。
 *
 * <p>NeoECOAE 属于可选模组，本类自身不引用其任何类型：ECO 专有调用全部收敛在内部桥接类
 * {@code NeoECOBridge} 中，只有确认模组已加载后才会被解析，从而避免模组缺失时出现类加载失败。</p>
 */
public final class NeoECOAECompat {

    /** ECO 侧插入调用的归一化结果，用于隔离模组专有枚举。 */
    public enum UploadOutcome {
        /** 模组未加载，无法执行上传。 */
        UNAVAILABLE,
        /** 样板已写入 ECO 合成系统。 */
        INSERTED,
        /** ECO 合成系统中已存在等价样板，无需重复写入。 */
        ALREADY_PRESENT,
        /** ECO 侧目标已满。 */
        NO_SPACE,
        /** 样板类型不被 ECO 合成系统接受。 */
        INCOMPATIBLE,
        /** 当前网络中没有可写入的 ECO 目标。 */
        NO_TARGET
    }

    private NeoECOAECompat() {
    }

    /**
     * 返回 NeoECOAE 是否已加载。
     *
     * <p>判定交给 {@link ModCheckUtils}，避免在类初始化阶段缓存结果而受模组列表
     * 尚未就绪的影响。版本下限由 {@code neoforge.mods.toml} 的依赖声明约束。</p>
     */
    public static boolean isAvailable() {
        return ModCheckUtils.isECOLoading();
    }

    /**
     * 判断结果是否表示样板已进入 ECO 合成系统。
     *
     * <p>{@link UploadOutcome#ALREADY_PRESENT} 同样计为已满足：样板已存在于 ECO 合成系统，
     * 调用方不应再回退到其他目标重复写入。</p>
     */
    public static boolean isAccepted(UploadOutcome outcome) {
        return outcome == UploadOutcome.INSERTED || outcome == UploadOutcome.ALREADY_PRESENT;
    }

    /**
     * 将图样编码终端已编码槽位中的样板上传到 ECO 合成系统。
     *
     * <p>仅在 ECO 返回 {@link UploadOutcome#INSERTED} 时清空编码槽位，与 ECO 原生上传按钮
     * 的语义保持一致：{@link UploadOutcome#ALREADY_PRESENT} 不改变槽位内容。</p>
     *
     * @param player 服务器玩家
     * @param menu   图样编码终端菜单
     * @return 归一化后的上传结果
     */
    public static UploadOutcome tryUploadFromEncodingMenu(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (!isAvailable() || player == null || menu == null) {
            return UploadOutcome.UNAVAILABLE;
        }

        var encodedSlot = ((PatternEncodingTermMenuAccessor) (Object) menu).eap$getEncodedPatternSlot();
        if (encodedSlot == null) {
            return UploadOutcome.UNAVAILABLE;
        }

        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return UploadOutcome.INCOMPATIBLE;
        }

        IGrid grid = resolveGrid(menu);
        if (grid == null) {
            return UploadOutcome.NO_TARGET;
        }

        UploadOutcome outcome = NeoECOBridge.insert(grid, stack);
        if (outcome == UploadOutcome.INSERTED) {
            encodedSlot.set(ItemStack.EMPTY);
        }
        return outcome;
    }

    /**
     * 解析编码终端所属的 AE 网络。
     *
     * <p>1.21 的菜单不再直接暴露网络，需要经 {@link AEBaseMenu#getTarget()} 取得
     * {@link IActionHost} 后才能访问其可操作节点。</p>
     */
    private static IGrid resolveGrid(PatternEncodingTermMenu menu) {
        try {
            if (menu instanceof AEBaseMenu base) {
                Object target = base.getTarget();
                if (target instanceof IActionHost host && host.getActionableNode() != null) {
                    return host.getActionableNode().getGrid();
                }
            }
        } catch (Throwable ignored) {
            // 菜单目标尚未就绪时按无网络处理。
        }
        return null;
    }

    /**
     * ECO 专有调用桥接类。
     *
     * <p>本类在方法体中直接引用 NeoECOAE 的类型，因此只有 {@link #isAvailable()} 为真时
     * 才会被实际加载。</p>
     */
    private static final class NeoECOBridge {

        private NeoECOBridge() {
        }

        static UploadOutcome insert(IGrid grid, ItemStack pattern) {
            var service = grid.getService(cn.dancingsnow.neoecoae.api.IECOPatternStorageService.class);
            if (service == null) {
                return UploadOutcome.NO_TARGET;
            }

            var result = service.getPatternStorage().insertPatternWithResult(pattern.copy());
            return switch (result) {
                case INSERTED -> UploadOutcome.INSERTED;
                case ALREADY_PRESENT -> UploadOutcome.ALREADY_PRESENT;
                case NO_SPACE -> UploadOutcome.NO_SPACE;
                case INCOMPATIBLE -> UploadOutcome.INCOMPATIBLE;
                case NO_TARGET -> UploadOutcome.NO_TARGET;
            };
        }
    }
}
