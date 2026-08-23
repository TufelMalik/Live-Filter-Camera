precision highp float;

varying vec2 textureCoordinate;
uniform sampler2D inputImageTexture;

uniform float intensity; // 0.0 to 1.0

void main() {
    vec4 baseColor = texture2D(inputImageTexture, textureCoordinate);
    
    if (intensity <= 0.001) {
        gl_FragColor = baseColor;
        return;
    }
    
    // Sample surrounding neighborhood for soft highlight diffusion
    float blurRadius = 0.004 * intensity;
    vec3 bloomSum = vec3(0.0);
    float totalWeight = 0.0;
    
    vec2 offsets[8];
    offsets[0] = vec2(-1.0, -1.0);
    offsets[1] = vec2( 0.0, -1.0);
    offsets[2] = vec2( 1.0, -1.0);
    offsets[3] = vec2(-1.0,  0.0);
    offsets[4] = vec2( 1.0,  0.0);
    offsets[5] = vec2(-1.0,  1.0);
    offsets[6] = vec2( 0.0,  1.0);
    offsets[7] = vec2( 1.0,  1.0);
    
    for (int i = 0; i < 8; i++) {
        vec2 sampleCoord = textureCoordinate + offsets[i] * blurRadius;
        vec3 sampleColor = texture2D(inputImageTexture, sampleCoord).rgb;
        
        // Extract highlights (luminance threshold > 0.5)
        float lum = dot(sampleColor, vec3(0.299, 0.587, 0.114));
        float highlight = smoothstep(0.45, 0.95, lum);
        
        bloomSum += sampleColor * highlight;
        totalWeight += 1.0;
    }
    
    vec3 bloomAverage = bloomSum / totalWeight;
    
    // Screen blend mode: 1 - (1 - a) * (1 - b)
    vec3 blended = 1.0 - (1.0 - baseColor.rgb) * (1.0 - bloomAverage * (intensity * 0.85));
    
    gl_FragColor = vec4(clamp(blended, 0.0, 1.0), baseColor.a);
}
