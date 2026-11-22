#version 400

// ---------------------------------------------------------------------------
//  Bedrock water - all declarations and helpers used by bedrock_water.vsh/.fsh.
// ---------------------------------------------------------------------------

uniform sampler2D BedrockSceneSampler;
uniform sampler2D BedrockDepthSampler;
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

vec3 bs_reconstruct_view(float depthValue, vec2 uv) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depthValue * 2.0 - 1.0, 1.0);
    vec4 view = BedrockProjectionInv * ndc;
    return view.xyz / view.w;
}

// Cheap screen-space reflection march against the opaque scene depth buffer.
float bs_reflection_march(vec3 eyeWorld, vec3 reflectDir, float maxDistance) {
    float stepSize = maxDistance / 12.0;
    vec3 pos = eyeWorld;
    for (int i = 0; i < 12; i++) {
        pos += reflectDir * stepSize;
        vec4 clip = ProjMat * (BedrockViewMat * vec4(pos, 1.0));
        if (clip.w <= 0.0) {
            return 0.0;
        }
        vec2 uv = (clip.xy / clip.w) * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            return 0.0;
        }
        float sceneDepth = texture(BedrockDepthSampler, uv).r;
        vec3 sceneView = bs_reconstruct_view(sceneDepth, uv);
        vec3 sampleView = (BedrockViewMat * vec4(pos, 1.0)).xyz;
        if (abs(sampleView.z - sceneView.z) < 0.4) {
            return smoothstep(0.0, 0.12, min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y)));
        }
    }
    return 0.0;
}

float bs_water_caustics(vec2 worldXZ) {
    vec2 p = worldXZ * 0.55 + BedrockGameTime * 0.06;
    return pow(abs(sin(p.x) * sin(p.y)), 3.0) * BedrockCaustics * 0.35;
}
