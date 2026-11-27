#version 330

// Scene depth as 26.x has it (1 near, 0 for the sky); 1.21.11 stores it the other way round.
//? if >=26 {
#define SCENE_DEPTH(d) (d)
//?} else {
/*#define SCENE_DEPTH(d) (1.0 - (d))
*///?}

// Ambience's colour filter (see ScreenFilter): a photo-style grade over the game picture.

uniform sampler2D InSampler;
// The world's depth: 0 where nothing was drawn (the sky; 26.2 clears depth to 0 and nearer is larger).
uniform sampler2D DepthSampler;

layout(std140) uniform FilterConfig {
    vec4 GradeA;   // x = brightness, y = contrast, z = saturation, w = warmth (each about -1..1)
    vec4 GradeB;   // x = tint amount, y = vignette, z = grain (0..1), w = seconds
    vec4 TintCol;  // rgb = tint colour, a = aspect ratio
    vec4 Extra;    // x = 1 to leave the sky's colour alone (a world effect tones the world, not the sky)
};

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec3 original = c;
    float sky = SCENE_DEPTH(texture(DepthSampler, texCoord).r) <= 0.0 ? Extra.x : 0.0;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    // Saturation around the pixel's own brightness.
    c = mix(vec3(l), c, max(0.0, 1.0 + GradeA.z));
    // Warmth: toward orange and away from blue (or the other way), kept roughly brightness-neutral.
    c *= vec3(1.0 + 0.22 * GradeA.w, 1.0 + 0.04 * GradeA.w, 1.0 - 0.25 * GradeA.w);
    // Tint: wash toward the colour, keeping light and shade.
    float tl = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(c, tl * TintCol.rgb * 1.6, GradeB.x);
    // Contrast around mid grey (a gentle S-curve, so it does not clip as quickly as a straight scale).
    c = clamp(c, 0.0, 1.0);
    vec3 s = c * c * (3.0 - 2.0 * c);
    c = GradeA.y >= 0.0 ? mix(c, s, GradeA.y * 1.6) : mix(c, vec3(0.5), -GradeA.y * 0.8);
    c += GradeA.x;
    c = mix(c, original, sky);
    // Vignette: darker toward the corners, round on any screen shape.
    vec2 d = (texCoord - 0.5) * vec2(TintCol.a, 1.0);
    float v = smoothstep(0.35, 1.05, length(d) * 1.25);
    c *= 1.0 - GradeB.y * v * 0.85;
    // Film grain that moves every frame.
    float n = hash(gl_FragCoord.xy + fract(GradeB.w * 13.7) * 917.0) - 0.5;
    c += n * GradeB.z * 0.12;
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
