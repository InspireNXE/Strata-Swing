package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.theme.SpriteId;

/**
 * Turns a sprite id into the class path resource that holds its image. {@link SwingSprites} asks it once per sprite
 * and caches the answer, so it need not be fast.
 *
 * <p>Implementations must return a path without a leading slash, in the form {@link ClassLoader#getResource}
 * takes, and must return the same path every time for the same id. They are called on the Swing event thread.</p>
 */
@FunctionalInterface
public interface SpriteResolver {
    /**
     * The default layout: {@code assets/<namespace>/sprites/<path>.png}.
     */
    SpriteResolver DEFAULT = sprite -> "assets/" + sprite.namespace() + "/sprites/" + sprite.path() + ".png";

    /**
     * Returns the resource path of a sprite's image.
     *
     * @param sprite the sprite
     * @return the resource path, without a leading slash
     */
    String resourcePath(SpriteId sprite);
}
