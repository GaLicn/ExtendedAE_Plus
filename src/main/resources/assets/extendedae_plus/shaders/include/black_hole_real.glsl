// The real black hole: the smooth one, as opposed to the pixel-art sprite. It
// is drawn in the sky by black_hole.fsh and over the head of a player wearing
// the Infinity Core by black_hole_lens.fsh, which share all of it through this
// file and differ only in what lies behind it.
//
// Its look follows Gargantua, the black hole of the film Interstellar (see
// James, von Tunzelmann, Franklin and Thorne, Class. Quantum Grav. 32 (2015)
// 065001, on how it was rendered):
//
// - Seen from a little above the disk, its near side is a bright band across
//   the lower half of the shadow, reaching far out on either side, while the
//   far side is bent by gravity into an arch over the top of the shadow,
//   and into a thinner one under it.
// - No Doppler beaming and no colour shift. The film left both out so the
//   disk would read clearly, so the two sides of the disk are equally bright
//   and the same colour.
// - White-hot gas at the inner edge, cooling outwards through gold to orange,
//   and streaked with fine filaments wound round the black hole.
// - A disk of some thickness whose outer gas is thin enough to see through.
//   Seen face on, as in the arches, the outer gas is faint; seen edge on, as
//   at the ends of the band, a ray runs through a long stretch of it, so the
//   band reaches far out to either side while the arches stay close to the
//   shadow.
// - A crisp black shadow outlined by a thin photon ring.
// - A soft glow over everything, which the film owes to the veiling flare of
//   its camera lenses and which stands in for bloom here.
//
// The including shader must declare uTime and import black_hole_glitch.glsl,
// for the hash, and black_hole_geodesic.glsl before this file.
//
// Keep every byte of this file ASCII. Some GL drivers reject GLSL source that
// contains anything else, comments included, which is also why all comments
// here are written in English.

// ============================================================================
// Tunable parameters
//
// "Shadow radius" is the radius of the black disk of the black hole. Disk
// radii are measured in units of the black hole mass M, and the shadow radius
// is about 5.2 M.
// ============================================================================

// ---- Shape ----
// Inner and outer disk radius (M). The inner edge sits at the innermost
// stable orbit, as Gargantua's does, which leaves a black gap between the
// photon ring and the halo over the shadow. The disk is wider than on the
// pixel-art sprite, so while a glitch swaps one for the other their edges do
// not quite line up, which only adds to the glitch.
const float DISK_INNER_RADIUS = 6.0;
const float DISK_OUTER_RADIUS = 24.0;
// Thickness of the disk (M). A ray that meets the disk at a shallow angle runs
// through DISK_THICKNESS / sine of that angle of gas. Larger makes the band
// seen edge on brighter and longer compared with the arches.
const float DISK_THICKNESS = 0.35;
// Length of gas (M) at the inner edge that a ray must run through for the gas
// to turn opaque. Larger lets more of the disk be seen through.
const float DISK_OPACITY_SCALE = 0.2;
// The gas thins out with radius as (inner radius / r) to this power, so the
// outer disk is brighter seen edge on than face on. Larger leaves less of
// the front face of the disk to be seen.
const float DENSITY_FALLOFF = 1.0;
// Shallowest angle, as its sine, at which a ray is taken to meet the disk.
// It bounds the path through the gas of the disk seen exactly edge on.
const float MIN_INCIDENCE = 0.03;
// The bright body of the disk ends between these two radii (M); beyond it
// only faint wisps, at WISP_LEVEL of its brightness, reach out to the outer
// radius. Seen edge on they are the long frayed wings either side of the
// shadow, while the halo over it, the image of the body, keeps a clear edge.
const float BODY_FADE_START = 10.0;
const float BODY_FADE_END = 16.0;
const float WISP_LEVEL = 0.3;
// Fraction of the outer radius at which the wisps start to fade out.
const float OUTER_EDGE_START = 0.5;
// Speed at which brightness rises from the inner edge. Larger sharpens it.
const float INNER_RISE_SPEED = 3.0;

