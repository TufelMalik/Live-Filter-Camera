precision mediump float;

varying vec2 textureCoordinate;
uniform sampler2D inputImageTexture;
uniform float intensity;

void main() {
    vec4 base = texture2D(inputImageTexture, textureCoordinate);
    
    // Sample surrounding texels for high-performance soft diffusion
    vec2 offset = vec2(0.003, 0.003) * (intensity * 2.0);
    vec4 blur = texture2D(inputImageTexture, textureCoordinate + vec2(-offset.x, -offset.y)) * 0.25;
    blur += texture2D(inputImageTexture, textureCoordinate + vec2(offset.x, -offset.y)) * 0.25;
    blur += texture2D(inputImageTexture, textureCoordinate + vec2(-offset.x, offset.y)) * 0.25;
    blur += texture2D(inputImageTexture, textureCoordinate + vec2(offset.x, offset.y)) * 0.25;
    
    // Screen / Soft Light Glow Blend
    vec3 glow = 1.0 - (1.0 - base.rgb) * (1.0 - blur.rgb);
    vec3 result = mix(base.rgb, glow, intensity * 0.7);
    
    gl_FragColor = vec4(result, base.a);
}
