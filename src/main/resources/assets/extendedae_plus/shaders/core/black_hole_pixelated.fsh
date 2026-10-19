#version 150

// Pixel-art black hole for the night sky, drawn as a sprite.
//
// This pass renders the black hole into a small offscreen target at one texel
// per pixel-art pixel. black_hole_pixelated_finish then cleans up stray texels
// and adds the outline and the glow, and black_hole_pixelated_composite stamps
// the result onto the sky with nearest-neighbour sampling and adds the glitch
// layer; both need to read neighbouring texels, which this pass cannot. The
// grid is fixed to the sprite rather than to the screen, so the pixels hold
// still while the camera turns and while the black hole crosses the sky.
//
// The geometry and the ray integration follow black_hole.fsh. The shading does
// not: instead of a tone-mapped gradient, every texel is assigned one flat
// colour from a small hand-picked palette, the way a pixel artist would paint
// it. Brightness is cut into a few bands with hard edges. The approaching side
// of the disk, blue-shifted, is painted in the cool ramp and the receding side
// in the warm one, with a violet ramp between them. There is no dither: every
// colour sits in a solid block.
//
// vPlane is the position on the image plane in shadow radii, so seen from far
// away the shadow edge is the unit circle. Every fragment of this pass is
// exactly one texel, so vPlane is a texel centre and screen-space derivatives
// measure one texel. The rays are traced from the camera that the uniforms of
// black_hole_geodesic.glsl describe: far away at a fixed angle for the sky,
// close by for the black hole over the head, which is drawn as an object in
// the world with its disk level with the ground.
//
// Output is premultiplied: rgb is emitted light, alpha is how much of the sky
// behind it is blocked. Alpha is 0 or 1, as in a sprite.
//
// Keep every byte of this file ASCII. Some GL drivers reject GLSL source that
// contains anything else, comments included, which is also why all comments
// here are written in English.

in vec2 vPlane;

uniform float uTime;
// 1 draws the lensed star field this pass generates for the sky. The
// head-mounted black hole is stamped over the real scene, which already holds
// the stars and the terrain, so it passes 0 and lets the scene show through.
uniform float uStarField;

out vec4 fragColor;

// ============================================================================
// Tunable parameters
//
// "Shadow radius" is the radius of the black disk of the black hole. Disk
// radii are measured in units of the black hole mass M, and the shadow radius
// is about 5.2 M. The number of texels per shadow radius is set by the size of
// the offscreen target, BlackHoleSkyRenderer.PIXELS_PER_SHADOW_RADIUS.
// ============================================================================

// ---- Shape ----
// Inner and outer disk radius (M). A smaller inner radius brings the disk
// closer to the black hole; the outer radius sets how far it reaches sideways.
const float DISK_INNER_RADIUS = 4.5;
const float DISK_OUTER_RADIUS = 18.0;
// Fraction of the outer radius at which the disk starts to fade out.
const float OUTER_EDGE_START = 0.7;
// Brightness scale of the far half of the disk, the part behind the black hole
// whose light gravity bends up over the shadow and down under it. Below 1 the
// two arcs come out thinner and dimmer than the near side. The dimming follows
// the position on the disk only, so it fades in smoothly across the whole far
// half; black_hole.fsh dims just the stretch straight above and below the
// shadow, which at pixel scale drops whole bands there and leaves a notch.
const float FAR_SIDE_BRIGHTNESS = 0.8;
// Brightness scale of the second image, the other face of the far half, bent
// round under the shadow. It is seen through the lower part of the shadow's
// outline, so a bright one fills the black disk in from below and the hole no
// longer reads as a hole; dimmer keeps it a thin rim under the photon ring.
const float UNDERSIDE_BRIGHTNESS = 0.45;