// ---- Brightness and colour ----
// Overall exposure; all light is multiplied by it before tone mapping.
const float EXPOSURE = 1.0;
// Disk brightness, and the power by which it falls off outwards. Larger
// powers keep the light on the inner disk and the arches over and under the
// shadow close to it.
const float DISK_BRIGHTNESS = 4.5;
const float DISK_BRIGHTNESS_POWER = 2.2;
// Most the disk can block of what lies behind it, at the inner and the outer
// radius, once the gas is opaque.
const float DISK_OPACITY_INNER = 1.0;
const float DISK_OPACITY_OUTER = 0.95;
// Disk colour follows "heat", which falls off outwards as (inner radius / r)
// to the power HEAT_POWER, through four colours from the inner edge out. The
// tone mapping washes out whatever is bright, so the inner gas comes out near
// white and only the outer colours need to be strongly tinted to stay warm.
const float HEAT_POWER = 1.0;
const vec3 WHITE_HOT_COLOR = vec3(1.00, 0.95, 0.86);
const vec3 GOLD_COLOR = vec3(1.00, 0.74, 0.42);
const vec3 AMBER_COLOR = vec3(1.00, 0.50, 0.20);
const vec3 EMBER_COLOR = vec3(0.75, 0.26, 0.08);

// ---- Filaments ----
// The gas lies in rings, *_RADIAL_CELLS of them per unit of log radius, each
// varying smoothly over *_CELLS cells round its orbit, so the filaments are far
// longer than they are wide. Three layers of them, from coarse to fine, are
// stacked. Each ring turns at the orbital speed of its own radius, the inner
// edge once every INNER_TURN_SECONDS and the rest slower, as Kepler has it, so
// the rings slide past one another. Every ring turns a whole number of times
// in the 10000 seconds after which uTime wraps, so nothing jumps at the wrap.
const float COARSE_STREAK_CELLS = 4.0;
const float COARSE_STREAK_RADIAL_CELLS = 10.0;
const float STREAK_CELLS = 6.0;
const float STREAK_RADIAL_CELLS = 24.0;
const float FINE_STREAK_CELLS = 11.0;
const float FINE_STREAK_RADIAL_CELLS = 55.0;
const float INNER_TURN_SECONDS = 12.0;
// Contrast of each layer at the inner edge and at the outer edge. The inner
// gas is streaked finely enough that the filaments show on the front face
// of the disk; further out they stand apart, and at the edge the gaps
// between them go dark, so the wings fray into wisps.
const float COARSE_STREAK_CONTRAST_INNER = 0.2;
const float COARSE_STREAK_CONTRAST_OUTER = 0.3;
const float STREAK_CONTRAST_INNER = 0.45;
const float STREAK_CONTRAST_OUTER = 0.55;
const float FINE_STREAK_CONTRAST_INNER = 0.3;
const float FINE_STREAK_CONTRAST_OUTER = 0.4;
// Fraction of its gas the disk keeps between the filaments, at the inner edge
// and at the outer edge.
const float GAP_OPACITY_INNER = 0.6;
const float GAP_OPACITY_OUTER = 0.1;
// Fades the filaments to their mean once a cell approaches pixel size, which
// keeps the distant black hole from shimmering. These are the start and end
// of that fade, in cells per pixel.
const float NOISE_FADE_START = 0.4;
const float NOISE_FADE_END = 0.9;

// ---- Photon ring ----
// Brightness, position (shadow radii) and minimum width (shadow radii) of the
// photon ring. The higher-order sub-rings are not drawn separately: they hug
// the main ring within a few thousandths of a shadow radius, well under one
// pixel at any playable resolution.
const float RING_BRIGHTNESS = 1.6;
const float RING_RADIUS = 1.012;
const float RING_WIDTH = 0.012;
const vec3 RING_COLOR = vec3(1.00, 0.90, 0.75);

// ---- Glow (in place of a bloom pass) ----
const vec3 GLOW_COLOR = vec3(1.00, 0.72, 0.45);
// Bright rim round the shadow: brightness and falloff, plus a wider second
// layer with its own brightness and falloff. Kept faint, so the gap between
// the photon ring and the arches stays dark.
const float HALO_BRIGHTNESS = 0.05;
const float HALO_FALLOFF = 12.0;
const float HALO_WIDE_BRIGHTNESS = 0.03;
const float HALO_WIDE_FALLOFF = 1.2;
// Light bleeding just inside the shadow edge: brightness and falloff. Larger
// leaves the shadow edge less black.
const float SHADOW_SPILL_BRIGHTNESS = 0.03;
const float SHADOW_SPILL_FALLOFF = 24.0;
// Wide, faint haze: brightness and falloff.
const float HAZE_BRIGHTNESS = 0.02;
const float HAZE_FALLOFF = 0.6;
// A glow over the disk: brightness, half length across the picture and half
// height (shadow radii) seen edge on. The height grows as the disk opens up,
// by DISK_GLOW_OPENING times the height the disk itself spans. Seen edge on it
// is a long, thin streak of light along the band.
const float DISK_GLOW_BRIGHTNESS = 0.35;
const float DISK_GLOW_LENGTH = 4.0;
const float DISK_GLOW_HEIGHT = 0.08;
const float DISK_GLOW_OPENING = 0.8;
// How much haze and disk glow is kept in the part of the shadow not covered by
// the disk. Smaller leaves the shadow blacker.
const float GLOW_IN_SHADOW = 0.15;

