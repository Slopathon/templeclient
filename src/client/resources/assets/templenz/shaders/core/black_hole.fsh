#version 330

layout(std140) uniform BlackHoleConfig {
    vec4 Params;
    vec4 CamPos;
    vec4 CamRight;
    vec4 CamUp;
    vec4 CamForward;
    vec4 Accent;
    vec4 Centre;
};

in vec2 texCoord;
out vec4 fragColor;

const float R_IN = 2.9;
const float R_OUT = 11.5;
const float TAU = 6.2831853;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float pnoise(vec2 p, float period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, period);
    float x1 = mod(i.x + 1.0, period);
    float a = hash21(vec2(x0, i.y));
    float b = hash21(vec2(x1, i.y));
    float c = hash21(vec2(x0, i.y + 1.0));
    float d = hash21(vec2(x1, i.y + 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float pfbm(vec2 p, float period) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * pnoise(p, period);
        p = p * 2.0 + vec2(0.0, 13.7);
        period *= 2.0;
        a *= 0.5;
    }
    return s;
}

float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i);
    float n100 = hash31(i + vec3(1, 0, 0));
    float n010 = hash31(i + vec3(0, 1, 0));
    float n110 = hash31(i + vec3(1, 1, 0));
    float n001 = hash31(i + vec3(0, 0, 1));
    float n101 = hash31(i + vec3(1, 0, 1));
    float n011 = hash31(i + vec3(0, 1, 1));
    float n111 = hash31(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y), mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float gas(float r, float phi, float seed) {
    float around = phi / TAU;
    float n = pfbm(vec2(around * 22.0, r * 3.1 + seed), 22.0);
    float streak = pnoise(vec2(around * 96.0, r * 15.0 + seed * 3.0), 96.0);
    return n * 0.78 + streak * 0.32;
}

vec4 disk(vec3 p, float r, vec3 dir) {
    float time = Params.x;
    float phi = atan(p.z, p.x);
    float omega = 1.9 * pow(r, -1.5);
    float cycle = 14.0;
    float a = fract(time / cycle);
    float b = fract(time / cycle + 0.5);
    float wa = 1.0 - abs(2.0 * a - 1.0);
    float n = mix(gas(r, phi - omega * b * cycle, 7.31), gas(r, phi - omega * a * cycle, 0.0), wa);

    float inner = smoothstep(R_IN, R_IN + 0.45, r);
    float outer = 1.0 - smoothstep(R_OUT * 0.42, R_OUT, r);
    float fall = pow(R_IN / r, 2.1);
    float heat = clamp(pow(R_IN / r, 0.9), 0.0, 1.0);
    vec3 deep = vec3(0.78, 0.2, 0.035);
    vec3 warm = mix(vec3(1.0, 0.6, 0.24), Accent.rgb * 1.1, Accent.w);
    vec3 white = vec3(1.0, 0.93, 0.83);
    vec3 c = mix(deep, warm, smoothstep(0.22, 0.62, heat));
    c = mix(c, white, smoothstep(0.64, 1.0, heat));

    vec3 v = vec3(-p.z, 0.0, p.x) / r;
    float beta = min(0.62, sqrt(0.5 / max(r - 1.0, 0.1)));
    float gamma = inversesqrt(1.0 - beta * beta);
    float g = 1.0 / (gamma * (1.0 - beta * dot(v, -dir)));
    float boost = mix(1.0, g * g * g, 0.42);
    c = mix(c, c * vec3(0.92, 0.98, 1.12), clamp((g - 1.0) * 1.5, 0.0, 0.5));
    float well = sqrt(max(0.0, 1.0 - 1.0 / r));

    float density = inner * outer * (0.3 + 0.95 * n);
    float alpha = clamp(density * (0.45 + 0.7 * fall), 0.0, 1.0);
    vec3 light = c * (fall * 3.4 + 0.22) * (0.4 + 1.15 * n) * boost * well;
    return vec4(light, alpha);
}