// ---- Brightness ----
// Disk brightness, and the power by which brightness falls off outwards
// (larger makes the inner ring brighter and the outer ring dimmer).
const float DISK_BRIGHTNESS = 5.0;
const float DISK_BRIGHTNESS_POWER = 1.0;
// Doppler beaming. Gas on a circular orbit moves at beta = sqrt(M / (r - 2M)),
// so the side turning towards the viewer is brightened and the receding side
// is dimmed. BEAMING_POWER is the exponent of the Doppler factor: 4 is the
// relativistically correct value for bolometric intensity, but 2 keeps both
// sides inside the band range, and BEAMING_STRENGTH damps it further.
const float BEAMING_POWER = 2.0;
const float BEAMING_STRENGTH = 0.45;
// Share of the beaming that also sets where the disk ends. The rest only
// brightens and recolours the gas: at full share the approaching side would
// reach far past the receding one, at zero the two would be the same size.
const float SHAPE_BEAMING = 0.35;
// "Heat" falls off outwards; HEAT_POWER is the power of that falloff. The cool
// outer gas is dimmer by up to HEAT_DIM at the rim, which is what keeps the
// disk a thin bright band with a darker rim instead of a solid blob of colour.
const float HEAT_POWER = 0.85;
const float HEAT_DIM = 0.45;
// Speed at which brightness rises from the inner edge. Larger sharpens it.
const float INNER_RISE_SPEED = 2.5;

// ---- Bands ----
// Brightness, after tone mapping, at which a texel steps up to the next band
// of the palette. Gas dimmer than BAND_1 is not drawn at all, so the disk ends
// on a hard edge the finishing pass can outline.
const float BAND_1 = 0.28;
const float BAND_2 = 0.50;
const float BAND_3 = 0.66;
const float BAND_4 = 0.82;
const float BAND_5 = 0.94;

// ---- Palette ----
// Each ramp runs from the deep shade at the outer rim to the near-white core.
// The cool ramp paints the approaching (blue-shifted) side of the disk, the
// warm ramp the receding side, and the violet ramp the gas between them, which
// moves across the line of sight. The ramp follows the direction the gas
// moves in, from -1 straight away from the viewer to 1 straight towards:
// within HUE_VIOLET_HALF_WIDTH of zero it is violet. Going round the disk the
// colours therefore cycle cool, violet, warm, violet. The beaming factor is
// not used for this: on the far side, where the gas moves across the line of
// sight, it stays close to any threshold over a wide area.
//
// Violet is the main colour and cool and warm the accents on either side, so
// the violet ramp covers most of the disk. All three ramps share their
// deepest shade, RAMP_DEEP, which wraps the whole disk in one colour at its
// outer edge.
const vec3 RAMP_DEEP = vec3(0.40, 0.11, 0.66);
const vec3 WARM_2 = vec3(0.92, 0.14, 0.52);
const vec3 WARM_3 = vec3(1.00, 0.45, 0.50);
const vec3 WARM_4 = vec3(1.00, 0.80, 0.62);
const vec3 VIOLET_2 = vec3(0.64, 0.26, 0.96);
const vec3 VIOLET_3 = vec3(0.84, 0.52, 1.00);
const vec3 VIOLET_4 = vec3(0.95, 0.86, 1.00);
const vec3 COOL_2 = vec3(0.18, 0.52, 1.00);
const vec3 COOL_3 = vec3(0.30, 0.88, 1.00);
const vec3 COOL_4 = vec3(0.82, 1.00, 1.00);
const vec3 HOT_CORE = vec3(1.00, 1.00, 0.94);
const float HUE_VIOLET_HALF_WIDTH = 0.45;
// The photon ring, one colour all the way round so it outlines the shadow.
const vec3 RING_COLOR = vec3(0.92, 1.00, 1.00);
// The lensed stars, drawn as single texels, and the arms of the brightest ones
// while they twinkle.
const vec3 STAR_COLOR = vec3(0.92, 0.96, 1.00);
const vec3 STAR_ARM_COLOR = vec3(0.45, 0.62, 1.00);

