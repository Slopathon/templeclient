#version 330

in vec4 vertexColor;
in vec2 shape;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

void main() {
    float kind = floor((shape.x + 2.0) / 4.0);
    float cell = floor((shape.y + 2.0) / 4.0);
    vec2 p = vec2(shape.x - kind * 4.0, shape.y - cell * 4.0);
    vec4 colour = vertexColor;
    if (kind > 0.5) {
        float r = length(p);
        float a;
        float hot = 0.0;
        if (kind < 1.5) {
            a = exp(-3.2 * r * r) * (1.0 - smoothstep(0.72, 1.0, r));
            hot = exp(-r * r * 26.0);
        } else if (kind < 2.5) {
            float y = abs(p.y);
            a = exp(-3.0 * y * y) * (1.0 - smoothstep(0.78, 1.0, y));
            hot = exp(-y * y * 22.0);
        } else if (kind < 3.5) {
            vec2 q = abs(p);
            float core = exp(-r * r * 38.0);
            float rays = exp(-q.x * 20.0) * exp(-q.y * 2.4) + exp(-q.y * 20.0) * exp(-q.x * 2.4);
            float halo = exp(-r * r * 5.0) * 0.28;
            a = min(1.0, core + rays * 0.85 + halo) * (1.0 - smoothstep(0.8, 1.0, r));
            hot = core + rays * 0.5;
        } else if (kind < 4.5) {
            vec2 o = vec2(cell * 7.13, cell * 3.71);
            float n = noise(p * 1.7 + o) * 0.6 + noise(p * 3.9 - o) * 0.4;
            float edge = r * (0.75 + 0.55 * n);
            a = pow(1.0 - smoothstep(0.15, 1.0, edge), 1.4) * (0.6 + 0.4 * n);
            cell = 0.0;
        } else if (kind < 5.5) {
            float d = (r - 0.8) / 0.11;
            a = exp(-d * d) + exp(-r * r * 4.0) * 0.06;
            a *= 1.0 - smoothstep(0.93, 1.0, r);
            hot = exp(-d * d * 6.0);
        } else if (kind < 6.5) {
            float w = max(fwidth(r), 1e-4);
            a = 1.0 - smoothstep(1.0 - w * 1.5, 1.0, r);
        } else {
            a = exp(-p.y * p.y * 60.0) * exp(-p.x * p.x * 2.2) + exp(-r * r * 30.0) * 0.8;
            a *= 1.0 - smoothstep(0.85, 1.0, max(abs(p.x), r * 0.5));
            hot = exp(-r * r * 30.0);
        }
        colour.a *= a;
        colour.rgb = mix(colour.rgb, vec3(1.0), clamp(hot * cell / 8.0, 0.0, 1.0));
    }
    if (colour.a < 0.002) {
        discard;
    }
    colour.rgb += (hash(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = colour;
}
