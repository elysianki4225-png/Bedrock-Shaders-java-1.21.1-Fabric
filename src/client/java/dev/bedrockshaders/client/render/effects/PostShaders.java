package dev.bedrockshaders.client.render.effects;

/**
 * Fullscreen post-processing shaders. These are Bedrock-Shaders specific programs
 * compiled at runtime with plain LWJGL (they are not vanilla core shaders), so they
 * are kept as GLSL 150 core sources that compile on any OpenGL 3.2+ context.
 */
public final class PostShaders {
    private PostShaders() {
    }

    private static final String FULLSCREEN_VSH = """
            #version 150
            in vec3 Position;
            out vec2 texCoord;
            void main() {
                gl_Position = vec4(Position.xy, 0.0, 1.0);
                texCoord = Position.xy * 0.5 + 0.5;
            }
            """;

    public static String fullscreenVertex() {
        return FULLSCREEN_VSH;
    }

    /** SSAO: reconstructs view-space normals from depth and samples a hemisphere. */
    public static String ssaoFragment() {
        return """
                #version 150
                uniform sampler2D DepthSampler;
                uniform mat4 ProjMat;
                uniform mat4 ProjInv;
                uniform vec2 ScreenSize;
                uniform float Radius;
                uniform float Strength;
                uniform float Near;
                uniform float Far;
                in vec2 texCoord;
                out vec4 fragColor;

                vec3 viewPos(vec2 uv, float depth) {
                    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                    vec4 view = ProjInv * ndc;
                    return view.xyz / view.w;
                }

                float hash(vec2 p) {
                    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
                }

                void main() {
                    float depth = texture(DepthSampler, texCoord).r;
                    if (depth >= 0.9999) {
                        fragColor = vec4(1.0);
                        return;
                    }
                    vec3 origin = viewPos(texCoord, depth);
                    vec3 normal = normalize(cross(dFdx(origin), dFdy(origin)));
                    vec3 tangent = normalize(abs(normal.z) < 0.9 ? cross(normal, vec3(0.0, 1.0, 0.0)) : cross(normal, vec3(1.0, 0.0, 0.0)));
                    vec3 bitangent = cross(normal, tangent);

                    float occlusion = 0.0;
                    int samples = 12;
                    float angle = hash(texCoord * ScreenSize) * 6.2831853;
                    for (int i = 0; i < samples; i++) {
                        float r = float(i) / float(samples);
                        float theta = float(i) * 2.39996323 + angle;
                        float h = sqrt(1.0 - r);
                        vec3 dir = vec3(cos(theta) * h, sin(theta) * h, r);
                        vec3 sampleWorld = origin + (tangent * dir.x + bitangent * dir.y + normal * dir.z) * Radius;
                        vec4 clip = ProjMat * vec4(sampleWorld, 1.0);
                        vec2 uv = (clip.xy / clip.w) * 0.5 + 0.5;
                        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                            continue;
                        }
                        float sampleDepth = texture(DepthSampler, uv).r;
                        vec3 sampleView = viewPos(uv, sampleDepth);
                        float range = smoothstep(0.0, 1.0, Radius / max(abs(origin.z - sampleView.z), 1.0e-3));
                        if (sampleView.z >= origin.z + 0.02) {
                            occlusion += range;
                        }
                    }
                    occlusion = clamp(occlusion / float(samples) * Strength, 0.0, 1.0);
                    fragColor = vec4(1.0 - occlusion);
                }
                """;
    }

    /** 4-tap separable bloom downsample + bright pass. */
    public static String bloomFragment() {
        return """
                #version 150
                uniform sampler2D InputSampler;
                uniform vec2 TexelSize;
                uniform float Threshold;
                in vec2 texCoord;
                out vec4 fragColor;

                void main() {
                    vec3 color = texture(InputSampler, texCoord).rgb * 0.25;
                    color += texture(InputSampler, texCoord + vec2(TexelSize.x, 0.0)).rgb * 0.25;
                    color += texture(InputSampler, texCoord + vec2(0.0, TexelSize.y)).rgb * 0.25;
                    color += texture(InputSampler, texCoord + TexelSize).rgb * 0.25;
                    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    float weight = smoothstep(Threshold, Threshold + 0.45, luma);
                    fragColor = vec4(color * weight, 1.0);
                }
                """;
    }

    /** Screen-space god rays: radial samples towards the projected sun. */
    public static String godRaysFragment() {
        return """
                #version 150
                uniform sampler2D SceneSampler;
                uniform sampler2D DepthSampler;
                uniform vec2 ScreenSize;
                uniform vec2 SunScreenPos;
                uniform float SunVisible;
                uniform float Strength;
                in vec2 texCoord;
                out vec4 fragColor;

                void main() {
                    if (SunVisible < 0.001) {
                        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                        return;
                    }
                    vec2 delta = (texCoord - SunScreenPos) / 32.0;
                    vec2 uv = texCoord;
                    float illumination = 0.0;
                    for (int i = 0; i < 32; i++) {
                        uv -= delta;
                        float visibility = step(0.9995, texture(DepthSampler, uv).r);
                        illumination += visibility * (1.0 - float(i) / 32.0);
                    }
                    vec3 sunColor = texture(SceneSampler, clamp(SunScreenPos, vec2(0.0), vec2(1.0))).rgb;
                    fragColor = vec4(sunColor * illumination / 32.0 * Strength, 1.0);
                }
                """;
    }

