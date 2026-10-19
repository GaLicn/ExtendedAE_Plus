#version 150

// Finishing pass for the pixel-art black hole sprite. It runs at sprite
// resolution, one fragment per texel, between black_hole_pixelated, which
// draws the raw sprite, and black_hole_pixelated_composite, which stamps the
// finished sprite onto the sky.
//
// It does the jobs a pixel artist does by hand once a sprite is blocked in,
// all of which need to look at neighbouring texels:
//
// - Gap filling. A texel of clear sky or of shadow one texel wide between two
//   lit texels, such as the gaps between the photon ring and the inner edge
//   of the disk, takes the colour of one of them. Left alone, the outline
//   would turn each into a dark speck on the edge of the shadow.
// - Orphan cleanup. A solid texel whose colour none of its eight neighbours
//   shares is a stray pixel left by the band and hue thresholds. It takes the
//   colour most of its neighbours have instead, so every colour sits in a
//   clump of at least two texels.
// - Rim. Lit texels touching the shadow are painted in one bright rim colour,
//   so the hole stands out from the disk all the way round, even where the
//   gas next to it is dim.
// - Outline and glow. Clear texels next to the lit silhouette are filled with
//   a dark outline colour, and the ring of texels beyond that with a fainter
//   glow colour, so the light steps down twice and stops, the way a sprite
//   carries its own glow.
//
// Output is premultiplied, like the sprite: rgb is emitted light, alpha is how
// much of the sky behind it is blocked. The outline and the glow only add
// light; they never block the sky.
//
// Keep every byte of this file ASCII. Some GL drivers reject GLSL source that
// contains anything else, comments included, which is also why all comments
// here are written in English.

// The raw sprite and its size in texels.
uniform sampler2D uSpriteTex;
uniform vec2 uSpriteTexels;

out vec4 fragColor;

// ============================================================================
// Tunable parameters
// ============================================================================

// Colour of the outline around the lit silhouette, and of the fainter ring of
// glow outside it. Set GLOW_COLOR to black to leave only the outline.
const vec3 OUTLINE_COLOR = vec3(0.30, 0.06, 0.48);
const vec3 GLOW_COLOR = vec3(0.16, 0.04, 0.30);
// Colour of the lit texels that touch the shadow.
const vec3 RIM_COLOR = vec3(1.00, 1.00, 0.94);

// ============================================================================
// Helpers
// ============================================================================

// The raw sprite texel at an integer texel coordinate; outside the sprite it
// reads as clear sky.
vec4 rawTexel(vec2 texel) {
    if (any(lessThan(texel, vec2(0.0))) || any(greaterThanEqual(texel, uSpriteTexels))) {
        return vec4(0.0);
    }
    return texelFetch(uSpriteTex, ivec2(texel), 0);
}

bool sameColour(vec4 a, vec4 b) {
    vec4 d = abs(a - b);
    return max(max(d.r, d.g), max(d.b, d.a)) < 0.02;
}

// A texel that blocks the sky: the disk, the ring or the black shadow. Stars
// and clear sky only add light, so they are never cleaned up.
bool isSolid(vec4 texel) {
    return texel.a >= 0.5;
}

// A solid texel that is not black: the disk or the ring.
bool isLit(vec4 texel) {
    return isSolid(texel) && max(texel.r, max(texel.g, texel.b)) >= 0.05;
}

// A solid black texel: the shadow.
bool isShadow(vec4 texel) {
    return isSolid(texel) && !isLit(texel);
}

// The texel after gap filling and orphan cleanup. A texel that is not lit but
// has lit texels on both sides, above and below or left and right, takes the
// colour of the one above or on the right. A solid texel that shares its
// colour with none of its eight neighbours takes the colour two of its four
// direct neighbours agree on, or failing that the colour of the one on its
// left.
vec4 cleanTexel(vec2 texel) {
    vec4 centre = rawTexel(texel);
    vec4 east = rawTexel(texel + vec2(1.0, 0.0));
    vec4 west = rawTexel(texel - vec2(1.0, 0.0));
    vec4 north = rawTexel(texel + vec2(0.0, 1.0));
    vec4 south = rawTexel(texel - vec2(0.0, 1.0));
    if (!isLit(centre)) {
        if (isLit(north) && isLit(south)) {
            return north;
        }
        if (isLit(east) && isLit(west)) {
            return east;
        }
    }
    if (!isSolid(centre)) {
        return centre;
    }
    if (sameColour(centre, east) || sameColour(centre, west)
            || sameColour(centre, north) || sameColour(centre, south)
            || sameColour(centre, rawTexel(texel + vec2(1.0, 1.0)))
            || sameColour(centre, rawTexel(texel + vec2(-1.0, 1.0)))
            || sameColour(centre, rawTexel(texel + vec2(1.0, -1.0)))
            || sameColour(centre, rawTexel(texel + vec2(-1.0, -1.0)))) {
        return centre;
    }
    if (sameColour(east, north) || sameColour(east, south) || sameColour(east, west)) {
        return east;
    }
    if (sameColour(north, south) || sameColour(north, west)) {
        return north;
    }
    if (sameColour(south, west)) {
        return south;
    }
    return west;
}

// Whether a texel, after cleanup, is part of the lit silhouette the outline
// wraps: solid and not black, so the shadow itself is never outlined.
float litAt(vec2 texel) {
    return isLit(cleanTexel(texel)) ? 1.0 : 0.0;
}

bool shadowAt(vec2 texel) {
    return isShadow(cleanTexel(texel));
}

void main() {
    vec2 texel = floor(gl_FragCoord.xy);
    vec4 colour = cleanTexel(texel);

    if (isLit(colour) && (shadowAt(texel + vec2(1.0, 0.0)) || shadowAt(texel - vec2(1.0, 0.0))
            || shadowAt(texel + vec2(0.0, 1.0)) || shadowAt(texel - vec2(0.0, 1.0)))) {
        colour = vec4(RIM_COLOR, 1.0);
    }

    // Only clear texels take the outline, so it wraps the silhouette from
    // outside and never paints over it, nor over a star.
    if (!isSolid(colour) && max(colour.r, max(colour.g, colour.b)) < 0.05) {
        float touching = max(max(litAt(texel + vec2(1.0, 0.0)), litAt(texel - vec2(1.0, 0.0))),
                max(litAt(texel + vec2(0.0, 1.0)), litAt(texel - vec2(0.0, 1.0))));
        if (touching > 0.0) {
            colour = vec4(OUTLINE_COLOR, 0.0);
        } else {
            float near = max(max(max(litAt(texel + vec2(2.0, 0.0)), litAt(texel - vec2(2.0, 0.0))),
                    max(litAt(texel + vec2(0.0, 2.0)), litAt(texel - vec2(0.0, 2.0)))),
                    max(max(litAt(texel + vec2(1.0, 1.0)), litAt(texel - vec2(1.0, 1.0))),
                    max(litAt(texel + vec2(1.0, -1.0)), litAt(texel - vec2(1.0, -1.0)))));
            colour = vec4(GLOW_COLOR * near, 0.0);
        }
    }
    fragColor = colour;
}
