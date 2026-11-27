#version 330

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

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += noise(p) * a;
        p = p * 2.02 + vec2(11.3, -7.1);
        a *= 0.5;
    }
    return s;
}

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

vec2 segment(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
    return vec2(length(p - (a + ab * t)), t);
}

void main() {
    vec4 far = InvViewProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w - CameraPos.xyz);
    float time = Params.x;
    float aspect = Params.z / max(Params.w, 1.0);
    vec2 screen = vec2(texCoord.x * aspect, texCoord.y);
    vec2 pointer = vec2(Pointer.x * aspect, Pointer.y);
    float toPointer = length(screen - pointer);

    vec3 col = sky(dir);
    vec3 S = normalize(SunDir.xyz);
    float s = max(dot(dir, S), 0.0);

    if (dir.y > 0.05) {
        vec2 sp = vec2(atan(dir.x, dir.z) * 70.0, dir.y * 110.0);
        vec2 cell = floor(sp);
        float r = hash(cell);
        if (r > 0.94) {
            vec2 centre = cell + vec2(hash(cell + 3.1), hash(cell + 7.7));
            float d = length(sp - centre);
            float twinkle = 0.6 + 0.4 * sin(time * (1.5 + r * 4.0) + r * 60.0);
            float near = 1.0 + 2.5 * exp(-toPointer * toPointer * 40.0) * Pointer.z;
            float star = smoothstep(0.16, 0.0, d) * twinkle * near;
            col += vec3(1.0, 0.95, 0.85) * star * smoothstep(0.08, 0.5, dir.y) * 0.9;
        }
    }

    float disc = smoothstep(SunColor.w, SunColor.w * 0.82, acos(clamp(s, -1.0, 1.0)));
    col += SunColor.rgb * (disc * 4.0 + pow(s, 900.0) * 2.0);

    if (dir.y > 0.0) {
        float t = 220.0 / max(dir.y, 0.02);
        vec2 p = dir.xz * t * 0.0045 + vec2(time * 0.012, time * 0.004);
        float d = fbm(p);
        float cover = smoothstep(0.52, 0.78, d) * smoothstep(0.0, 0.18, dir.y);
        vec3 lit = mix(vec3(0.46, 0.32, 0.52), vec3(1.25, 0.82, 0.52), pow(s, 2.0) * 0.8 + 0.2);
        lit = mix(lit, SunColor.rgb * 1.3, pow(s, 12.0));
        float edge = smoothstep(0.52, 0.6, d) - smoothstep(0.6, 0.78, d);
        col = mix(col, lit, cover * 0.85);
        col += SunColor.rgb * edge * pow(s, 3.0) * 0.5;
    }

    col += vec3(1.0, 0.85, 0.6) * exp(-toPointer * toPointer * 14.0) * Pointer.z * 0.18;

    for (int i = 0; i < 4; i++) {
        vec4 sh = Shooting[i];
        if (sh.w < 0.0 || sh.w > 1.4) {
            continue;
        }
        vec2 start = vec2(sh.x * aspect, sh.y);
        vec2 heading = vec2(cos(sh.z), sin(sh.z));
        float travel = sh.w * 0.9;
        vec2 head = start + heading * travel;
        vec2 tail = head - heading * min(travel, 0.22);
        vec2 seg = segment(screen, tail, head);
        float width = 0.0016 + 0.0022 * seg.y;
        float line = exp(-pow(seg.x / width, 2.0)) * seg.y * seg.y;
        float life = smoothstep(0.0, 0.12, sh.w) * (1.0 - smoothstep(0.8, 1.4, sh.w));
        col += vec3(1.0, 0.94, 0.82) * line * life * 1.6;
        col += vec3(1.0, 0.9, 0.7) * exp(-pow(length(screen - head) / 0.006, 2.0)) * life;
    }

    col = filmic(col * 1.05);
    float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    fragColor = vec4(col + dither / 255.0, 1.0);
}
