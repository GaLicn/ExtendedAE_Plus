#version 150

// Composite pass for the pixel-art black hole: stamps the finished sprite,
// drawn by black_hole_pixelated and black_hole_pixelated_finish, onto the sky,
// or over the scene for the black hole over the head, with nearest-neighbour
// sampling, so each sprite texel stays one crisp block of screen pixels at any
// resolution, field of view or distance.
//
// Two effects are added on the way: the glitch layer, which now and then tears
// runs of texel rows sideways and throws magenta and cyan ghosts of the
// silhouette, and the dissolve that builds the sprite up texel by texel at
// dusk and takes it apart again in the rain. Every offset is a whole number of
// texels, so neither ever breaks the pixel grid.
//
// Some glitch bursts swap the sprite for the smooth black hole of
// black_hole.fsh and a later one swaps it back. During such a burst this pass
// leaves out the bands the smooth black hole has taken over; the bands, the
// tears and the schedule are shared with it through black_hole_glitch.glsl.
//
// vPlane is the position on the image plane in shadow radii; the sprite covers
// exactly -PLANE_HALF_EXTENT to +PLANE_HALF_EXTENT of it.
//
// Output is premultiplied: rgb is emitted light, alpha is how much of the sky
// behind it is blocked.
//
// Keep every byte of this file ASCII. Some GL drivers reject GLSL source that
// contains anything else, comments included, which is also why all comments
// here are written in English.

in vec2 vPlane;

// The sprite, one texel per pixel-art pixel, and its size in texels.
uniform sampler2D uSpriteTex;
uniform vec2 uSpriteTexels;
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
// ============================================================================

// ---- Glitch ----
// The black hole is drawn as a hacked object that now and then tears, as if a
// corrupted slice of the frame were written over it. The bursts are scheduled
// in BlackHoleGlitch; the bands and the tears are set in black_hole_glitch.glsl.
// Ghosts during a burst: copies of the silhouette this many texels to either
// side, drawn in one flat colour each where they stick out past the sprite.
// This is the pixel-art take on a channel split; splitting the actual colour
// channels would produce colours outside the palette.
const float GLITCH_GHOST_TEXELS = 2.0;
const vec3 GLITCH_GHOST_WARM = vec3(1.00, 0.16, 0.56);
const vec3 GLITCH_GHOST_COOL = vec3(0.10, 0.85, 1.00);

// ---- Dissolve ----
// While the black hole fades in and out, whole texels appear and vanish
// instead of the sprite turning translucent. The order is hashed and biased
// outwards by DISSOLVE_RADIAL_BIAS, so the shadow and the ring form first and
// the rim of the disk last.
const float DISSOLVE_RADIAL_BIAS = 0.75;

// ============================================================================
// Fixed values, normally no need to change
// ============================================================================

// Half the extent of the sprite in shadow radii: half its size in texels, times
// the size of one texel. The sky sprite and the head sprite differ in shape.
#define PLANE_HALF_EXTENT (0.5 * uSpriteTexels * uGlitchTexel)

#moj_import <extendedae_plus:black_hole_glitch.glsl>

// The sprite texel at an integer texel coordinate. texelFetch makes the
// lookup exact regardless of the texture's filter mode, and the clamp keeps
// torn rows from reading past the sprite, whose border is clear.
vec4 spriteTexel(vec2 texel) {
    return texelFetch(uSpriteTex, ivec2(clamp(texel, vec2(0.0), uSpriteTexels - 1.0)), 0);
}

void main() {
    // The sprite texel under this pixel.
    vec2 texel = floor((vPlane / PLANE_HALF_EXTENT * 0.5 + 0.5) * uSpriteTexels);

    // ---- Glitch ----
    // Bands the smooth black hole has taken over are left to it, and during a
    // burst some bands slide sideways by a whole number of texels.
    float band = glitchBand(vPlane);
    float pixelArt = showsSmooth(band) ? 0.0 : 1.0;
    vec2 source = texel + vec2(glitchTear(band), 0.0);

    vec4 sprite = spriteTexel(source);
    vec3 color = sprite.rgb;
    float occlusion = sprite.a;

    // Ghosts: where the texel is clear, a copy of the silhouette shifted one
    // way shows through in the warm colour and one shifted the other way in
    // the cool colour. The ghosts add light only, so they never block the sky.
    if (uGlitch > 0.0 && sprite.a < 0.5) {
        vec2 offset = vec2(GLITCH_GHOST_TEXELS, 0.0);
        if (spriteTexel(source - offset).a >= 0.5) {
            color = GLITCH_GHOST_WARM;
        } else if (spriteTexel(source + offset).a >= 0.5) {
            color = GLITCH_GHOST_COOL;
        }
    }

    // ---- Dissolve ----
    // A texel is shown once the visibility passes its place in the order. The
    // order is fixed per texel, so the sprite assembles steadily instead of
    // flickering as the brightness changes from tick to tick.
    float outwards = length((texel + 0.5) / uSpriteTexels - 0.5) * 2.0;
    float order = mix(hashCell(texel, 233u), clamp(outwards, 0.0, 1.0), DISSOLVE_RADIAL_BIAS);
    float shown = step(order, uVisibility * 1.001);

    // ---- Horizon ----
    // Tested on the continuous plane position rather than on the texel, so the
    // black hole sets behind the dark lower sky on a clean line instead of a
    // staircase.
    float height = dot(uHorizon.xy, vPlane) + uHorizon.z;
    float aboveHorizon = clamp((height - uHorizonClip) / max(fwidth(height), 1.0e-6), 0.0, 1.0);

    float fade = pixelArt * shown * aboveHorizon;
    color *= fade;
    occlusion *= fade;
    if (occlusion < 0.002 && max(color.r, max(color.g, color.b)) < 0.002) {
        discard;
    }
    fragColor = vec4(color, occlusion);
}
