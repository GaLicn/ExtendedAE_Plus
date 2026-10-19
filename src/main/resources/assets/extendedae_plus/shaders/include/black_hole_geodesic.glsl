// Light rays around the black hole, shared by every shader that traces them:
// the real black hole in the sky (black_hole.fsh) and over the head of a
// player wearing the Infinity Core (black_hole_lens.fsh), and the pixel-art
// sprite of both (black_hole_pixelated.fsh).
//
// The black hole has a frame of its own, measured in units of its mass M,
// with the disk in the xz plane. The uniforms below place the camera in that
// frame, and every ray is followed from the camera itself. The sky black hole
// is seen from infinitely far away at a fixed angle. The one over the head is
// an object in the world with its disk level with the ground: walking round it
// shows the disk from every side while the gas on it stays put, from below
// the picture turns over, and up close the near side of the disk looms larger
// than the far side, as perspective has it.
//
// A point of the picture is given as q, in shadow radii, on the image plane:
// the plane through the centre of the black hole spanned by the camera's right
// and up axes. Seen from far away, the shadow edge is the unit circle there.
//
// Keep every byte of this file ASCII. Some GL drivers reject GLSL source that
// contains anything else, comments included, which is also why all comments
// here are written in English.

// Direction from the black hole to the camera, and the distance between the
// two in M; a distance of 0 puts the camera infinitely far away.
uniform vec3 uViewDirection;
uniform float uViewDistance;
// The camera's right and up axes.
uniform vec3 uViewRight;
uniform vec3 uViewUp;

// Integration steps per half orbit of a light ray. More is more accurate and
// slower.
const int ORBIT_STEPS = 12;
// How far the background seen past the black hole is dragged round it, as a
// multiple of the deflection angle. It is the one visual control over how
// strongly space appears to be warped; zero leaves the background flat.
const float LENS_STRENGTH = 2.5;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;
// Distances on the disk are in units of the black hole mass (G = c = 1). The
// shadow edge is at the critical impact parameter, 3 * sqrt(3).
const float CRITICAL_IMPACT = 5.19615242;

// A light ray in its orbital plane: x is M / r and y its derivative with
// respect to the angle swept around the black hole. It obeys x'' = 3x^2 - x.
vec2 orbitSlope(vec2 ray) {
    return vec2(ray.y, ray.x * (3.0 * ray.x - 1.0));
}

// Moves a ray `angle` radians further around the black hole. Rays that fell
// through the horizon (x > 0.5) or escaped (x < 0) are left where they are.
vec2 advanceOrbit(vec2 ray, float angle) {
    float h = angle / float(ORBIT_STEPS);
    for (int i = 0; i < ORBIT_STEPS; i++) {
        if (ray.x > 0.5 || ray.x < 0.0) {
            break;
        }
        vec2 k1 = orbitSlope(ray);
        vec2 k2 = orbitSlope(ray + 0.5 * h * k1);
        vec2 k3 = orbitSlope(ray + 0.5 * h * k2);
        vec2 k4 = orbitSlope(ray + h * k3);
        ray += h / 6.0 * (k1 + 2.0 * (k2 + k3) + k4);
    }
    return ray;
}

// A camera ray, set up in the plane it orbits in. Having swept an angle phi
// round the black hole, it lies in direction axis * cos(phi) + across *
// sin(phi) from it, at the radius `ray` gives; it starts at phi = 0, where the
// camera is. impact is its impact parameter in shadow radii, below 1 for rays
// that fall into the black hole.
struct CameraRay {
    vec3 axis;
    vec3 across;
    vec2 ray;
    float impact;
};

CameraRay cameraRay(vec2 q) {
    vec3 offset = CRITICAL_IMPACT * (q.x * uViewRight + q.y * uViewUp);
    CameraRay camera;
    camera.axis = uViewDirection;
    if (uViewDistance <= 0.0) {
        // From far away every ray arrives parallel to the line of sight, and
        // its offset from the centre is its impact parameter.
        float b = length(offset);
        camera.across = offset / max(b, 1.0e-4);
        camera.ray = vec2(0.0, 1.0 / max(b, 0.01 * CRITICAL_IMPACT));
        camera.impact = b / CRITICAL_IMPACT;
        return camera;
    }
    // From close by, the ray leaves the camera towards its point on the image
    // plane. A ray leaving a static observer at radius r at an angle alpha to
    // the radial direction has impact parameter r sin(alpha) / sqrt(1 - 2M / r).
    vec3 heading = normalize(offset - uViewDirection * uViewDistance);
    vec3 sideways = heading - dot(heading, uViewDirection) * uViewDirection;
    float sinAlpha = length(sideways);
    float x = 1.0 / uViewDistance;
    float b = max(uViewDistance * sinAlpha / sqrt(1.0 - 2.0 * x), 0.01 * CRITICAL_IMPACT);
    float slope = sqrt(max(1.0 / (b * b) - x * x * (1.0 - 2.0 * x), 0.0));
    camera.across = sideways / max(sinAlpha, 1.0e-6);
    // Heading inwards, the ray closes in on the black hole as it turns.
    camera.ray = vec2(x, dot(heading, uViewDirection) < 0.0 ? slope : -slope);
    camera.impact = b / CRITICAL_IMPACT;
    return camera;
}

