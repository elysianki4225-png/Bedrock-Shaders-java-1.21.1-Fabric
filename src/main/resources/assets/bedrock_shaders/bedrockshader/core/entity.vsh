#version 400

#moj_import <bedrock_global.glsl>
#moj_import <common_lighting.glsl>


in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec3 worldNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = bs_fog_distance(Position, FogShape);
    vertexColor = Color;
    worldNormal = normalize(Normal);
    lightMapColor = texture(Sampler2, clamp(vec2(UV2) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