    /** Final composite: SSAO, bloom, god rays, grading, tone mapping and vignette. */
    public static String compositeFragment() {
        return """
                #version 150
                uniform sampler2D SceneSampler;
                uniform sampler2D BloomSampler;
                uniform sampler2D AoSampler;
                uniform sampler2D RaysSampler;
                uniform vec2 ScreenSize;
                uniform float BloomStrength;
                uniform float AoStrength;
                uniform float RaysStrength;
                uniform float Exposure;
                uniform float Saturation;
                uniform vec3 Gain;
                uniform vec3 Offset;
                uniform vec3 Gamma;
                uniform vec3 Contrast;
                uniform float Vignette;
                uniform float Underwater;
                uniform float Time;
                in vec2 texCoord;
                out vec4 fragColor;

                vec3 tonemap(vec3 x) {
                    // Bedrock "generic" operator: filmic ACES-style curve.
                    vec3 c = max(x, 0.0);
                    return (c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14);
                }

                void main() {
                    vec2 uv = texCoord;
                    if (Underwater > 0.01) {
                        uv += vec2(sin(uv.y * 28.0 + Time * 1.7), cos(uv.x * 24.0 + Time * 1.3)) * 0.0016 * Underwater;
                    }
                    vec3 color = texture(SceneSampler, uv).rgb;
                    float ao = texture(AoSampler, texCoord).r;
                    color *= mix(1.0, ao, AoStrength);
                    color += texture(BloomSampler, texCoord).rgb * BloomStrength;
                    color += texture(RaysSampler, texCoord).rgb * RaysStrength;

                    color *= Exposure;

                    color = color * Gain + Offset;
                    color = (color - 0.5) * Contrast + 0.5;

                    // Tonemap while still linear, then encode for display. Doing the
                    // transfer function here - once, at the very end - is what makes the
                    // image correct: the core shaders emit linear radiance and nothing
                    // else in the chain applies a curve.
                    color = tonemap(color);
                    color = pow(clamp(color, 0.0, 1.0), vec3(1.0 / 2.2));

                    // Gamma above is Bedrock's per-pack midtone curve, applied after the
                    // display encode so a pack can still tint midtones.
                    color = pow(clamp(color, 0.0, 1.0), Gamma);

                    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    color = mix(vec3(luma), color, Saturation);

                    float vignette = smoothstep(1.2, 0.35, length(texCoord - 0.5) * 2.0);
                    color *= mix(1.0, vignette, Vignette);

                    fragColor = vec4(color, 1.0);
                }
                """;
    }

    /** Sky: Bedrock scattering gradient with sun/moon discs and stars. */
    public static String skyFragment() {
        return """
                #version 150
                uniform mat4 InvViewProj;
                uniform vec3 SunDir;
                uniform vec3 MoonDir;
                uniform vec3 Zenith;
                uniform vec3 Horizon;
                uniform vec3 SunColor;
                uniform vec3 MoonColor;
                uniform float SunIntensity;
                uniform float MoonIntensity;
                uniform float Stars;
                uniform float Time;
                uniform vec3 CameraPos;
                in vec2 texCoord;
                out vec4 fragColor;

                float hash13(vec3 p) {
                    p = fract(p * 0.1031);
                    p += dot(p, p.yzx + 33.33);
                    return fract((p.x + p.y) * p.z);
                }

                vec3 stars(vec3 dir) {
                    vec3 p = dir * 220.0;
                    vec3 cell = floor(p);
                    float h = hash13(cell);
                    if (h < 0.9975) {
                        return vec3(0.0);
                    }
                    vec3 center = cell + 0.5 + (vec3(hash13(cell + 1.7), hash13(cell + 3.1), hash13(cell + 5.3)) - 0.5) * 0.6;
                    float d = length(p - center);
                    float twinkle = 0.65 + 0.35 * sin(Time * 1.7 + h * 40.0);
                    return vec3(smoothstep(0.35, 0.0, d) * twinkle);
                }

                void main() {
                    vec4 clip = vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
                    vec4 world = InvViewProj * clip;
                    vec3 dir = normalize(world.xyz / world.w - CameraPos);

                    float up = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
                    vec3 sky = mix(Horizon, Zenith, pow(up, 0.62));

                    float sunDot = max(dot(dir, SunDir), 0.0);
                    float moonDot = max(dot(dir, MoonDir), 0.0);
                    sky += SunColor * pow(sunDot, 8.0) * SunIntensity * 0.55;
                    sky += SunColor * pow(sunDot, 900.0) * SunIntensity * 24.0;
                    sky += MoonColor * pow(moonDot, 20.0) * MoonIntensity * 0.35;
                    sky += MoonColor * pow(moonDot, 2400.0) * MoonIntensity * 9.0;

                    float night = smoothstep(0.15, -0.25, SunDir.y);
                    sky += stars(dir) * Stars * night;

                    float horizon = pow(1.0 - abs(dir.y), 6.0);
                    sky = mix(sky, Horizon, horizon * 0.35);

                    fragColor = vec4(sky, 1.0);
                }
                """;
    }
}
