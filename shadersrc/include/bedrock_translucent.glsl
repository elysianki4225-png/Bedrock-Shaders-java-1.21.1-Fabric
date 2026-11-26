#version 400

// ---------------------------------------------------------------------------
//  Bedrock translucency - declarations/helpers used by bedrock_translucent.vsh/.fsh.
//  Water, glass and ice share vanilla's rendertype_translucent program, so this
//  shader applies Bedrock water reflections only to up-facing surfaces.
// ---------------------------------------------------------------------------

// No scene or depth samplers here on purpose. They were declared for a screen-space
// reflection march that nothing ever called, and BedrockUniforms never bound them, so
// calling it would have sampled whatever happened to be on unit 0 - usually the block
// texture itself. That is where the black tile patches on the water came from.
uniform vec2 BedrockWaveOffsetA;
uniform vec2 BedrockWaveOffsetB;
uniform vec3 BedrockSunColorLinear;
uniform vec3 BedrockWaterColorLinear;
uniform vec3 BedrockWaterSurfaceColorLinear;
uniform float BedrockWaterTransparency;
uniform float BedrockWaterWaveStrength;
uniform float BedrockWaterReflection;
uniform float BedrockWaterRefraction;
uniform float BedrockCaustics;
uniform vec2 BedrockScreenSize;
uniform mat4 BedrockProjectionInv;

vec2 bs_water_wave(vec2 worldXZ) {
    vec2 a = worldXZ * 0.31 + BedrockWaveOffsetA;
    vec2 b = worldXZ * 0.17 - BedrockWaveOffsetB;
    return vec2(
        sin(a.x) * 0.5 + sin(b.y * 1.7) * 0.35,
        cos(a.y) * 0.5 + cos(b.x * 1.3) * 0.35
    ) * (0.012 + 0.028 * BedrockWaterWaveStrength);
}

vec3 bs_water_normal(vec3 worldPos, vec3 flatNormal) {
    vec2 slope = bs_water_wave(worldPos.xz);
    return normalize(mat3(BedrockInvViewMat) * (flatNormal + vec3(slope.x, 0.0, slope.y)));
}

float bs_water_caustics(vec2 worldXZ) {
    vec2 p = worldXZ * 0.55 + BedrockGameTime * 0.06;
    return pow(abs(sin(p.x) * sin(p.y)), 3.0) * BedrockCaustics * 0.35;
}
