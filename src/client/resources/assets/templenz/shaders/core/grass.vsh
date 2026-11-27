#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <templenz:sample_lightmap.glsl>

layout(std140) uniform GrassConfig {
    vec4 Wind;
    vec4 Sun;
    vec4 Sky;
    vec4 Wetness;
    vec4 Fade;
    vec4 Thin;
    vec4 ThinB;
    vec4 Share;
    vec4 Pusher[12];
    vec4 PusherS[12];
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec3 bladeColor;
out vec3 lightColor;
out vec3 bladeNormal;
out vec3 relPos;
out float along;
out float skyLight;
out float pressed;
out float acrossBlade;
out float coverage;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float puddleNoise(vec2 p) {
    return valueNoise(p) * 0.55 + valueNoise(p * 2.13 + 7.1) * 0.3 + valueNoise(p * 4.7 + 3.3) * 0.15;
}

void main() {
    float code = floor(UV0.x / 6.0);
    float rest = UV0.x - code * 6.0;
    float edge = floor(rest / 2.0);
    float t = (rest - edge * 2.0) / 0.999;
    float side = edge < 0.5 ? -1.0 : edge < 1.5 ? 1.0 : 0.0;
    int kind = code >= 32.0 ? 1 : 0;
    acrossBlade = side;
    coverage = 1.0;
    float face = mod(code, 32.0) * (6.2831853 / 32.0);
    vec3 Normal = vec3(cos(face) * 0.94, 0.34, sin(face) * 0.94);
    float packedLight = floor(UV0.y / 4.0);
    float h = UV0.y - packedLight * 4.0;
    float sky = floor(packedLight / 16.0);
    ivec2 light = ivec2(int(packedLight - sky * 16.0) * 16, int(sky) * 16);
    float rank = Color.a;

    vec3 root = vec3(Position.x, Position.y - t * h, Position.z);
    vec3 shape = Position - root;
    vec3 rel = root + ModelOffset;
    vec2 world = root.xz + TextureMat[3].xz;
    float dist = length(rel);

    float grow = kind == 1 ? 1.0 - smoothstep(Fade.z, Fade.w, dist) : 1.0 - smoothstep(Fade.x, Fade.y, dist);
    if (kind == 0) {
        float keep = mix(mix(mix(1.0, ThinB.z, smoothstep(Thin.x, Thin.y, dist)), ThinB.w, smoothstep(Thin.z, Thin.w, dist)),
                Share.x, smoothstep(ThinB.x, ThinB.y, dist));
        grow *= 1.0 - smoothstep(keep * 0.9, keep, rank);
        vec3 across = normalize(cross(Normal, vec3(0.0, 1.0, 0.0)));
        float widen = pow(1.0 / keep, 0.82);
        shape += across * side * Share.y * (1.0 - 0.72 * t) * (widen - 1.0);
    }
    float pool = 0.0;
    if (Wetness.y > 0.0 && kind != 1) {
        vec2 ground = root.xz + TextureMat[2].xz;
        pool = Wetness.z > 0.5 ? 1.0 : smoothstep(1.02 - Wetness.y, 1.1 - Wetness.y, puddleNoise(ground * 0.17) + 0.06);
        grow *= mix(1.0, Wetness.z > 0.5 ? 0.38 : 0.82, pool);
    }
    shape *= grow;
    float hh = h * grow;
    if (h < 0.001) {
        coverage = (1.0 - smoothstep(Fade.x, Fade.y, dist)) * (Wetness.z > 0.5 ? 0.0 : 1.0);
    }

    vec2 windDir = normalize(vec2(0.85, 0.53));
    float time = Wind.x;
    float along0 = dot(world, windDir);
    float front = sin(along0 * 0.42 - time * 1.9 + sin(world.x * 0.11 - world.y * 0.07) * 1.6);
    float swell = sin(along0 * 0.11 - time * 0.65 + 1.7);
    float lull = 0.75 + 0.25 * sin(time * 0.21 + world.y * 0.013);
    float lean = (0.2 + 0.17 * front + 0.13 * swell) * lull;
    vec2 flutter = vec2(sin(time * 5.3 + world.x * 3.1 + rank * 19.0), cos(time * 4.4 + world.y * 2.7 + rank * 11.0)) * 0.035;
    vec2 bend = (windDir * lean + flutter) * Wind.y * (0.75 + 0.45 * clamp(h, 0.0, 1.5));

    vec2 push = vec2(0.0);
    float press = 0.0;
    if (Wind.z > 0.0) {
        for (int i = 0; i < 12; i++) {
            float s = PusherS[i].x;
            if (s <= 0.0) {
                continue;
            }
            vec3 d = rel - Pusher[i].xyz;
            float r = Pusher[i].w;
            float dl = length(d.xz);
            float k = (1.0 - smoothstep(r * 0.3, r, dl)) * s * (1.0 - smoothstep(0.5, 1.3, abs(d.y)));
            if (k <= 0.0) {
                continue;
            }
            vec2 aside = dl > 1e-3 ? d.xz / dl : vec2(0.0);
            vec2 going = PusherS[i].yz;
            push += (dot(going, going) > 0.5 ? going * 0.75 + aside * 0.35 : aside * 0.6) * k;
            press = max(press, k);
        }
        press = min(press * Wind.z, 1.0);
        bend = bend * (1.0 - 0.6 * press) + push * 0.7 * Wind.z * min(1.0, 0.6 / max(h, 0.1));
    }
    float amount = length(bend);
    if (amount > 0.85) {
        bend *= 0.85 / amount;
        amount = 0.85;
    }
    float tt = t * t;
    shape.xz += bend * hh * tt;
    shape.y *= 1.0 / sqrt(1.0 + amount * amount * tt * 1.6);

    vec3 pos = root + shape + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    if (side != 0.0) {
        vec3 acrossDir = normalize(cross(Normal, vec3(0.0, 1.0, 0.0)));
        float halfNow = max(abs(dot(pos - (root + ModelOffset), acrossDir)), 1e-5);
        float pixels = 2.0 * halfNow * ProjMat[1][1] * 0.5 * Share.z / max(gl_Position.w, 1e-3);
        if (pixels < 1.5) {
            float widen = min(1.5 / max(pixels, 1e-3), 12.0);
            pos += acrossDir * side * halfNow * (widen - 1.0);
            gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
            coverage = max(1.0 / widen, 0.12) * 1.15;
        }
    }

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    bladeColor = Color.rgb;
    lightColor = sample_lightmap(Sampler2, light).rgb;
    bladeNormal = normalize(Normal + vec3(bend.x, 0.0, bend.y) * t * 0.8);
    relPos = pos;
    along = t;
    skyLight = sky / 15.0;
    pressed = press;
}
