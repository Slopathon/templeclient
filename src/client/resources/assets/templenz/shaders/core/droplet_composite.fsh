#version 330

uniform sampler2D InSampler;
uniform sampler2D ThickSampler;
uniform sampler2D SceneSampler;

layout(std140) uniform DropletConfig {
    mat4 ViewProj;
    mat4 InvViewProj;
    vec4 Right;
    vec4 Up;
    vec4 Sun;
    vec4 Sky;
    vec4 Screen;
    vec4 Time;
};

in vec2 texCoord;

out vec4 fragColor;

vec3 rayAt(vec2 uv) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, Right.w > 0.5 ? 0.5 : 0.0, 1.0);
    return normalize(p.xyz / p.w);
}

vec3 pointAt(vec2 uv, out bool ok) {
    vec4 s = texture(InSampler, uv);
    ok = s.g > 0.02;
    return rayAt(uv) * s.r;
}

void main() {
    vec4 here = texture(InSampler, texCoord);
    if (here.g < 0.02) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 V = rayAt(texCoord);
    vec3 P = V * here.r;
    bool okA;
    bool okB;
    vec2 px = vec2(Screen.x * 2.0, 0.0);
    vec2 py = vec2(0.0, Screen.y * 2.0);
    vec3 xa = pointAt(texCoord + px, okA) - P;
    vec3 xb = P - pointAt(texCoord - px, okB);
    vec3 dx = !okA ? xb : !okB ? xa : (dot(xa, xa) < dot(xb, xb) ? xa : xb);
    vec3 ya = pointAt(texCoord + py, okA) - P;
    vec3 yb = P - pointAt(texCoord - py, okB);
    vec3 dy = !okA ? yb : !okB ? ya : (dot(ya, ya) < dot(yb, yb) ? ya : yb);
    vec3 N = normalize(cross(dx, dy));
    if (dot(N, V) > 0.0) {
        N = -N;
    }

    float thick = texture(ThickSampler, texCoord).r;
    float light = Sky.a;
    vec2 bend = (ViewProj * vec4(N, 0.0)).xy * 0.04 * clamp(thick * 3.0, 0.0, 1.0);
    vec3 behind = texture(SceneSampler, clamp(texCoord + bend, vec2(0.001), vec2(0.999))).rgb;
    behind = pow(behind, vec3(1.15)) * 0.82;
    vec3 T = exp(-vec3(0.45, 0.07, 0.04) * thick);
    vec3 deep = vec3(0.012, 0.045, 0.055) * light;
    vec3 body = behind * T + deep * (1.0 - T);

    vec3 R = reflect(V, N);
    float cosI = clamp(dot(N, -V), 0.0, 1.0);
    float F = 0.02 + 0.98 * pow(1.0 - cosI, 5.0);
    vec3 sky = mix(Sky.rgb * 0.85 + 0.1, Sky.rgb, clamp(R.y * 2.0, 0.0, 1.0));
    sky = mix(sky, vec3(0.7, 0.73, 0.78), Screen.w * 0.8) * light;
    vec3 col = mix(body, sky, F);
    vec3 sunDir = normalize(vec3(Sun.x, Sun.y, 0.0));
    float s = max(dot(R, sunDir), 0.0);
    col += vec3(1.0, 0.95, 0.85) * (pow(s, 400.0) * 6.0 + pow(s, 30.0) * 0.08) * Sun.w * (1.0 - Screen.w);

    float alpha = smoothstep(0.02, 0.35, here.g) * clamp(here.b, 0.0, 1.0);
    fragColor = vec4(col, alpha);
}
