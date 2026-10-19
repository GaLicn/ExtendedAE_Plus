// Glitch bands shared by the two sky black holes, the smooth one in
// black_hole.fsh and the pixel-art one in black_hole_pixelated_composite.fsh.
// Both are cut into the same horizontal bands and torn along the same rows,
// so while a glitch burst swaps one black hole for the other, band by band,
// the two read as one corrupted picture.
//
// When the bursts come, and which of them switch to the smooth black hole and
// back, is worked out on the CPU in BlackHoleGlitch and handed in through the
// uniforms below; this file only turns that into bands. It also holds the
// integer hash both shaders use.
//
// The including shader must declare uTime and PLANE_HALF_EXTENT before
// importing this file.
//
// Keep every byte of this file ASCII. Some GL drivers reject GLSL source that
// contains anything else, comments included, which is also why all comments
// here are written in English.

// 1 while a glitch burst lasts, 0 otherwise.
uniform float uGlitch;
// Share of the bands that show the smooth black hole rather than the
// pixel-art one: 0 or 1 between bursts, rising or falling while a burst swaps
// the two over.
uniform float uSmoothShare;
// One texel of the pixel-art sprite, in shadow radii. Bands and tears are
// whole sprite texels in both black holes, so they line up.
uniform float uGlitchTexel;

// How many times per second the pattern of a burst is redrawn while it lasts.
const float GLITCH_FLICKER_RATE = 15.0;
// Tearing: the black hole is cut into bands of GLITCH_BAND_TEXELS rows, a
// share GLITCH_TEAR_SHARE of which are shifted sideways by up to
// GLITCH_TEAR_TEXELS during a burst.
const float GLITCH_BAND_TEXELS = 3.0;
const float GLITCH_TEAR_SHARE = 0.4;
const float GLITCH_TEAR_TEXELS = 5.0;

// lowbias32 integer hash by Chris Wellons (public domain).
uint hashUint(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float hashCell(vec2 cell, uint seed) {
    // The offset keeps lattice coordinates positive before the unsigned cast.
    uvec2 c = uvec2(ivec2(cell) + 32768);
    return float(hashUint(c.x ^ hashUint(c.y ^ seed)) >> 8u) * (1.0 / 16777216.0);
}

// The band a point of the plane (shadow radii) lies in.
float glitchBand(vec2 plane) {
    return floor((plane.y + PLANE_HALF_EXTENT.y) / (uGlitchTexel * GLITCH_BAND_TEXELS));
}

// Sideways shift of a band, in sprite texels. During a burst a share of the
// bands slide by at least one texel, so a torn band always shows; otherwise
// it is 0.
float glitchTear(float band) {
    float frame = floor(uTime * GLITCH_FLICKER_RATE);
    float torn = step(hashCell(vec2(band, frame), 227u), GLITCH_TEAR_SHARE) * uGlitch;
    float side = hashCell(vec2(band, frame), 229u) < 0.5 ? -1.0 : 1.0;
    return torn * side * (1.0 + floor(hashCell(vec2(band, frame), 231u) * GLITCH_TEAR_TEXELS));
}

// Whether a band shows the smooth black hole. Every band has its own
// threshold, redrawn with the burst pattern, so as the share rises the bands
// flip over in random order and flicker between the two while they do.
bool showsSmooth(float band) {
    return hashCell(vec2(band, floor(uTime * GLITCH_FLICKER_RATE)), 241u) < uSmoothShare;
}
