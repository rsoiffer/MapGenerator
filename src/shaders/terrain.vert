#version 330

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoords;

out vec2 TexCoords;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

uniform sampler2D tex;
uniform sampler2D height;

void main() {
    vec3 pos = aPos + vec3(0, 0, texture(height, aTexCoords).r);
    gl_Position = projection * view * model * vec4(pos, 1.0);
    TexCoords = aTexCoords;
}
