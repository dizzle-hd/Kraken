package dev.nautilus.test.mixin;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Test environment only: headless CI displays (Xvfb + Mesa llvmpipe) offer no sRGB-capable GLX visual,
 * so SDL cannot create the window. Ask for a normal framebuffer instead – the game renders into its own
 * targets anyway, only the final blit to the window is affected.
 */
@Mixin(GlBackend.class)
public abstract class GlBackendMixin {
    private static final int SDL_GL_FRAMEBUFFER_SRGB_CAPABLE = 22;

    @Redirect(method = "createWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SetAttribute(II)Z"), require = 0)
    private boolean nautilusTest$noSrgb(int attr, int value) {
        if (attr == SDL_GL_FRAMEBUFFER_SRGB_CAPABLE) value = 0;
        return SDLVideo.SDL_GL_SetAttribute(attr, value);
    }
}