// ---- Gas texture ----
// Clumps of gas: DISK_CELLS around one orbit and DISK_RADIAL_CELLS per unit of
// log radius, interpolated smoothly in both directions so no seam runs round
// the disk. The radial cells are few because the disk, seen nearly edge on, is
// only a few texels deep. SPIRAL_WINDING shifts the clumps round the orbit by
// that many cells per unit of log radius, so they line up into trailing arms.
const float DISK_CELLS = 17.0;
const float DISK_RADIAL_CELLS = 2.8;
const float SPIRAL_WINDING = 3.8;
// How far the clumps push the brightness up and down. They move the band
// edges, so the gas shows as bands that bulge and recede as the disk turns.
// More gain shows the turning more clearly but breaks the bands into smaller,
// busier blocks.
const float DENSITY_BASE = 0.66;
const float DENSITY_GAIN = 0.34;
// Rotation. The gas at the inner edge goes round once every INNER_TURN_SECONDS
// and the angular speed falls off outwards as (inner radius / r) to the power
// ROTATION_FALLOFF, so the whole disk turns as one body whose inner part runs
// ahead, with no seam between parts turning at different rates.
const float INNER_TURN_SECONDS = 10.0;
const float ROTATION_FALLOFF = 1.0;
// A disk turning faster inside than outside winds any pattern on it into ever
// tighter spirals. To keep the clumps the right shape, the texture is carried
// round for FLOW_CYCLE_SECONDS and then handed over to a fresh copy that has
// been running half a cycle behind it; the two are cross-faded, so the gas
// keeps flowing without a jump. uTime wraps every 10000 seconds, so
// FLOW_CYCLE_SECONDS must divide 10000, or the texture jumps at the wrap.
const float FLOW_CYCLE_SECONDS = 8.0;
// Fades the texture to its mean once a cell approaches texel size along the
// orbit, which keeps it from breaking into single texels where the lens
// squeezes the disk into the thin arcs over and under the shadow. Those arcs
// therefore stay smooth bands.
const float NOISE_FADE_START = 0.25;
const float NOISE_FADE_END = 0.5;

// ---- Lensed star field ----
// The background sky is bent by the same deflection as the disk light, so the
// stars close to the hole are dragged into arcs around it. Each texel looks up
// the sky at the angle its ray came from; a star lights only the texel whose
// patch of sky contains it, so it is one texel when unlensed and an arc of
// texels where the lens stretches it. Only the stars within STAR_FIELD_RADIUS
// (shadow radii) are drawn, since further out the lensing is too weak to see
// and the vanilla stars behind take over. How strongly the lens drags them is
// LENS_STRENGTH in black_hole_geodesic.glsl.
const float STAR_DENSITY = 4.0;
const float STAR_THRESHOLD = 0.9;
const float STAR_FIELD_RADIUS = 2.6;
// Share of stars that twinkle into a four-armed pixel sparkle, and how long a
// twinkle cycle takes. Each star has its own phase and is lit up for about a
// fifth of its cycle. uTime wraps every 10000 seconds, so STAR_TWINKLE_SECONDS
// must divide 10000.
const float STAR_SPARKLE_SHARE = 0.35;
const float STAR_TWINKLE_SECONDS = 4.0;
// Largest patch of sky, in lattice cells, that a texel may cover and still
// show a star. Right at the shadow edge the lens squeezes the whole sky into a
// few texels, which would otherwise light them all as one white clump.
const float STAR_MAX_PATCH = 0.6;

// ============================================================================
// Fixed values, normally no need to change
// ============================================================================

// The camera, the ray integration and the lens, shared with the real black
// hole.
#moj_import <extendedae_plus:black_hole_geodesic.glsl>

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

// Value noise that repeats every DISK_CELLS cells along x.
float orbitNoise(vec2 p, uint seed) {
    vec2 cell = floor(p);
    vec2 f = p - cell;
    vec2 w = f * f * (3.0 - 2.0 * f);
    float x0 = cell.x - DISK_CELLS * floor(cell.x / DISK_CELLS);
    float x1 = x0 + 1.0 > DISK_CELLS - 0.5 ? 0.0 : x0 + 1.0;
    float a = hashCell(vec2(x0, cell.y), seed);
    float b = hashCell(vec2(x1, cell.y), seed);
    float c = hashCell(vec2(x0, cell.y + 1.0), seed);
    float d = hashCell(vec2(x1, cell.y + 1.0), seed);
    return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
}

