#version 330
#extension GL_ARB_separate_shader_objects : require

// Kraken liquid glass.
// Sampler0  : blurred copy of the world behind the GUI
// texCoord0 : screen position of this fragment (uv, y up)
// vertexColor.r  : signed distance to the glass edge, encoded (see LiquidGlass.java)
// vertexColor.gb : outward edge normal in GUI space (x right, y down), encoded 0..1
// vertexColor.a  : opacity of the whole element (pop-in animation)
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

const float BEVEL = 9.0;   // GUI px over which the edge bends the light
const float OUTER = 2.0;   // GUI px of the encoded range outside the edge

void main() {
    float sdf = vertexColor.r * (BEVEL + OUTER) - BEVEL;           // < 0 inside
    float aa = max(fwidth(sdf), 1e-4);
    float coverage = clamp(0.5 - sdf / aa, 0.0, 1.0) * vertexColor.a;
    if (coverage <= 0.002) discard;

    vec2 n = vertexColor.gb * 2.0 - 1.0;
    float nl = length(n);
    n = nl > 1e-3 ? n / nl : vec2(0.0);
    vec2 nuv = vec2(n.x, -n.y);                                    // uv space is y-up

    float edge = clamp(1.0 + sdf / BEVEL, 0.0, 1.0);               // 0 inside .. 1 at the edge
    float bend = edge * edge * edge;

    // refraction: near the rim the background is pulled inwards (lens edge)
    vec2 uv = texCoord0 - nuv * bend * 0.045;
    // a touch of chromatic aberration where the glass bends most
    float ca = bend * 0.0055;
    vec3 col;
    col.r = texture(Sampler0, uv - nuv * ca).r;
    col.g = texture(Sampler0, uv).g;
    col.b = texture(Sampler0, uv + nuv * ca).b;

    // glass grade: a bit brighter and more saturated than the world behind it
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, 1.20) * 1.06 + 0.035;

    // lighting: specular rim from the top-left, soft shade on the opposite side
    vec2 light = normalize(vec2(-0.55, -0.85));
    float facing = dot(n, light);
    float rim = smoothstep(0.80, 1.0, edge);
    float spec = pow(max(facing, 0.0), 2.0) * smoothstep(0.45, 1.0, edge);
    float shade = pow(max(-facing, 0.0), 2.0) * smoothstep(0.65, 1.0, edge);
    col += vec3(1.0) * (rim * 0.22 + spec * 0.42);
    col -= vec3(0.10) * shade;
    // very fine bright line exactly at the border
    col += vec3(1.0) * smoothstep(0.93, 1.0, edge) * 0.25;

    fragColor = vec4(clamp(col, 0.0, 1.0), coverage) * ColorModulator;
}
