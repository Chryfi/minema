#version 120

uniform sampler2D tex;
uniform float near;
uniform float far;
uniform float preCalcNear;

varying vec4 texcoord;

void main()
{
    gl_FragColor = vec4(preCalcNear * far / (far + near - (2 * texture2D(tex, texcoord.st).z - 1) * (far - near)));
}