// The gas at a point of the disk, in [0, 1] around a mean of 0.5. turns is the
// polar angle in turns, logRadius the log of the radius over the inner radius,
// r the radius itself and cellsPerTexel the per-texel change of the angle in
// cells.
//
// Each of the two copies of the texture has been carried round by its angular
// speed for the time since it was last renewed. A copy is renewed, with a
// fresh offset, at the moment its weight reaches zero, so no renewal shows.
float flowingGas(float turns, float logRadius, float r, float cellsPerTexel) {
    float detail = 1.0 - smoothstep(NOISE_FADE_START, NOISE_FADE_END, cellsPerTexel);
    if (detail <= 0.0) {
        return 0.5;
    }
    // The disk turns with its left side coming towards the viewer, which is
    // towards decreasing polar angle, so the texture is read further along.
    float turnsPerSecond = pow(DISK_INNER_RADIUS / r, ROTATION_FALLOFF) / INNER_TURN_SECONDS;
    vec2 base = vec2(turns * DISK_CELLS - logRadius * SPIRAL_WINDING, logRadius * DISK_RADIAL_CELLS);
    float cycles = uTime / FLOW_CYCLE_SECONDS;
    float gas = 0.0;
    float weightSquared = 0.0;
    for (int copy = 0; copy < 2; copy++) {
        float phase = cycles + 0.5 * float(copy);
        float age = fract(phase);
        float weight = 1.0 - abs(2.0 * age - 1.0);
        // A fresh angular offset for every renewal, from the renewal count.
        float renewal = hashCell(vec2(floor(phase), float(copy)), 59u) * DISK_CELLS;
        vec2 coord = base + vec2(turnsPerSecond * age * FLOW_CYCLE_SECONDS * DISK_CELLS + renewal, 0.0);
        gas += weight * (orbitNoise(coord, 23u + uint(copy) * 18u) - 0.5);
        weightSquared += weight * weight;
    }
    // Two noises averaged are flatter than one; rescaling by the summed weight
    // keeps the contrast steady through the hand-over.
    return 0.5 + detail * gas / sqrt(max(weightSquared, 1.0e-4));
}

// Per-texel change of the polar angle of p.
float angleFootprint(vec2 p) {
    vec2 px = dFdx(p);
    vec2 py = dFdy(p);
    return (abs(p.x * px.y - p.y * px.x) + abs(p.x * py.y - p.y * py.x)) / max(dot(p, p), 1.0e-4);
}

// Whether the patch of sky a texel sees, centred on p with half size halfSpan
// (shadow radii), contains a star: 1 for the texel holding the star, 0.5 for
// the texels that form the arms of a twinkling one, 0 otherwise. One star sits
// in each occupied lattice cell at a hashed offset.
float starAt(vec2 p, vec2 halfSpan) {
    if (2.0 * max(halfSpan.x, halfSpan.y) * STAR_DENSITY > STAR_MAX_PATCH) {
        return 0.0;
    }
    vec2 home = floor(p * STAR_DENSITY);
    float lit = 0.0;
    // The patch can reach into the neighbouring cells.
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            vec2 cell = home + vec2(float(i), float(j));
            if (hashCell(cell, 101u) < STAR_THRESHOLD) {
                continue;
            }
            vec2 star = (cell + vec2(hashCell(cell, 103u), hashCell(cell, 107u))) / STAR_DENSITY;
            vec2 d = abs(star - p);
            lit = max(lit, step(d.x, halfSpan.x) * step(d.y, halfSpan.y));
            // The arms: the four texels beside the star's own, while it is lit
            // up. Only where the lens leaves the star unstretched, since a
            // smeared star is already an arc.
            float phase = fract(uTime / STAR_TWINKLE_SECONDS + hashCell(cell, 109u));
            if (hashCell(cell, 113u) < STAR_SPARKLE_SHARE && phase < 0.2) {
                vec2 arm = abs(d - 2.0 * halfSpan);
                float horizontal = step(arm.x, halfSpan.x) * step(d.y, halfSpan.y);
                float vertical = step(d.x, halfSpan.x) * step(arm.y, halfSpan.y);
                lit = max(lit, 0.5 * max(horizontal, vertical));
            }
        }
    }
    return lit;
}

