#version 400

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform float BedrockMaterialGloss;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec3 worldNormal;
out float materialGloss;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = bs_fog_distance(Position, FogShape);
    vertexColor = Color;
    worldNormal = Normal;
    materialGloss = clamp(0.5 + Normal.y * 0.5, 0.0, 1.0) * BedrockMaterialGloss;
    lightMapColor = texture(Sampler2, clamp(vec2(UV2) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
