#version 330
#extension GL_ARB_separate_shader_objects : require

// Nautilus Client liquid glass: separable gaussian blur (h), runs on a downscaled copy of the world.
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 texel = vec2(1.0, 0.0) / vec2(textureSize(InSampler, 0));
    // 9-tap gaussian using bilinear filtering (offsets between texels)
    vec4 c = texture(InSampler, texCoord) * 0.2270270270;
    c += texture(InSampler, texCoord + texel * 1.3846153846) * 0.3162162162;
    c += texture(InSampler, texCoord - texel * 1.3846153846) * 0.3162162162;
    c += texture(InSampler, texCoord + texel * 3.2307692308) * 0.0702702703;
    c += texture(InSampler, texCoord - texel * 3.2307692308) * 0.0702702703;
    fragColor = vec4(c.rgb, 1.0);
}
