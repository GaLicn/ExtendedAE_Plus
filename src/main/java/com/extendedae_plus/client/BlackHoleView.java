package com.extendedae_plus.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector3f;

/**
 * 黑洞着色器的相机，即 black_hole_geodesic.glsl 声明的四个 uView* uniform。
 *
 * <p>坐标系固定在黑洞上，吸积盘位于其 xz 平面内。头部黑洞的盘面与地面平行，故其坐标轴就是世界
 * 坐标轴；天空中的黑洞则从无穷远处以固定角度观看，坐标轴可任取，见 {@link #farAway}。</p>
 *
 * @param direction 由黑洞指向相机的单位向量
 * @param distance  相机到黑洞中心的距离，以黑洞质量 M 为单位（阴影半径为 3√3 M）；0 表示相机在
 *                  无穷远处
 * @param right     相机向右的单位向量，即像平面的横轴
 * @param up        相机向上的单位向量，即像平面的纵轴
 */
record BlackHoleView(Vector3f direction, float distance, Vector3f right, Vector3f up) {

    /** 从无穷远处、高出盘面 tiltDegrees 度看去，像平面横轴沿盘面方向：天空中的黑洞即如此。 */
    static BlackHoleView farAway(float tiltDegrees) {
        double tilt = Math.toRadians(tiltDegrees);
        float sin = (float) Math.sin(tilt);
        float cos = (float) Math.cos(tilt);
        return new BlackHoleView(new Vector3f(0.0F, sin, cos), 0.0F,
                new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, cos, -sin));
    }

    /** 写入引入 black_hole_geodesic.glsl 的着色器；每次绘制前都须调用。 */
    void apply(ShaderInstance shader) {
        shader.safeGetUniform("uViewDirection").set(direction);
        shader.safeGetUniform("uViewDistance").set(distance);
        shader.safeGetUniform("uViewRight").set(right);
        shader.safeGetUniform("uViewUp").set(up);
    }
}