// ============================================================================
// Fixed values, normally no need to change
// ============================================================================

// uTime wraps after this many seconds.
const float TIME_WRAP_SECONDS = 10000.0;

// Per-pixel change of (angle, log radius) for the polar coordinates of p.
vec2 polarFootprint(vec2 p) {
    vec2 px = dFdx(p);
    vec2 py = dFdy(p);
    float inverseLengthSquared = 1.0 / max(dot(p, p), 1.0e-4);
    float angle = abs(p.x * px.y - p.y * px.x) + abs(p.x * py.y - p.y * py.x);
    float logRadius = abs(dot(p, px)) + abs(dot(p, py));
    return vec2(angle, logRadius) * inverseLengthSquared;
}

// One ring of gas: value noise round the orbit, `cells` cells to a turn, carried
// round at the orbital speed of the ring. row numbers the ring, rows to a unit
// of log radius from the inner edge out. turns is the polar angle in turns.
float ringNoise(float turns, float row, float cells, float rows, uint seed) {
    float radius = DISK_INNER_RADIUS * exp(row / rows);
    float period = INNER_TURN_SECONDS * pow(radius / DISK_INNER_RADIUS, 1.5);
    float turnsPerWrap = max(floor(TIME_WRAP_SECONDS / period + 0.5), 1.0);
    // The gas turns towards decreasing polar angle, as on the pixel-art
    // sprite, so the noise is read further along.
    float x = (turns + fract(uTime * (turnsPerWrap / TIME_WRAP_SECONDS))) * cells;
    float cell = floor(x);
    float f = x - cell;
    float x0 = cell - cells * floor(cell / cells);
    float x1 = x0 + 1.0 > cells - 0.5 ? 0.0 : x0 + 1.0;
    return mix(hashCell(vec2(x0, row), seed), hashCell(vec2(x1, row), seed), f * f * (3.0 - 2.0 * f));
}

// Filaments: rings of gas blended smoothly across the radius, in [0, 1] round
// a mean of 0.5. Each ring turns as a whole, so the differential rotation of
// the disk never winds the pattern up. polar is the per-pixel change of
// (angle, log radius).
float filaments(float turns, float logRadius, float cells, float rows, vec2 polar, uint seed) {
    vec2 cellsPerPixel = vec2(polar.x * cells / TAU, polar.y * rows);
    float detail = 1.0 - smoothstep(NOISE_FADE_START, NOISE_FADE_END, max(cellsPerPixel.x, cellsPerPixel.y));
    if (detail <= 0.0) {
        return 0.5;
    }
    float y = logRadius * rows;
    float row = floor(y);
    float f = y - row;
    float value = mix(ringNoise(turns, row, cells, rows, seed), ringNoise(turns, row + 1.0, cells, rows, seed),
            f * f * (3.0 - 2.0 * f));
    return mix(0.5, value, detail);
}

vec3 diskPalette(float heat) {
    vec3 color = mix(EMBER_COLOR, AMBER_COLOR, smoothstep(0.2, 0.4, heat));
    color = mix(color, GOLD_COLOR, smoothstep(0.35, 0.65, heat));
    return mix(color, WHITE_HOT_COLOR, smoothstep(0.6, 1.0, heat));
}

