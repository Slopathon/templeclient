#version 330

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

const float TEMPLE_FOG_MARK = -10000.0;

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (fogEnd < 0.0) {
        return clamp(-fogEnd, 0.0, 1.0);
    }
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }

    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

float temple_fog_value(float vertexDistance, float packedStart, float packedEnd) {
    float code = TEMPLE_FOG_MARK - packedStart;
    float end = floor(code / 1024.0 + 0.001);
    float begin = code - end * 1024.0;
    float thickness = clamp(floor(packedEnd / 1000.0 + 0.0001) / 100.0, 0.0, 1.0);
    float gradient = packedEnd - floor(packedEnd / 1000.0 + 0.0001) * 1000.0;
    float g = exp2((gradient - 50.0) / 25.0);
    float u = clamp((vertexDistance - begin) / max(end - begin, 0.5), 0.0, 1.0);
    return thickness * (1.0 - exp(-3.0 * pow(u, g))) / (1.0 - exp(-3.0));
}

float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd) {
    float environment = environmentalStart <= TEMPLE_FOG_MARK
            ? temple_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd)
            : linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd);
    return max(environment, linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd));
}

vec4 apply_fog(vec4 inColor, float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd, vec4 fogColor) {
    float fogValue = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, environmentalStart, environmantalEnd, renderDistanceStart, renderDistanceEnd);
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

float fog_spherical_distance(vec3 pos) {
    return length(pos);
}

float fog_cylindrical_distance(vec3 pos) {
    float distXZ = length(pos.xz);
    float distY = abs(pos.y);
    return max(distXZ, distY);
}
