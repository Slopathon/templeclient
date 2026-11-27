#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec3 rel;
in float shellDistance;
in float shellSpan;

out vec4 fragColor;

vec4 hash4(vec3 p) {
    vec4 q = vec4(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)),
            dot(p, vec3(113.5, 271.9, 124.6)), dot(p, vec3(419.2, 371.9, 168.2)));
    return fract(sin(q) * 43758.5453);
}

float flakeShape(vec2 q, float seed, float pxr) {
    float r = length(q);
    if (r > 1.05) {
        return 0.0;
    }
    float sector = 1.0471976;
    float a = mod(atan(q.y, q.x) + sector * 0.5, sector) - sector * 0.5;
    vec2 p = r * vec2(cos(a), abs(sin(a)));
    float armWidth = 0.07 * (1.0 - p.x) + 0.018;
    float arm = smoothstep(armWidth + pxr, armWidth - pxr, p.y) * smoothstep(1.0 + pxr, 1.0 - pxr, p.x);
    float spacing = 0.24 + 0.1 * seed;
    float base = p.x - mod(p.x, spacing);
    vec2 rel = vec2(p.x - base, p.y);
    vec2 bd = vec2(0.5, 0.8660254);
    float along = dot(rel, bd);
    float across = length(rel - bd * along);
    float branchWidth = 0.045 * (1.0 - p.x) + 0.012;
    float branchLength = (1.0 - base) * (0.35 + 0.25 * seed);
    float branch = smoothstep(branchWidth + pxr, branchWidth - pxr, across)
            * smoothstep(-pxr, pxr, along) * smoothstep(branchLength + pxr, branchLength - pxr, along)
            * step(0.12, base);
    float hub = smoothstep(0.2 + pxr, 0.2 - pxr, r);
    return max(max(arm, branch), hub);
}
bool underSky(vec3 fromCamera) {
    vec2 m = fromCamera.xz - vec2(TextureMat[2].x, TextureMat[2].y);
    float size = TextureMat[3].y;
    if (m.x < 0.0 || m.y < 0.0 || m.x >= size || m.y >= size) {
        return true;
    }
    vec4 h = texture(Sampler0, (floor(m) + 0.5) / size);
    float ground = floor(h.r * 255.0 + 0.5) * 256.0 + floor(h.g * 255.0 + 0.5) + TextureMat[3].x;
    return TextureMat[2].z + fromCamera.y >= ground;
}

void main() {
    float t = length(rel);
    if (t > TextureMat[0].w) {
        discard;
    }
    vec3 dir = rel / t;
    float time = TextureMat[1].z;
    float cell = clamp(shellSpan * 0.6, 0.22, 5.0);
    float cells = max(1.0, floor(1024.0 / cell + 0.5));
    cell = 1024.0 / cells;
    vec3 snowPos = ModelOffset + rel;
    vec3 id = floor(snowPos / cell);
    float alpha = 0.0;
    for (int n = 0; n < 2; n++) {
        vec3 cid = id + (n == 0 ? vec3(0.0) : vec3(0.0, 1.0, 0.0));
        vec4 h = hash4(mod(cid, cells));
        if (h.w > TextureMat[1].x) {
            continue;
        }
        vec3 centre = (cid + 0.15 + 0.7 * h.xyz) * cell;
        centre.x += sin(time * (0.7 + h.x) + h.y * 40.0) * 0.12 * min(cell, 1.0);
        centre.z += cos(time * (0.6 + h.z) + h.x * 40.0) * 0.12 * min(cell, 1.0);
        vec3 c = centre - ModelOffset;
        float along = dot(c, dir);
        if (along < 0.3) {
            continue;
        }
        float window = 1.0 - clamp(abs(along - t) / (shellSpan * 1.2), 0.0, 1.0);
        if (window <= 0.0) {
            continue;
        }
        vec3 off = c - dir * along;
        float size = TextureMat[1].y * (0.6 + 0.8 * h.z);
        float px = along * TextureMat[1].w;
        float detail = smoothstep(2.5, 5.0, size / max(px, 1e-5));
        vec3 nrm = normalize(vec3(sin(time * (0.5 + h.x) + h.y * 20.0), 1.1 + cos(time * (0.4 + h.z) + h.x * 20.0),
                sin(time * (0.6 + h.y) + h.z * 20.0)));
        float coverage = 0.0;
        float glint = 0.0;
        if (detail > 0.0) {
            float facingRay = dot(dir, nrm);
            float hitT = dot(c, nrm) / (abs(facingRay) < 1e-3 ? 1e-3 : facingRay);
            vec3 hit = dir * hitT - c;
            vec3 ax = normalize(cross(nrm, vec3(0.31, 0.94, 0.17)));
            vec3 ay = cross(nrm, ax);
            vec2 q = vec2(dot(hit, ax), dot(hit, ay)) / size;
            float edgeOn = smoothstep(0.05, 0.3, abs(facingRay));
            coverage = flakeShape(q, h.x, px / size / max(abs(facingRay), 0.2)) * edgeOn * detail;
            glint = pow(max(0.0, dot(reflect(dir, nrm), normalize(vec3(0.3, 0.9, 0.2)))), 24.0) * coverage;
        }
        if (detail < 1.0) {
            off.y *= 0.6;
            float r = max(size * 0.45, px * 0.75);
            float energy = (size * size * 0.55) / (r * r);
            float disc = 1.0 - smoothstep(r - px * 0.5, r + px * 0.5, length(off));
            coverage += disc * min(energy, 1.0) * (1.0 - detail);
        }
        if (coverage <= 0.0 || !underSky(c)) {
            continue;
        }
        float sparkle = 0.75 + 0.25 * sin(time * (2.0 + 3.0 * h.y) + h.x * 60.0);
        alpha = max(alpha, clamp(coverage * window * sparkle * 0.92 + glint * window, 0.0, 1.0));
    }
    float veil = TextureMat[2].w * shellSpan / TextureMat[0].w * (underSky(rel) ? 1.0 : 0.25);
    float a = clamp(alpha * ColorModulator.a + veil, 0.0, 1.0);
    if (a <= 0.002) {
        discard;
    }
    fragColor = vec4(ColorModulator.rgb, a);
}
