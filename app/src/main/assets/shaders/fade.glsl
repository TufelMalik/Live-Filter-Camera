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
    
    // Lift shadows/blacks and slightly compress highlights for faded film look
    float lift = 0.22 * intensity;
    float maxLevel = 1.0 - (0.05 * intensity);
    
    vec3 faded = color.rgb * (maxLevel - lift) + lift;
    
    // Soft tone curve
    faded = mix(color.rgb, faded, intensity);
    
    gl_FragColor = vec4(clamp(faded, 0.0, 1.0), color.a);
}
