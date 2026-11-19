package com.extendedae_plus.client;

import net.minecraft.client.renderer.ShaderInstance;

/**
 * 像素风格黑洞的故障时间表。
 *
 * <p>像素风格黑洞在故障期间呈现画面撕裂效果。部分故障会将像素黑洞切换为原版平滑黑洞，保持一段
 * 时间后再切回像素黑洞。样式替换按故障进度逐条带进行，条带与撕裂的计算见
 * black_hole_glitch.glsl。每隔若干次故障，天空中的黑洞会在故障过半时重新定位到随机位置，见
 * BlackHoleSkyRenderer。</p>
 *
 * <p>时间表仅取决于着色器时间，因此可按帧直接计算，无需保存状态。渲染器可据此跳过当前帧不可见
 * 的黑洞，避免无效绘制。</p>
 */
final class BlackHoleGlitch {

    // 时间划分为固定窗口，每个窗口以 CHANCE 的概率在随机时刻发生一次故障，以避免故障按固定周期出现。
    // CHANCE 取 0 即关闭故障。着色器时间每 10000 秒循环一次，WINDOW_SECONDS
    // 必须整除 10000，否则时间表会在循环处跳变。
    private static final float WINDOW_SECONDS = 8.0F;
    private static final float CHANCE = 0.5F;
    // 单次故障的时长。
    private static final float BURST_SECONDS = 0.4F;
    // 故障中切换到原版平滑黑洞的概率。平滑黑洞保持 SMOOTH_SECONDS 秒后，再通过一次故障切回像素
    // 黑洞；切换过程包含两次故障及其间的保持阶段，三者总时长不得超过 WINDOW_SECONDS。
    private static final float SWITCH_CHANCE = 0.5F;
    private static final float SMOOTH_SECONDS = 4.0F;

    // burstPastHalfway 在不处于任何故障后半段时的返回值。
    static final int NO_BURST = -1;

    private BlackHoleGlitch() {
    }

    /**
     * 某一时刻的故障状态。
     *
     * @param bursting    是否正处于故障中
     * @param smoothShare 显示原版平滑黑洞的条带比例：0 为全部像素黑洞，1 为全部平滑黑洞
     */
    record State(boolean bursting, float smoothShare) {
        static final State PIXELATED = new State(false, 0.0F);
        static final State SMOOTH = new State(false, 1.0F);

        /** 写入 black_hole_glitch.glsl 声明的 uniform；引入该文件的着色器每次绘制前都须调用。 */
        void apply(ShaderInstance shader) {
            shader.safeGetUniform("uGlitch").set(bursting ? 1.0F : 0.0F);
            shader.safeGetUniform("uSmoothShare").set(smoothShare);
            // 条带与撕裂都以像素精灵的整纹素计，两个黑洞才能对齐。
            shader.safeGetUniform("uGlitchTexel").set(1.0F / BlackHolePixelSprite.PIXELS_PER_SHADOW_RADIUS);
        }
    }

    static State at(float time) {
        int window = (int) Math.floor(time / WINDOW_SECONDS);
        if (hash(window, 1, 223) >= CHANCE) {
            return State.PIXELATED;
        }
        boolean switching = hash(window, 2, 239) < SWITCH_CHANCE;
        float length = span(switching);
        float since = sinceStart(time, window, switching);
        if (since < 0.0F || since > length) {
            return State.PIXELATED;
        }
        if (!switching) {
            return new State(true, 0.0F);
        }
        // 第一次故障中平滑黑洞逐条带接管，第二次故障中逐条带交还。
        if (since < BURST_SECONDS) {
            return new State(true, since / BURST_SECONDS);
        }
        if (since < BURST_SECONDS + SMOOTH_SECONDS) {
            return State.SMOOTH;
        }
        return new State(true, 1.0F - (since - BURST_SECONDS - SMOOTH_SECONDS) / BURST_SECONDS);
    }

    /**
     * 本时刻所处故障的编号，仅在故障已进行过半时给出，否则为 {@link #NO_BURST}。天空黑洞渲染器根据
     * 编号变化统计已发生的故障次数，并按设定间隔在故障过半时重新定位黑洞。
     */
    static int burstPastHalfway(float time) {
        int window = (int) Math.floor(time / WINDOW_SECONDS);
        if (hash(window, 1, 223) >= CHANCE) {
            return NO_BURST;
        }
        boolean switching = hash(window, 2, 239) < SWITCH_CHANCE;
        float since = sinceStart(time, window, switching);
        if (since >= 0.5F * BURST_SECONDS && since <= BURST_SECONDS) {
            return 2 * window;
        }
        // 切换回像素黑洞的第二次故障，在平滑黑洞保持期之后。
        float second = since - BURST_SECONDS - SMOOTH_SECONDS;
        if (switching && second >= 0.5F * BURST_SECONDS && second <= BURST_SECONDS) {
            return 2 * window + 1;
        }
        return NO_BURST;
    }

    /** 窗口内的故障从开始到结束的总时长，切换时含两次故障与其间的保持期。 */
    private static float span(boolean switching) {
        return switching ? 2.0F * BURST_SECONDS + SMOOTH_SECONDS : BURST_SECONDS;
    }

    /** 距窗口内故障开始的秒数，故障开始前为负。 */
    private static float sinceStart(float time, int window, boolean switching) {
        float start = hash(window, 0, 211) * (WINDOW_SECONDS - span(switching));
        return time - window * WINDOW_SECONDS - start;
    }

    /** 与着色器中 hashCell 相同的格点哈希，取值 [0, 1)。 */
    private static float hash(int x, int y, int seed) {
        int h = lowbias32((x + 32768) ^ lowbias32((y + 32768) ^ seed));
        return (h >>> 8) * (1.0F / 16777216.0F);
    }

    /** Chris Wellons 的 lowbias32 整数哈希（公有领域）。 */
    private static int lowbias32(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }
}