// Brightness after tone mapping (x), direction of motion along the line of
// sight (y, -1 away from the viewer to 1 towards) and whether the texel is on
// the disk at all (z) for one image of the disk. p is the point on the disk
// as viewedPoint gives it, angleTexel the per-texel change of its polar
// angle, radiusTexel the per-texel change of its radius and gain a brightness
// scale for the image.
vec3 diskSample(vec2 p, float angleTexel, float radiusTexel, float gain) {
    float r = length(p);
    // Only the centre of the texel decides whether it is on the disk: a hard
    // edge, as a sprite has. The span widens the test by up to half a texel
    // where the image is squeezed thinner than one, so the thin lensed arcs
    // stay continuous lines instead of breaking up.
    float slack = 0.5 * min(radiusTexel, DISK_INNER_RADIUS);
    if (r + slack < DISK_INNER_RADIUS || r - slack > DISK_OUTER_RADIUS) {
        return vec3(0.0);
    }
    r = clamp(r, DISK_INNER_RADIUS, DISK_OUTER_RADIUS);

    float closeness = DISK_INNER_RADIUS / r;
    // The gas is laid out by the angle on the disk itself, not on the picture,
    // so it stays put while the camera walks round the black hole.
    float gas = flowingGas(atan(p.y, p.x) / TAU + viewedFrameTurns(), log(r / DISK_INNER_RADIUS), r,
            angleTexel * DISK_CELLS / TAU);
    float density = DENSITY_BASE + DENSITY_GAIN * (gas - 0.5) * 2.0;

    // Doppler beaming of the orbiting gas; the side moving towards the viewer
    // (the left one here) is boosted and the receding side is dimmed.
    float motion = sideView();
    float approach = -p.x / r * sqrt(closeness) * motion;
    float beta = clamp(sqrt(1.0 / max(r - 2.0, 0.05)), 0.0, 0.95);
    float gamma = 1.0 / sqrt(1.0 - beta * beta);
    float doppler = 1.0 / (gamma * (1.0 - beta * approach));
    float beaming = mix(1.0, pow(doppler, BEAMING_POWER), BEAMING_STRENGTH);

    float heat = pow(closeness, HEAT_POWER);
    float innerRise = 1.0 - exp(-(r - DISK_INNER_RADIUS) * INNER_RISE_SPEED);
    float outerFade = 1.0 - smoothstep(DISK_OUTER_RADIUS * OUTER_EDGE_START, DISK_OUTER_RADIUS, r);
    float farSide = smoothstep(-0.2, 0.6, -p.y / max(length(p), 1.0e-4)) * motion;
    float radiance = DISK_BRIGHTNESS * pow(closeness, DISK_BRIGHTNESS_POWER) * density
            * mix(HEAT_DIM, 1.0, smoothstep(0.0, 1.0, heat)) * innerRise * outerFade
            * mix(1.0, FAR_SIDE_BRIGHTNESS, farSide) * gain;
    // The outline of the disk takes only a share of the beaming, so the
    // approaching side is a little larger rather than far larger; inside that
    // outline the full beaming sets the band, never dropping below the first.
    float outline = 1.0 - exp(-radiance * mix(1.0, beaming, SHAPE_BEAMING));
    if (outline < BAND_1) {
        return vec3(0.0);
    }
    return vec3(max(1.0 - exp(-radiance * beaming), BAND_1), -p.x / max(length(p), 1.0e-4) * motion, 1.0);
}

// One step of one ramp: ramp 0 is warm, 1 violet and 2 cool; step 1 is the
// deepest shade, shared by all three, and 4 the brightest.
vec3 rampColor(int ramp, int step) {
    if (step <= 1) {
        return RAMP_DEEP;
    }
    if (ramp == 0) {
        return step >= 4 ? WARM_4 : (step == 3 ? WARM_3 : WARM_2);
    }
    if (ramp == 1) {
        return step >= 4 ? VIOLET_4 : (step == 3 ? VIOLET_3 : VIOLET_2);
    }
    return step >= 4 ? COOL_4 : (step == 3 ? COOL_3 : COOL_2);
}