// Light (rgb) and opacity (a) of one image of the disk. p is the point on the
// disk as viewedPoint gives it, polar its per-pixel change of (angle, log
// radius), radiusPixel the per-pixel change of its radius and incidence the
// sine of the angle at which the ray meets the disk.
vec4 diskImage(vec2 p, vec2 polar, float radiusPixel, float incidence) {
    float r = length(p);
    // Fraction of the pixel that lands on the disk. This also keeps the images
    // near the photon ring, which are thinner than a pixel, from aliasing.
    float span = max(radiusPixel, 1.0e-3);
    float coverage = clamp((min(r + 0.5 * span, DISK_OUTER_RADIUS)
            - max(r - 0.5 * span, DISK_INNER_RADIUS)) / span, 0.0, 1.0);
    if (coverage <= 0.0) {
        return vec4(0.0);
    }

    float closeness = DISK_INNER_RADIUS / r;
    // Laid out by the angle on the disk itself, so the filaments stay put
    // while the camera walks round the black hole.
    float turns = atan(p.y, p.x) / TAU + viewedFrameTurns();
    float logRadius = log(r / DISK_INNER_RADIUS);
    float outward = smoothstep(0.0, 1.0, logRadius / log(BODY_FADE_END / DISK_INNER_RADIUS));
    float coarse = filaments(turns, logRadius, COARSE_STREAK_CELLS, COARSE_STREAK_RADIAL_CELLS, polar, 59u);
    float medium = filaments(turns, logRadius, STREAK_CELLS, STREAK_RADIAL_CELLS, polar, 71u);
    float fine = filaments(turns, logRadius, FINE_STREAK_CELLS, FINE_STREAK_RADIAL_CELLS, polar, 83u);
    float density = max(1.0
            + 2.0 * mix(COARSE_STREAK_CONTRAST_INNER, COARSE_STREAK_CONTRAST_OUTER, outward) * (coarse - 0.5)
            + 2.0 * mix(STREAK_CONTRAST_INNER, STREAK_CONTRAST_OUTER, outward) * (medium - 0.5)
            + 2.0 * mix(FINE_STREAK_CONTRAST_INNER, FINE_STREAK_CONTRAST_OUTER, outward) * (fine - 0.5), 0.0);

    // A quick rise from the inner edge, the bright body, then the wisps that
    // fade out towards the outer edge. The rise is held at zero inside the
    // inner edge: the pixel coverage above lets points just inside it through,
    // and a negative rise there turns the opacity and the light negative, which
    // punches holes in the photon ring.
    float innerRise = 1.0 - exp(-max(r - DISK_INNER_RADIUS, 0.0) * INNER_RISE_SPEED);
    float wisps = mix(WISP_LEVEL, 1.0, 1.0 - smoothstep(BODY_FADE_START, BODY_FADE_END, r));
    float outerFade = 1.0 - smoothstep(DISK_OUTER_RADIUS * OUTER_EDGE_START, DISK_OUTER_RADIUS, r);
    float body = coverage * wisps * outerFade;

    // How much gas the ray runs through on its way across the disk, the wisps
    // being thinner than the body, in units that turn the gas opaque at 1.
    // Thin gas glows in proportion to it and lets the rest through; past 1
    // the gas is opaque and only its surface shows.
    float path = DISK_THICKNESS / max(incidence, MIN_INCIDENCE) * mix(1.0, 0.5, 1.0 - wisps);
    float gaps = clamp(mix(mix(GAP_OPACITY_INNER, GAP_OPACITY_OUTER, outward), 1.0, density), 0.0, 1.0);
    float opticalDepth = path * gaps * pow(closeness, DENSITY_FALLOFF) / DISK_OPACITY_SCALE;
    float filled = 1.0 - exp(-opticalDepth);

    float brightness = DISK_BRIGHTNESS * pow(closeness, DISK_BRIGHTNESS_POWER) * density * innerRise;
    vec3 light = diskPalette(pow(closeness, HEAT_POWER)) * brightness * body * filled;
    float opacity = body * mix(DISK_OPACITY_OUTER, DISK_OPACITY_INNER, sqrt(closeness)) * filled
            * (0.3 + 0.7 * innerRise);
    return vec4(light, opacity);
}