vec3 sky(vec3 d) {
    vec3 col = vec3(0.004, 0.005, 0.01);
    float band = exp(-pow(dot(d, normalize(vec3(0.35, 1.0, -0.25))) * 3.2, 2.0));
    float cloud = valueNoise3(d * 4.0) * 0.6 + valueNoise3(d * 9.0) * 0.4;
    col += vec3(0.06, 0.05, 0.08) * band * smoothstep(0.35, 0.9, cloud);
    for (int layer = 0; layer < 3; layer++) {
        float scale = 280.0 + 240.0 * float(layer);
        vec3 p = d * scale;
        vec3 cell = floor(p);
        float h = hash31(cell + float(layer) * 17.0);
        if (h > 0.988) {
            vec3 jitter = vec3(hash31(cell + 1.3), hash31(cell + 2.7), hash31(cell + 5.1)) - 0.5;
            float dist = length(p - (cell + 0.5 + jitter * 0.5));
            float bright = pow((h - 0.988) / 0.012, 2.0);
            vec3 tint = mix(vec3(1.0, 0.86, 0.7), vec3(0.75, 0.85, 1.0), hash31(cell + 9.1));
            float twinkle = 0.75 + 0.25 * sin(Params.x * (1.0 + h * 3.0) + h * 80.0);
            col += tint * smoothstep(0.42, 0.0, dist) * (0.35 + 2.4 * bright) * twinkle * (1.0 - 0.92 * Centre.z);
        }
    }
    return col;
}

vec3 filmic(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    float aspect = Params.y;
    vec2 uv = (texCoord * 2.0 - 1.0) * vec2(aspect, 1.0) - Centre.xy;
    float cr = cos(Params.z);
    float sr = sin(Params.z);
    uv = vec2(cr * uv.x + sr * uv.y, -sr * uv.x + cr * uv.y);
    vec3 dir = normalize(CamForward.xyz + (uv.x * CamRight.xyz + uv.y * CamUp.xyz) * Params.w);

    vec3 pos = CamPos.xyz;
    vec3 vel = dir;
    vec3 hv = cross(pos, vel);
    float h2 = dot(hv, hv);
    vec3 col = vec3(0.0);
    float cover = 0.0;
    float haze = 0.0;
    bool captured = false;
    for (int i = 0; i < 320; i++) {
        float r = length(pos);
        float dt = clamp(r * CamPos.w, 0.02, 2.5);
        vec3 prev = pos;
        float r2 = r * r;
        vel += -1.5 * h2 * pos / (r2 * r2 * r) * dt * 0.5;
        pos += vel * dt;
        float q = length(pos);
        float q2 = q * q;
        vel += -1.5 * h2 * pos / (q2 * q2 * q) * dt * 0.5;

        if (prev.y * pos.y < 0.0) {
            vec3 hit = mix(prev, pos, prev.y / (prev.y - pos.y));
            float hr = length(hit.xz);
            if (hr > R_IN && hr < R_OUT) {
                vec4 d = disk(hit, hr, normalize(vel));
                col += (1.0 - cover) * d.rgb * d.a;
                cover += (1.0 - cover) * d.a;
            }
        }
        float rr = length(pos.xz);
        float shell = smoothstep(R_IN * 0.85, R_IN + 1.2, rr) * (1.0 - smoothstep(4.5, R_OUT, rr));
        haze += (1.0 - cover) * exp(-abs(pos.y) * 2.4) * shell * pow(R_IN / max(rr, R_IN), 1.6) * dt;

        if (q < 1.0) {
            captured = true;
            break;
        }
        if (cover > 0.985 || (q > 60.0 && dot(pos, vel) > 0.0)) {
            break;
        }
    }
    col += vec3(1.0, 0.62, 0.3) * haze * 0.07;
    if (!captured) {
        col += (1.0 - cover) * sky(normalize(vel));
    }

    col = filmic(col * 1.1);
    float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    fragColor = vec4(col + dither / 255.0, 1.0);
}
