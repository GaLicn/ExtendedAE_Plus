package com.extendedae_plus.client;

import net.minecraft.client.renderer.ShaderInstance;

/**
 * 像素风格黑洞的故障时间表。
 *
 * <p>黑洞被当作一个被入侵的对象来画，不时像被写坏的画面一样撕裂。部分故障会把像素黑洞换成
 * 原版平滑黑洞，平滑黑洞保持一段时间后，再由一次故障换回像素黑洞。替换在故障期间逐条带进行，
 * 条带与撕裂的计算见 black_hole_glitch.glsl。每隔几次故障，天空中的黑洞还会在故障进行到一半时
 * 瞬移到天空中的随机位置，见 BlackHoleSkyRenderer。</p>
 *
 * <p>时间表只取决于着色器时间，故每帧直接求值即可，无需保存状态；渲染器据此还能跳过本帧完全
 * 看不见的那个黑洞，不必两个都画。</p>
 */
final class BlackHoleGlitch {

    // 时间被切成若干窗口，每个窗口以 CHANCE 的概率在其中随机时刻发生一次故障，故故障间隔不规则，
    // 不会踩着节拍出现。CHANCE 取 0 即关闭故障。着色器时间每 10000 秒循环一次，WINDOW_SECONDS
    // 必须整除 10000，否则时间表会在循环处跳变。
    private static final float WINDOW_SECONDS = 8.0F;
    private static final float CHANCE = 0.5F;
    // 单次故障的时长。
    private static final float BURST_SECONDS = 0.4F;
    // 故障中切换到原版平滑黑洞的比例。平滑黑洞保持 SMOOTH_SECONDS 秒后，再由一次故障切回像素
    // 黑洞；一进一出两次故障与其间的保持时间都落在同一个窗口内，故三者之和不得超过 WINDOW_SECONDS。
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
     * 本时刻所处的那次故障的编号，仅在故障已进行过半时给出，否则为 {@link #NO_BURST}。天空中的
     * 黑洞的渲染器据编号的变化清点经过的故障，每隔几次在故障过半时瞬移一次。
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
