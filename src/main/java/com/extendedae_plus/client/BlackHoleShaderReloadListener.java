package com.extendedae_plus.client;

import com.extendedae_plus.ExtendedAEPlus;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/** 加载黑洞天空着色器，并在读取时对本模组的着色器源码做 ASCII 过滤。 */
public final class BlackHoleShaderReloadListener {

    private BlackHoleShaderReloadListener() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        BlackHoleSkyRenderer.setSmoothShader(null);
        BlackHoleSkyRenderer.setPixelatedShader(null);
        BlackHolePixelSprite.setSpriteShader(null);
        BlackHolePixelSprite.setFinishShader(null);
        BlackHoleLensRenderer.setShader(null);

        // 平滑风格为单遍绘制。像素风格下故障会临时切换到平滑风格，故两种风格都需要它。
        register(event, "black_hole", DefaultVertexFormat.POSITION_TEX, BlackHoleSkyRenderer::setSmoothShader);
        // 像素风格由三遍组成：原始精灵、杂点清理与描边、贴到天空的合成。所选风格见
        // BlackHoleSkyRenderer.USE_PIXELATED_SHADER。
        if (BlackHoleSkyRenderer.USE_PIXELATED_SHADER) {
            register(event, "black_hole_pixelated", DefaultVertexFormat.POSITION_TEX,
                    BlackHolePixelSprite::setSpriteShader);
            register(event, "black_hole_pixelated_finish", DefaultVertexFormat.POSITION_TEX,
                    BlackHolePixelSprite::setFinishShader);
            register(event, "black_hole_pixelated_composite", DefaultVertexFormat.POSITION_TEX,
                    BlackHoleSkyRenderer::setPixelatedShader);
        }

        // 屏幕空间透镜通道。其顶点仅含裁剪空间位置，故使用 POSITION 格式；采样源在绘制时
        // 以自定义 sampler 名绑定，不能写进 JSON 的固定采样槽。
        register(event, "black_hole_lens", DefaultVertexFormat.POSITION, BlackHoleLensRenderer::setShader);
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format,
            Consumer<ShaderInstance> sink) {
        try {
            event.registerShader(
                    new ShaderInstance(asciiShaderSources(event.getResourceProvider()), ExtendedAEPlus.id(name), format),
                    sink);
        } catch (IOException exception) {
            EAP$LOGGER.error("Failed to load the black-hole shader {}", name, exception);
        }
    }

    /**
     * 剥离本模组着色器阶段（含 #moj_import 引入的 .glsl）的非 ASCII 字节。部分 GL 驱动会拒绝
     * GLSL 源码中的任何非 ASCII 字节，注释亦不例外，故在此统一滤除以避免驱动报错；着色器源码本身
     * 为纯 ASCII 时该过滤不改变内容，仅作为兜底保留。
     */
    private static ResourceProvider asciiShaderSources(ResourceProvider provider) {
        return location -> {
            Optional<Resource> resource = provider.getResource(location);
            String path = location.getPath();
            if (resource.isEmpty() || !location.getNamespace().equals(ExtendedAEPlus.MODID)
                    || !(path.endsWith(".vsh") || path.endsWith(".fsh") || path.endsWith(".glsl"))) {
                return resource;
            }
            Resource original = resource.get();
            return Optional.of(new Resource(original.source(), () -> stripNonAscii(original.open())));
        };
    }

    private static InputStream stripNonAscii(InputStream source) throws IOException {
        try (source) {
            byte[] bytes = source.readAllBytes();
            ByteArrayOutputStream ascii = new ByteArrayOutputStream(bytes.length);
            for (byte b : bytes) {
                if (b >= 0) {
                    ascii.write(b);
                }
            }
            return new ByteArrayInputStream(ascii.toByteArray());
        }
    }
}