// The real black hole at the point q of the image plane (shadow radii), seen
// by the camera of black_hole_geodesic.glsl: rgb is the light it gives off,
// before tone mapping, and a how much of what lies behind it, the sky or the
// scene, it blocks. The caller puts the background behind it, weighted by
// 1 - a. Rays are only followed where `traced` is true, so callers can skip
// the fragments that will not show.
vec4 realBlackHole(vec2 q, bool traced) {
    CameraRay camera = cameraRay(q);
    float impact = camera.impact;
    float pixel = max(fwidth(impact), 1.0e-4);

    // Follow the ray to its first two crossings of the disk plane; see
    // diskCrossings. Only rays that pass inside the outer edge of the disk can
    // meet it, which bounds their impact parameter.
    float reach = DISK_OUTER_RADIUS / (CRITICAL_IMPACT * sqrt(1.0 - 2.0 / DISK_OUTER_RADIUS));
    vec2 primaryPoint = vec2(1.0e4);
    vec2 secondaryPoint = vec2(1.0e4);
    float primaryIncidence = 1.0;
    float secondaryIncidence = 1.0;
    if (traced && impact < reach) {
        vec3 primary;
        vec3 secondary;
        diskCrossings(camera, primary, secondary, primaryIncidence, secondaryIncidence);
        primaryPoint = viewedPoint(primary);
        secondaryPoint = viewedPoint(secondary);
    }

    // Derivatives have to be taken outside of branches.
    vec2 primaryPolar = polarFootprint(primaryPoint);
    vec2 secondaryPolar = polarFootprint(secondaryPoint);
    float primaryRadiusPixel = fwidth(length(primaryPoint));
    float secondaryRadiusPixel = fwidth(length(secondaryPoint));

    vec4 primary = diskImage(primaryPoint, primaryPolar, primaryRadiusPixel, primaryIncidence);
    vec4 secondary = diskImage(secondaryPoint, secondaryPolar, secondaryRadiusPixel, secondaryIncidence);
    float shadow = 1.0 - smoothstep(1.0 - pixel, 1.0 + pixel, impact);

    // Photon ring: light that circled the black hole piles up just outside the
    // shadow. Its width never drops below about a pixel; the energy is kept
    // constant instead, so it fades rather than aliasing when it is small.
    float ringWidth = max(RING_WIDTH, pixel * 0.8);
    float ringOffset = (impact - RING_RADIUS) / ringWidth;
    vec3 ringLight = RING_COLOR * RING_BRIGHTNESS * exp(-ringOffset * ringOffset) * (RING_WIDTH / ringWidth);

    // The images are composited front to back: the first one blocks what is
    // behind it where it is opaque, and the second shows through only in what
    // is left. The ring is farthest back of all, so it is attenuated by both.
    vec3 emission = primary.rgb + (1.0 - primary.a) * (secondary.rgb + (1.0 - secondary.a) * ringLight);
    float occlusion = 1.0 - (1.0 - primary.a) * (1.0 - secondary.a) * (1.0 - shadow);

    // Analytic glow in place of a bloom pass: a bright rim round the shadow, a
    // wide haze, and a glow over the disk that opens up with it. Little of it
    // reaches the part of the shadow left uncovered by the disk, so that it
    // stays black.
    float edge = abs(impact - 1.0);
    float halo = HALO_BRIGHTNESS * exp(-edge * HALO_FALLOFF) + HALO_WIDE_BRIGHTNESS * exp(-edge * HALO_WIDE_FALLOFF);
    float spill = SHADOW_SPILL_BRIGHTNESS * exp(-max(1.0 - impact, 0.0) * SHADOW_SPILL_FALLOFF);
    float haze = HAZE_BRIGHTNESS * exp(-max(impact - 1.0, 0.0) * HAZE_FALLOFF);
    float opening = abs(uViewDirection.y) * DISK_OUTER_RADIUS / CRITICAL_IMPACT;
    vec2 wing = q / vec2(DISK_GLOW_LENGTH, DISK_GLOW_HEIGHT + DISK_GLOW_OPENING * opening);
    float diskGlow = DISK_GLOW_BRIGHTNESS * exp(-dot(wing, wing));
    float bareShadow = shadow * (1.0 - primary.a);
    emission += GLOW_COLOR * (mix(halo, spill, bareShadow) + (haze + diskGlow) * mix(1.0, GLOW_IN_SHADOW, bareShadow));
    return vec4(emission, occlusion);
}

// Tone maps the light of the black hole, since the vanilla framebuffer cannot
// hold HDR values, and dithers it to break up 8-bit banding in the soft glow.
vec3 toneMap(vec3 emission) {
    vec3 color = 1.0 - exp(-emission * EXPOSURE);
    uvec2 fragCell = uvec2(gl_FragCoord.xy);
    float dither = float(hashUint(fragCell.x ^ hashUint(fragCell.y)) >> 8u) * (1.0 / 16777216.0) - 0.5;
    return max(color + dither / 255.0, 0.0);
}
