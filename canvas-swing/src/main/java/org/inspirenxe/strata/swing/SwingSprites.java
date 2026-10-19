package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.theme.SpriteId;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Loads sprites for the Swing canvas from the class path, at the resource a {@link SpriteResolver} names
 * ({@link SpriteResolver#DEFAULT} unless one is given). Images are loaded once and cached, including the fact that
 * one is missing.
 */
public final class SwingSprites {
    private final ClassLoader loader;
    private final SpriteResolver resolver;
    private final Map<SpriteId, Optional<BufferedImage>> cache;

    /**
     * Creates a loader that uses {@link SpriteResolver#DEFAULT}.
     *
     * @param loader the class loader to find images with
     */
    public SwingSprites(final ClassLoader loader) {
        this(loader, SpriteResolver.DEFAULT);
    }

    /**
     * Creates a loader.
     *
     * @param loader the class loader to find images with
     * @param resolver where each sprite's image lives on the class path
     */
    public SwingSprites(final ClassLoader loader, final SpriteResolver resolver) {
        this.loader = loader;
        this.resolver = resolver;
        this.cache = new HashMap<>();
    }

    /**
     * Returns a sprite's image.
     *
     * @param sprite the sprite
     * @return the image, or empty if there is no such resource or it cannot be read as an image
     */
    public Optional<BufferedImage> get(final SpriteId sprite) {
        return this.cache.computeIfAbsent(sprite, this::load);
    }

    private Optional<BufferedImage> load(final SpriteId sprite) {
        final String path = this.resolver.resourcePath(sprite);
        try (InputStream in = this.loader.getResourceAsStream(path)) {
            return in == null ? Optional.empty() : Optional.ofNullable(ImageIO.read(in));
        } catch (final IOException e) {
            // A broken image is treated like a missing one, so the canvas draws its placeholder instead of failing
            return Optional.empty();
        }
    }
}
