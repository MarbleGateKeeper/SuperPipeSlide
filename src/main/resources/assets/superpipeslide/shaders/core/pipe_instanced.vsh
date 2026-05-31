#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

#ifndef PIPE_INSTANCE_RECORD_VEC4S
#define PIPE_INSTANCE_RECORD_VEC4S 8
#endif

#ifndef PIPE_INSTANCE_CHUNK_CAPACITY
#define PIPE_INSTANCE_CHUNK_CAPACITY 128
#endif

in vec3 Position;

layout(std140) uniform PipeInstances {
    vec4 InstanceData[PIPE_INSTANCE_CHUNK_CAPACITY * PIPE_INSTANCE_RECORD_VEC4S];
};

#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
#else
out vec4 vertexColor;
#endif

#ifndef EMISSIVE
out vec4 lightMapColor;
#endif

out vec2 texCoord0;

void main() {
    int base = gl_InstanceID * PIPE_INSTANCE_RECORD_VEC4S;
    vec4 p0 = InstanceData[base];
    vec4 p1 = InstanceData[base + 1];
    vec4 p2 = InstanceData[base + 2];
    vec4 p3 = InstanceData[base + 3];
    vec4 color = InstanceData[base + 4];
    vec4 normalData = InstanceData[base + 5];
    vec4 light01 = InstanceData[base + 6];
    vec4 light32 = InstanceData[base + 7];

    vec2 corner = clamp(Position.xy, vec2(0.0), vec2(1.0));
    vec3 top = mix(p0.xyz, p1.xyz, corner.x);
    vec3 bottom = mix(p3.xyz, p2.xyz, corner.x);
    vec3 pos = mix(top, bottom, corner.y) + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    vec3 normal = normalData.xyz;
    if (dot(normal, normal) < 0.00000001) {
        normal = vec3(0.0, 1.0, 0.0);
    } else {
        normal = normalize(normal);
    }

#if defined(NO_CARDINAL_LIGHTING)
    vertexColor = color;
#elif defined(PER_FACE_LIGHTING)
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, color);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, color);
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, color);
#endif

#ifndef EMISSIVE
    vec2 topLight = mix(light01.xy, light01.zw, corner.x);
    vec2 bottomLight = mix(light32.xy, light32.zw, corner.x);
    vec2 packedLight = mix(topLight, bottomLight, corner.y);
    lightMapColor = sample_lightmap(Sampler2, ivec2(packedLight + vec2(0.5)));
#endif

    float u = mix(p0.w, p1.w, corner.x);
    float v = mix(p2.w, p3.w, corner.y);
    texCoord0 = vec2(u, v);

#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(texCoord0, 0.0, 1.0)).xy;
#endif
}
