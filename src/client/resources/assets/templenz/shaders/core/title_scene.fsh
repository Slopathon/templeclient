#version 330

uniform sampler2D Sampler0;

layout(std140) uniform SceneConfig {
    mat4 ViewProj;
    mat4 InvViewProj;
    vec4 CameraPos;
    vec4 SunDir;
    vec4 KeyDir;
    vec4 SkyTop;
    vec4 SkyHorizon;
    vec4 SunColor;
    vec4 FogColor;
    vec4 Accent;
    vec4 Params;
    vec4 Pointer;
    vec4 Click;
    vec4 Shooting[4];
    mat4 Letters[12];
};

in vec3 worldPos;
in vec3 worldNormal;
in vec2 texCoord;
in vec3 tint;
flat in int kind;

out vec4 fragColor;

vec3 sky(vec3 dir) {
    float h = dir.y;
    vec3 col = mix(SkyHorizon.rgb, SkyTop.rgb, smoothstep(-0.02, 0.6, h));
    float s = max(dot(dir, normalize(SunDir.xyz)), 0.0);
    col += SunColor.rgb * (pow(s, 5.0) * 0.45 + pow(s, 48.0) * 0.5);
    if (h < 0.0) {
        col = mix(col, SkyHorizon.rgb * vec3(0.92, 0.86, 0.9), smoothstep(0.0, -0.25, h));
    }
    return col;
}

vec3 filmic(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec3 N = normalize(worldNormal);
    vec3 toCam = CameraPos.xyz - worldPos;
    float dist = length(toCam);
    vec3 V = toCam / max(dist, 1e-4);
    vec3 L = normalize(KeyDir.xyz);
    vec3 S = normalize(SunDir.xyz);
    vec3 skyAmbient = mix(SkyHorizon.rgb, SkyTop.rgb, 0.45) * 0.55 + vec3(0.08, 0.09, 0.12);
    vec3 groundBounce = vec3(0.30, 0.24, 0.16);
    vec3 ambient = mix(groundBounce, skyAmbient, 0.5 + 0.5 * N.y);
    vec3 colour;
    float alpha = 1.0;

    if (kind < 16) {
        vec3 albedo = Accent.rgb;
        float nv = max(dot(N, V), 0.0);
        vec3 R = reflect(-V, N);
        vec3 env = sky(R);
        float fresnel = 0.04 + 0.96 * pow(1.0 - nv, 5.0);
        vec3 metal = env * mix(albedo, vec3(1.0), fresnel) * 1.05;
        float diff = max(dot(N, L), 0.0);
        vec3 H = normalize(L + V);
        float spec = pow(max(dot(N, H), 0.0), 90.0) * Accent.w;
        float rim = pow(1.0 - nv, 3.0) * max(dot(N, S) * 0.5 + 0.5, 0.0);
        colour = metal * 0.62 + albedo * (diff * SunColor.rgb * 0.55 + ambient * 0.35);
        colour += SunColor.rgb * spec * 1.6;
        colour += mix(albedo, vec3(1.0), 0.45) * rim * 0.9;
        float up = clamp((worldPos.y - 4.0) / 3.2, 0.0, 1.0);
        if (texCoord.x < 0.5) {
            colour *= 0.92 + 0.28 * up;
        }
        float sweep = fract(Params.x / 7.0) * 60.0 - 30.0;
        float band = worldPos.x - (worldPos.y - 4.0) * 0.6 - sweep;
        colour += mix(albedo, vec3(1.0), 0.6) * exp(-band * band * 1.6) * 0.55;
    } else if (kind == 251) {
        float r = length(texCoord);
        float a = (1.0 - smoothstep(0.25, 1.0, r)) * tint.r;
        fragColor = vec4(0.05, 0.035, 0.06, a * 0.6);
        return;
    } else if (kind == 250) {
        float diff = max(dot(N, L), 0.0);
        colour = tint * (ambient * 0.8 + diff * SunColor.rgb * 0.7);
    } else if (kind == 253) {
        float t = Params.x;
        vec2 p = worldPos.xz;
        vec3 wn = normalize(vec3(
                sin(p.x * 1.7 + t * 1.3) * 0.05 + sin(p.y * 2.3 - t * 0.9) * 0.03,
                1.0,
                cos(p.y * 1.9 + t * 1.1) * 0.05 + cos(p.x * 2.9 + t * 0.7) * 0.03));
        float nv = max(dot(wn, V), 0.0);
        float fresnel = 0.02 + 0.98 * pow(1.0 - nv, 5.0);
        vec3 reflected = sky(reflect(-V, wn));
        vec3 deep = vec3(0.05, 0.16, 0.24) + ambient * 0.12;
        colour = mix(deep, reflected, clamp(fresnel * 1.1 + 0.25, 0.0, 1.0));
        float glint = pow(max(dot(reflect(-S, wn), V), 0.0), 220.0);
        colour += SunColor.rgb * glint * 3.0;
        alpha = 0.92;
    } else {
        vec4 texel = texture(Sampler0, texCoord);
        if ((kind == 254 || kind == 252) && texel.a < 0.5) {
            discard;
        }
        vec3 albedo = texel.rgb * tint;
        float diff = max(dot(N, L), 0.0);
        if (kind == 252 || kind == 254 && abs(N.y) > 0.9) {
            diff = max(diff, 0.55);
        }
        colour = albedo * (ambient + diff * SunColor.rgb * 1.05);
    }

    float fog = 1.0 - exp(-pow(dist * FogColor.w, 1.35));
    vec3 haze = mix(sky(-V), FogColor.rgb, 0.35);
    colour = mix(colour, haze, clamp(fog, 0.0, 1.0));

    colour = filmic(colour * 1.05);
    float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    fragColor = vec4(colour + dither / 255.0, alpha);
}
