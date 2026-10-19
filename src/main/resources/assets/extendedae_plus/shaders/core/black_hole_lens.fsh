#version 150

// Screen-space gravitational lens: the black hole over the head of a player
// wearing the Infinity Core, composited over the already-rendered scene and
// bending the real pixels around it.
//
// This is the post-processing counterpart of black_hole.fsh. The black hole
// itself is the same real black hole from black_hole_real.glsl; only the
// background differs. black_hole.fsh samples a procedural star field, so its
// lensing can only bend that field. Here the background comes from uSceneTex,
// a copy of the finished scene, so the same deflection bends the terrain,
// entities and sky that the player actually sees.
//
// The black hole is an object in the world, its disk level with the ground:
// the rays are traced from the camera's real position, set by the uniforms of
// black_hole_geodesic.glsl. The pass is drawn at the depth of the centre of
// the black hole with the depth test on, so whatever stands in front of it
// keeps the untouched scene.
//
// When the pixel-art black hole is selected, this pass draws the black hole
// only in the glitch bands that show the real one, exactly as black_hole.fsh
// does in the sky; everywhere else it only bends the scene, and
// BlackHoleLensRenderer stamps the pixel-art sprite over it. The bands, the
// tears and the schedule are shared through black_hole_glitch.glsl.
//
// Keep every byte of this file ASCII. Some GL drivers reject GLSL source that
// contains anything else, comments included.

in vec2 vScreenUv;

// The rendered scene. Sampled at the deflected coordinate.
uniform sampler2D uSceneTex;
// Depth of the rendered scene, the inverse of the projection it was drawn
// with, and the distance of the centre of the black hole along the view axis
// in blocks. Together they tell what lies behind the black hole from what
// does not.
uniform sampler2D uSceneDepth;
uniform mat4 uInverseProjection;
uniform float uHoleDistance;
// Centre of the black hole in screen UV, origin at the bottom left.
uniform vec2 uCenter;
// Radius of the shadow in screen UV measured along y.
uniform float uRadius;
// Screen aspect ratio (width / height), so the lens stays circular.
uniform float uAspect;
// Extent of the pixel-art sprite on the image plane, in shadow radii, so the
// glitch bands here line up with the sprite stamped over this pass.
uniform vec2 uPlaneHalfExtent;
// 1 shows the black hole in full; lower values fade the lens and the black
// hole out together, as the camera comes too close to it.
uniform float uVisibility;

uniform float uTime;

out vec4 fragColor;

// ============================================================================
// Tunable parameters
//
// The black hole itself is tuned in black_hole_real.glsl.
// ============================================================================

// ---- Lensing ----
// Radius range, in shadow radii, over which the deflection fades back to no
// displacement. The deflection dies out only slowly on its own, so this sets
// how far round the black hole the scene is seen to bend; past
// LENS_FADE_END it is left as it is. How strongly the scene is dragged is
// LENS_STRENGTH in black_hole_geodesic.glsl.
const float LENS_FADE_START = 2.0;
const float LENS_FADE_END = 4.5;

// ============================================================================
// Fixed values, normally no need to change
// ============================================================================

#define PLANE_HALF_EXTENT uPlaneHalfExtent
// How far behind the centre of the black hole, in blocks, the scene must lie
// to be seen bent through it. It keeps the player wearing it, who stands right
// under it at about the same distance from the camera, out of the lens.
const float BEHIND_MARGIN = 1.5;
// Over this many blocks further back the bend eases in, so the ground just
// behind the wearer does not tear along the line where it starts.
const float BEHIND_RAMP = 2.0;
// Steps by which a bent sample that does not land behind the black hole is
// pulled back towards the pixel itself.
const int BEHIND_STEPS = 4;

// Glitch bands, and the hash used throughout.
#moj_import <extendedae_plus:black_hole_glitch.glsl>
// The camera, the ray integration and the lens.
#moj_import <extendedae_plus:black_hole_geodesic.glsl>
// The black hole itself.
#moj_import <extendedae_plus:black_hole_real.glsl>

// Distance along the view axis, in blocks, of the scene at a point of the
// screen.
float sceneDistance(vec2 uv) {
    float depth = texture(uSceneDepth, uv).r;
    vec4 view = uInverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return -view.z / view.w;
}

void main() {
    // Screen offset from the hole, made isotropic so the lens stays circular,
    // and expressed in shadow radii on the image plane.
    vec2 offsetUv = vScreenUv - uCenter;
    vec2 plane = vec2(offsetUv.x * uAspect, offsetUv.y) / max(uRadius, 1.0e-5);

    // Bands left to the pixel-art black hole get no black hole here, and
    // during a burst some bands are torn sideways, the bent scene with them.
    // Tears stay inside the extent of the sprite, as in the sky, where nothing
    // is drawn past it; otherwise the bent terrain below would tear as well.
    float band = glitchBand(plane);
    float shown = showsSmooth(band) ? uVisibility : 0.0;
    vec2 inside = step(abs(plane), PLANE_HALF_EXTENT);
    vec2 q = plane + vec2(glitchTear(band) * uGlitchTexel * inside.x * inside.y, 0.0);
    float rho = length(q);

    vec4 hole = realBlackHole(q, shown > 0.0) * shown;

    // The background is the real scene, resampled at the coordinate the ray
    // would have come from. A ray of impact parameter `impact` arrives from
    // sourceRadius; where that turns negative the ray has wrapped past half a
    // turn and samples the opposite side of the screen, which closes the field
    // into rings around the shadow. That deflection is what visibly warps the
    // world. Seen from close by, the impact parameter runs a little ahead of
    // the distance on the image plane; going by it keeps the rings hugging the
    // shadow edge.
    float impact = cameraRay(q).impact;
    float sourceRadius = impact - LENS_STRENGTH * deflectionAngle(impact);
    vec2 skyPoint = q / max(impact, 1.0e-4) * sourceRadius;
    vec2 warpedUv = uCenter + vec2(skyPoint.x / uAspect, skyPoint.y) * uRadius;
    // What this pixel shows is bent only as far as it lies behind the black
    // hole. The wearer, right under it, stays as drawn: bending them would
    // replace part of their head with the scene behind.
    float behind = smoothstep(BEHIND_MARGIN, BEHIND_MARGIN + BEHIND_RAMP,
            sceneDistance(vScreenUv) - uHoleDistance);
    float influence = (1.0 - smoothstep(LENS_FADE_START, LENS_FADE_END, rho)) * uVisibility * behind;
    vec2 sampleUv = clamp(mix(vScreenUv, warpedUv, influence), vec2(0.0), vec2(1.0));
    // Only what lies well behind the black hole is seen through it. A bent
    // ray that lands on something beside it, such as the head of the player
    // wearing it, would paint a second copy of that thing round the shadow;
    // such a sample is pulled back towards this pixel, and after the last
    // step it is this pixel itself, the scene left as it is.
    vec2 pullBack = (sampleUv - vScreenUv) / float(BEHIND_STEPS);
    for (int i = 0; i < BEHIND_STEPS; i++) {
        if (sceneDistance(sampleUv) >= uHoleDistance + BEHIND_MARGIN) {
            break;
        }
        sampleUv -= pullBack;
    }
    vec3 scene = texture(uSceneTex, sampleUv).rgb;

    // Emitted light over what the hole does not occlude. Far from the hole
    // both terms vanish and the scene passes through untouched.
    fragColor = vec4(toneMap(hole.rgb) + scene * (1.0 - hole.a), 1.0);
}
