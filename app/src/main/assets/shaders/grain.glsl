precision highp float;

varying vec2 textureCoordinate;
uniform sampler2D inputImageTexture;

uniform float intensity; // 0.0 to 1.0
uniform float time;      // Animated time seed

// High quality pseudo-random function
float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 color = texture2D(inputImageTexture, textureCoordinate);
    
    if (intensity <= 0.001) {
        gl_FragColor = color;
        return;
    }
    
    // Generate animated grain noise
    vec2 seed = textureCoordinate + vec2(sin(time * 37.0), cos(time * 53.0));
    float noise = (rand(seed) - 0.5) * 2.0;
    
    // Natural film grain response: more visible in midtones, less in crushed blacks or clipped whites
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    float response = 1.0 - 2.0 * abs(luminance - 0.5); // Peaks at midtones
    response = clamp(response * 1.5, 0.2, 1.0);
    
    vec3 grainColor = color.rgb + noise * (intensity * 0.35) * response;
    
    gl_FragColor = vec4(clamp(grainColor, 0.0, 1.0), color.a);
}
