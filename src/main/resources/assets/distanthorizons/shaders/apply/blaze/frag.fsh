#version 150 core

in vec2 TexCoord;

out vec4 fragColor;

uniform sampler2D uSourceColorTexture;
uniform sampler2D uSourceDepthTexture;

// DH apply frag
void main()
{
    fragColor = vec4(0.0);

    // a fragment depth of "1" means the fragment wasn't drawn to,
    // only update fragments that were drawn to
    float fragmentDepth = texture(uSourceDepthTexture, TexCoord).r;
    if (fragmentDepth != 1)
    {
        // Add a small depth bias to push DH LODs behind vanilla chunks to prevent Z-fighting
        gl_FragDepth = min(fragmentDepth + 0.0001, 0.999999); 
        fragColor = texture(uSourceColorTexture, TexCoord);
    }
    else
    {
        // use the original MC texture if no LODs were drawn to this fragment
        discard;
    }
}