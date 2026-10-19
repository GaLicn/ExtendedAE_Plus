#version 150

// The real black hole in the night sky.
//
// vPlane is the position on a tangent plane of the sky in shadow radii, so the
// shadow edge is the unit circle. The black hole itself, modelled on Gargantua
// from the film Interstellar, is drawn by black_hole_real.glsl from rays
// traced by black_hole_geodesic.glsl, here from a camera infinitely far away
// at a fixed angle. This file adds what lies behind it: a procedural star
// field, bent by the same deflection as the disk light, so the stars near the
// hole are dragged into arcs and, just outside the photon ring, into rings.
//
// Output is premultiplied: rgb is emitted light, alpha is how much of the sky
// behind it is blocked.
//
// When the pixel-art black hole is selected, this one only shows between the
// two glitch bursts that swap it in and out, and during those bursts only in
// the bands it has taken over, torn the same way as the pixel-art one; see
// black_hole_glitch.glsl. Otherwise uSmoothShare is 1 and uGlitch 0, and all
// of it shows.
//
// Keep every byte of this file ASCII. Some GL drivers reject GLSL source that
// contains anything else, comments included, which is also why all comments
// here are written in English.

in vec2 vPlane;

uniform float uTime;
uniform float uVisibility;
// World height, at unit distance, of the direction through a point of the
// plane: xy per shadow radius along each axis, z at the centre.
uniform vec3 uHorizon;
// Height, in the same units, below which the sky is hidden. Vanilla hides the
// lower sky behind a dark plane while the player is below the horizon; the
// rest of the time this is far below and only terrain covers the black hole.
uniform float uHorizonClip;

out vec4 fragColor;

// ============================================================================
// Tunable parameters
//
// The black hole itself is tuned in black_hole_real.glsl.
// ============================================================================

// ---- Lensed star field ----
// Each pixel samples the star field at the angle the ray would have come from
// far away; how strongly the lens drags it is LENS_STRENGTH in
// black_hole_geodesic.glsl. STAR_* configure the procedural field.
const float STAR_BRIGHTNESS = 1.5;
// Star field: lattice cells per shadow radius, fraction of cells that carry a
// star, sharpness of a star's core inside its cell, and its colour. The fade
// pair dims the field once a cell approaches pixel size, which keeps distant
// stars from shimmering.
const float STAR_DENSITY = 7.0;
const float STAR_THRESHOLD = 0.965;
const float STAR_CORE_SHARPNESS = 90.0;
const float STAR_FADE_START = 0.35;
const float STAR_FADE_END = 0.75;
const vec3 STAR_COLOR = vec3(0.85, 0.90, 1.0);
// Fade the bent sky out where the quad ends, so the takeover of the vanilla
// star field is not visible as an edge.
const float STAR_SKY_OPACITY = 1.0;

// ---- Framing ----
// Fraction of the half extent from which the sky quad fades out to transparent.
const float WINDOW_FADE_START = 0.75;

// ============================================================================
// Fixed values, normally no need to change
// ============================================================================

// Must match BlackHoleSkyRenderer.PLANE_HALF_WIDTH / PLANE_HALF_HEIGHT.
const vec2 PLANE_HALF_EXTENT = vec2(7.0, 4.0);

// Glitch bands, and the hash used throughout.
#moj_import <extendedae_plus:black_hole_glitch.glsl>
// The camera, the ray integration and the lens.
#moj_import <extendedae_plus:black_hole_geodesic.glsl>
// The black hole itself.
#moj_import <extendedae_plus:black_hole_real.glsl>

// The procedural star field, sampled at a sky position in shadow radii. One
// star sits in each lattice cell at a hashed offset, so the field is fixed on
// the sky rather than in screen space. cellsPerPixel fades a cell out once it
// shrinks towards a pixel, which keeps distant stars from shimmering.
float starField(vec2 p, vec2 cellsPerPixel) {
    float detail = 1.0 - smoothstep(STAR_FADE_START, STAR_FADE_END, max(cellsPerPixel.x, cellsPerPixel.y));
    if (detail <= 0.0) {
        return 0.0;
    }
    vec2 grid = p * STAR_DENSITY;
    vec2 cell = floor(grid);
    uvec2 c = uvec2(ivec2(cell) + 32768);
    uint seed = hashUint(c.x ^ hashUint(c.y ^ 101u));
    if (float(seed >> 8u) * (1.0 / 16777216.0) < STAR_THRESHOLD) {
        return 0.0;
    }
    // Offset of the star inside its cell, from two further hashes of the seed.
    vec2 offset = vec2(
            float(hashUint(seed ^ 0x9e3779b9u) >> 8u),
            float(hashUint(seed ^ 0x85ebca6bu) >> 8u)) * (1.0 / 16777216.0);
    vec2 delta = grid - cell - offset;
    return exp(-dot(delta, delta) * STAR_CORE_SHARPNESS) * detail;
}

void main() {
    // Bands left to the pixel-art black hole are not drawn, and during a burst
    // some bands are torn sideways. The tear moves only the picture: the
    // horizon is tested where the fragment really is.
    float band = glitchBand(vPlane);
    float shown = showsSmooth(band) ? 1.0 : 0.0;
    vec2 q = vPlane + vec2(glitchTear(band) * uGlitchTexel, 0.0);
    float rho = length(q);
    float height = dot(uHorizon.xy, vPlane) + uHorizon.z;
    float aboveHorizon = clamp((height - uHorizonClip) / max(fwidth(height), 1.0e-6), 0.0, 1.0);

    vec4 hole = realBlackHole(q, aboveHorizon > 0.0 && shown > 0.0);

    // The background sky is bent by the same deflection the disk light obeys.
    // A ray seen at rho would have come from sourceRadius on the sky; where
    // that value turns negative the ray has wrapped past half a turn and
    // samples the opposite side, which is what closes the field into rings
    // around the shadow. The shadow and the disk images both occlude it.
    float sourceRadius = rho - LENS_STRENGTH * deflectionAngle(rho);
    vec2 skyPoint = q / max(rho, 1.0e-4) * sourceRadius;
    vec2 skyCellsPerPixel = fwidth(skyPoint) * STAR_DENSITY;
    vec3 skyLight = STAR_COLOR * (starField(skyPoint, skyCellsPerPixel) * STAR_BRIGHTNESS * STAR_SKY_OPACITY);

    vec3 color = toneMap(hole.rgb + skyLight * (1.0 - hole.a));
    float occlusion = hole.a;

    float window = 1.0 - smoothstep(WINDOW_FADE_START, 1.0, length(q / PLANE_HALF_EXTENT));
    float fade = window * aboveHorizon * uVisibility * shown;
    color *= fade;
    occlusion *= fade;
    if (occlusion < 0.002 && max(color.r, max(color.g, color.b)) < 0.002) {
        discard;
    }
    fragColor = vec4(color, occlusion);
}
