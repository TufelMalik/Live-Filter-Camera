precision highp float;

varying vec2 textureCoordinate;
uniform sampler2D inputImageTexture;

uniform float intensity; // 0.0 to 1.0

void main() {
    vec4 color = texture2D(inputImageTexture, textureCoordinate);
    
    if (intensity <= 0.001) {
        gl_FragColor = color;
        return;
    }
    
    // Distance from center (normalized [0, 1])
    vec2 uv = textureCoordinate - vec2(0.5, 0.5);
    float dist = length(uv) * 1.4142; // Scale so corners reach ~1.0
    
    // Vignette falloff
    float vignetteStart = 0.25;
    float vignetteEnd = 0.95;
    float vignette = smoothstep(vignetteEnd, vignetteStart, dist);
    
    // Blend based on intensity
    vec3 darkened = color.rgb * vignette;
    vec3 result = mix(color.rgb, darkened, intensity);
    
    gl_FragColor = vec4(result, color.a);
}