// The point where a ray that has swept `angle` meets the disk plane. A ray
// that fell in first is put on the horizon and one that escaped far away, so
// the radius stays close to continuous across those edges and both land off
// the disk.
vec3 crossingPoint(CameraRay camera, vec2 ray, float angle) {
    float radius = ray.x >= 0.5 ? 2.0 : (ray.x <= 0.0 ? 1.0e4 : 1.0 / ray.x);
    return radius * (cos(angle) * camera.axis + sin(angle) * camera.across);
}

// Sine of the angle at which a ray that has swept `angle` meets the disk
// plane: 1 straight through it, near 0 grazing it. With x = M / r, the ray
// heads along -x' e_r + x e_phi, where e_r points away from the black hole and
// so lies in the disk plane at a crossing; only e_phi leaves the plane.
float crossingIncidence(CameraRay camera, vec2 ray, float angle) {
    vec3 onwards = cos(angle) * camera.across - sin(angle) * camera.axis;
    return clamp(abs(ray.x * onwards.y) / max(length(ray), 1.0e-6), 0.0, 1.0);
}

// Follows a camera ray to its first two crossings of the disk plane. The first
// shows the face of the disk turned towards the camera: the near side in
// front of the shadow and the far side bent over it. The second shows the
// other face of the far side, bent round under the shadow. Images of order
// three and above are not followed: they only exist for impact parameters
// within about a thousandth of the critical value, far less than one pixel.
// The incidences say how steeply the ray meets the plane at each crossing.
void diskCrossings(CameraRay camera, out vec3 primary, out vec3 secondary,
        out float primaryIncidence, out float secondaryIncidence) {
    // The ray is in the plane where axis.y cos(phi) + across.y sin(phi) = 0,
    // first for a phi between 0 and pi. axis.y is the sine of the angle from
    // the disk plane up to the camera, which the renderers never let reach 0.
    float axisHeight = camera.axis.y >= 0.0 ? max(camera.axis.y, 1.0e-4) : min(camera.axis.y, -1.0e-4);
    float firstCrossing = 0.5 * PI + atan(camera.across.y / axisHeight);
    vec2 ray = advanceOrbit(camera.ray, firstCrossing);
    primary = crossingPoint(camera, ray, firstCrossing);
    primaryIncidence = crossingIncidence(camera, ray, firstCrossing);
    ray = advanceOrbit(ray, PI);
    secondary = crossingPoint(camera, ray, firstCrossing + PI);
    secondaryIncidence = crossingIncidence(camera, ray, firstCrossing + PI);
}

// The same, for callers that only need the points.
void diskCrossings(CameraRay camera, out vec3 primary, out vec3 secondary) {
    float primaryIncidence;
    float secondaryIncidence;
    diskCrossings(camera, primary, secondary, primaryIncidence, secondaryIncidence);
}

// Horizontal direction from the black hole towards the camera, in the disk
// plane as (x, z). Straight above or below the black hole, where there is no
// such direction, the camera's right axis still says which way is across.
vec2 towardsCamera() {
    vec2 level = uViewDirection.xz;
    float levelLength = length(level);
    if (levelLength > 1.0e-3) {
        return level / levelLength;
    }
    vec2 right = normalize(uViewRight.xz);
    return vec2(-right.y, right.x);
}

// A point of the disk plane as the camera sees it: x across the line of sight,
// positive to the right, and y towards the camera. The shading works in this
// frame, so the side moving towards the camera and the far side bent over the
// shadow are always where the camera expects them.
vec2 viewedPoint(vec3 point) {
    vec2 towards = towardsCamera();
    return vec2(point.x * towards.y - point.z * towards.x, dot(point.xz, towards));
}

// Polar angle, in turns, of the x axis of viewedPoint in the black hole's own
// frame. Adding it to the polar angle of a viewed point gives the angle on
// the disk itself, so gas drawn by that angle stays where it is as the camera
// walks round.
float viewedFrameTurns() {
    vec2 towards = towardsCamera();
    return atan(-towards.x, towards.y) / TAU;
}

// How much of the orbital motion of the gas runs along the line of sight: 1
// seen from the side, falling to 0 seen from straight above or below, where
// the gas moves across the line of sight everywhere. It fades out whatever
// tells the approaching side from the receding one, and the near side from the
// far one, which no longer differ when the disk is seen face on.
float sideView() {
    return smoothstep(0.0, 0.5, length(uViewDirection.xz));
}

// Deflection angle of a ray that passes the black hole at impact parameter
// rho, measured in shadow radii, fitted to the numerical Schwarzschild
// solution. It diverges logarithmically towards the shadow edge, which is what
// folds the background into closed rings just outside the photon ring.
float deflectionAngle(float rho) {
    float x = max(rho, 1.0 + 1.0e-4);
    return log(1.0 + 0.638 / (x - 1.0)) + 0.133 / x;
}