// One flat palette colour for a disk texel: its band from the brightness, its
// ramp from the direction of motion. Returns alpha 0 where the texel is clear
// sky.
vec4 diskColor(vec3 disk) {
    float level = disk.x;
    if (disk.z <= 0.0) {
        return vec4(0.0);
    }
    if (level >= BAND_5) {
        return vec4(HOT_CORE, 1.0);
    }
    int ramp = disk.y < -HUE_VIOLET_HALF_WIDTH ? 0 : (disk.y > HUE_VIOLET_HALF_WIDTH ? 2 : 1);
    int step = level >= BAND_4 ? 4 : (level >= BAND_3 ? 3 : (level >= BAND_2 ? 2 : 1));
    return vec4(rampColor(ramp, step), 1.0);
}

void main() {
    vec2 q = vPlane;
    float rho = length(q);
    float texel = max(fwidth(q.x), 1.0e-4);
    vec2 direction = q / max(rho, 1.0e-4);

    // Follow the ray from the camera to its first two crossings of the disk
    // plane; see diskCrossings. Only rays that pass inside the outer edge of
    // the disk can meet it, which bounds their impact parameter.
    CameraRay camera = cameraRay(q);
    float reach = DISK_OUTER_RADIUS / (CRITICAL_IMPACT * sqrt(1.0 - 2.0 / DISK_OUTER_RADIUS));
    vec2 primaryPoint = vec2(1.0e4);
    vec2 secondaryPoint = vec2(1.0e4);
    if (camera.impact < reach) {
        vec3 primary;
        vec3 secondary;
        diskCrossings(camera, primary, secondary);
        primaryPoint = viewedPoint(primary);
        secondaryPoint = viewedPoint(secondary);
    }

    // Derivatives have to be taken outside of branches.
    float primaryAngleTexel = angleFootprint(primaryPoint);
    float secondaryAngleTexel = angleFootprint(secondaryPoint);
    float primaryRadiusTexel = fwidth(length(primaryPoint));
    float secondaryRadiusTexel = fwidth(length(secondaryPoint));
    vec2 skyPoint = direction * (rho - LENS_STRENGTH * deflectionAngle(rho));
    vec2 skyHalfSpan = 0.5 * fwidth(skyPoint);

    vec4 primary = diskColor(diskSample(primaryPoint, primaryAngleTexel, primaryRadiusTexel, 1.0));
    vec4 secondary = diskColor(diskSample(secondaryPoint, secondaryAngleTexel, secondaryRadiusTexel,
            UNDERSIDE_BRIGHTNESS));

    // Layers from front to back; the first solid one wins, since a sprite
    // texel holds exactly one colour. The near side of the disk passes in
    // front of the shadow, the shadow hides everything behind it, then come
    // the arc under the shadow, the photon ring hugging the shadow edge, and
    // the bent stars.
    vec4 color = vec4(0.0);
    if (primary.a > 0.0) {
        color = primary;
    } else if (camera.impact < 1.0) {
        color = vec4(0.0, 0.0, 0.0, 1.0);
    } else if (secondary.a > 0.0) {
        color = secondary;
    } else if (camera.impact < 1.0 + texel) {
        // The texel ring just outside the shadow edge.
        color = vec4(RING_COLOR, 1.0);
    } else if (uStarField > 0.0 && rho < STAR_FIELD_RADIUS) {
        // Stars add light without blocking the sky, so vanilla stars behind a
        // lensed one still show.
        float star = starAt(skyPoint, skyHalfSpan);
        color = vec4(star >= 1.0 ? STAR_COLOR : STAR_ARM_COLOR * step(0.25, star), 0.0);
    }

    if (color.a <= 0.0 && max(color.r, max(color.g, color.b)) <= 0.0) {
        discard;
    }
    fragColor = color;
}
