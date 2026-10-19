package com.extendedae_plus.client;

import com.extendedae_plus.init.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * 把吞噬核心装备到头部槽位时，在头顶位置登记一个黑洞。
 *
 * <p>本类不再自行绘制贴片。引力透镜要扭曲的是玩家真实看到的画面，而一张贴片只能画出
 * 正对相机的假图，无论怎样调整矩阵都无法扭曲其身后的地形与实体。故此处只计算黑洞的世界
 * 坐标，交由 {@link BlackHoleLensRenderer} 在场景渲染完成后以屏幕空间后处理重采样整幅
 * 画面。</p>
 *
 * <p>黑洞是悬在头顶的一个立体物体，吸积盘与地面平行，由透镜通道从相机的真实位置追踪光线成像：
 * 绕着它走能看到盘面的各个侧面，从下方仰视时画面上下翻转，挡在它前面的方块与实体也会遮住它。</p>
 */
public final class BlackHoleHeadRenderer implements ICurioRenderer {

    // 黑洞中心高出头部中心的距离（格）。头部高 0.5 格，取 1 格时黑洞中心离头顶 0.75 格，吸积盘
    // 的下沿与头顶之间留出一段空隙。
    private static final float HEIGHT_OFFSET = 1.0F;

    BlackHoleHeadRenderer() {
    }

    /**
     * 把吞噬核心登记到 Curios 的渲染器表。Curios 为可选前置，调用方须在确认其已加载后经反射
     * 调用本方法，避免未安装时链接本类触发 NoClassDefFoundError。
     */
    public static void registerRenderers() {
        CuriosRendererRegistry.register(ModItems.INFINITY_CORE.get(), BlackHoleHeadRenderer::new);
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack,
            SlotContext slotContext,
            PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent,
            MultiBufferSource bufferSource,
            int packedLight,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!(renderLayerParent.getModel() instanceof HeadedModel headedModel)) {
            return;
        }

        LivingEntity wearer = slotContext.entity();
        poseStack.pushPose();
        try {
            // 与猪咪等头部饰品保持一致的幼年缩放处理。
            if (wearer.isBaby() && !(wearer instanceof Villager)) {
                poseStack.translate(0.0F, 0.03125F, 0.0F);
                poseStack.scale(0.7F, 0.7F, 0.7F);
                poseStack.translate(0.0F, 1.0F, 0.0F);
            }
            headedModel.getHead().translateAndRotate(poseStack);
            boolean villagerHead = wearer instanceof Villager || wearer instanceof ZombieVillager;
            CustomHeadLayer.translateToHead(poseStack, villagerHead);

            // 坐标空间依据（Forge 1.20.1 源码）：LevelRenderer 以已含相机旋转的 PoseStack 调用
            // renderEntity，EntityRenderDispatcher.render 再 translate(相机相对坐标)。故 poseStack
            // 的平移分量是头部原点在视图空间中的位置，经 GameRenderer 写入 RenderSystem 的逆视图
            // 旋转转回，即头部原点相对相机的位置，加上相机世界坐标便是黑洞的世界坐标。此处只取
            // 平移而丢弃头部旋转：黑洞是球对称的，不应随头部俯仰摆动。
            Vector3f headOffset = RenderSystem.getInverseViewRotationMatrix()
                    .transform(poseStack.last().pose().getTranslation(new Vector3f()));
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            if (!camera.isInitialized()) {
                return;
            }
            Vec3 cameraPosition = camera.getPosition();
            BlackHoleLensRenderer.mark(
                    cameraPosition.x + headOffset.x,
                    cameraPosition.y + headOffset.y + HEIGHT_OFFSET,
                    cameraPosition.z + headOffset.z);
        } finally {
            poseStack.popPose();
        }
    }
}
