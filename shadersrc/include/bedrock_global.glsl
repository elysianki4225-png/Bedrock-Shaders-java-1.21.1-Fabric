#version 150

// ---------------------------------------------------------------------------
//  Bedrock Shaders - shared uniforms, helpers and PBR-ish utilities.
//  Injected into every overridden core shader by the runtime pack builder.
//  Uniforms that do not exist in the current JSON program are simply optimised
//  away by the GLSL compiler and cost nothing.
// ---------------------------------------------------------------------------

// Supported optional uniforms (must be declared by the JSON we generate):
//   mat4 BedrockViewMat          - view matrix (camera rotation + inverse translation)
//   mat4 BedrockInvViewMat       - inverse view matrix (world orientation -> view space)
//   vec4 BedrockSunDir           - xyz = direction *towards* the sun, w = intensity
//   vec4 BedrockMoonDir          - xyz = direction towards the moon, w = intensity
//   vec3 BedrockSkyZenith        - linear zenith colour
//   vec3 BedrockSkyHorizon       - linear horizon colour
//   float BedrockExposure        - pre-tonemap exposure scaler
//   float BedrockGameTime        - seconds since world load
//   float BedrockRain            - 0..1 rain gradient
//   float BedrockUnderwater      - 0..1 underwater mask
//   vec3 BedrockWaterFogColor    - linear water fog colour
//   vec3 BedrockFogColorLinear   - linear atmospheric fog colour
//   float BedrockCloudHeight
//   float BedrockSunAngle        - 0..1 normalised bed-time (same as Bedrock)

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int FogShape;

uniform mat4 BedrockViewMat;
uniform mat4 BedrockInvViewMat;
uniform vec4 BedrockSunDir;
uniform vec4 BedrockMoonDir;
uniform vec3 BedrockSkyZenith;
uniform vec3 BedrockSkyHorizon;
uniform float BedrockExposure;
uniform float BedrockGameTime;
uniform float BedrockRain;
uniform float BedrockUnderwater;
uniform vec3 BedrockWaterFogColor;
uniform vec3 BedrockFogColorLinear;
uniform float BedrockCloudHeight;
uniform float BedrockSunAngle;
uniform float BedrockFogDensity;

#define BS_PI 3.141592653589793
#define BS_SUN_I max(BedrockSunDir.w, 0.0)
#define BS_MOON_I max(BedrockMoonDir.w, 0.0)

vec3 bs_sunDir()   { return normalize(BedrockSunDir.xyz + vec3(0.0, 1.0e-5, 0.0)); }
vec3 bs_moonDir()  { return normalize(BedrockMoonDir.xyz + vec3(0.0, 1.0e-5, 0.0)); }

float bs_saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3  bs_saturate(vec3 x)  { return clamp(x, vec3(0.0), vec3(1.0)); }

float bs_fog_distance(vec3 pos, int shape) {
    if (shape == 0) {
        return length(pos);
    }
    float distXZ = length(pos.xz);
    float distY = abs(pos.y);
    return max(distXZ, distY);
}

// Exponential height fog with a soft analytic integral, still respecting the
// FogStart/FogEnd window so it blends with vanilla set-ups.
float bs_height_fog(vec3 worldPos, float density) {
    float heightFalloff = 0.045;
    float base = 62.0;
    float dy = worldPos.y - base;
    float integral = (exp(-heightFalloff * clamp(dy, -64.0, 320.0)) - 1.0) / -heightFalloff;
    return exp(-density * integral * 1.0e-3);
}

// Minecrftos-style normal decoding for vanilla block normals (0..255 octahedral-ish).
vec3 bs_decode_normal(vec3 normal) {
    return normalize(normal * 2.0 - 1.0);
}

// GGX / Smith specular highlight without any lookup textures.
float bs_distribution_ggx(float NdotH, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float d = (NdotH * a2 - NdotH) * NdotH + 1.0;
    return a2 / (BS_PI * d * d + 1.0e-5);
}

float bs_geometry_smith(float NdotV, float NdotL, float roughness) {
    float k = roughness * 0.5;
    float gv = NdotV / (NdotV * (1.0 - k) + k);
    float gl = NdotL / (NdotL * (1.0 - k) + k);
    return gv * gl;
}

vec3 bs_fresnel_schlick(float cosTheta, vec3 f0) {
    return f0 + (1.0 - f0) * pow(1.0 - cosTheta, 5.0);
}

// Environment BRDF approximation (Karis) - cheap and stable.
vec3 bs_env_brdf_approx(vec3 f0, float roughness, float NdotV) {
    vec4 c0 = vec4(-1.0, -0.0275, -0.572, 0.022);
    vec4 c1 = vec4(1.0, 0.0425, 1.04, -0.04);
    vec4 r = roughness * c0 + c1;
    float a004 = min(r.x * r.x, exp2(-9.28 * NdotV)) * r.x + r.y;
    vec2 ab = vec2(-1.04, 1.04) * a004 + r.zw;
    return f0 * ab.x + ab.y;
}

// Analytic sky used by every surface shader for ambient response.
vec3 bs_sky_radiance(vec3 dir) {
    float up = dir.y * 0.5 + 0.5;
    vec3 sky = mix(BedrockSkyHorizon, BedrockSkyZenith, pow(bs_saturate(up), 0.65));
    float sunAmount = pow(bs_saturate(dot(dir, bs_sunDir())), 8.0);
    float moonAmount = pow(bs_saturate(dot(dir, bs_moonDir())), 32.0);
    sky += vec3(1.0, 0.82, 0.62) * sunAmount * BS_SUN_I * 0.35;
    sky += vec3(0.55, 0.68, 1.0) * moonAmount * BS_MOON_I * 0.12;
    return sky * BedrockExposure;
}

vec3 bs_apply_fog(vec3 color, float vertexDistance) {
    float density = max(BedrockFogDensity, 0.0);
    float start = FogStart;
    float end = max(FogEnd, FogStart + 1.0e-3);
    float window = smoothstep(start, end, vertexDistance);
    // Convert the Bedrock exponential atmospheric term into a mix factor.
    float expTerm = 1.0 - exp(-vertexDistance * 0.0022 * density);
    float fog = clamp(mix(window, expTerm, 0.35 + 0.35 * BedrockRain), 0.0, 1.0);
    vec3 fogCol = mix(BedrockFogColorLinear, BedrockWaterFogColor, BedrockUnderwater * 0.85);
    return mix(color, fogCol * BedrockExposure, fog * FogColor.a);
}
