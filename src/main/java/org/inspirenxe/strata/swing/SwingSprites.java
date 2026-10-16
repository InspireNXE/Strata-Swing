package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.theme.SpriteId;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Loads sprites from the classpath at {@code assets/<namespace>/textures/gui/sprites/<path>.png}, the same layout
 * Minecraft's gui atlas reads, so one set of theme images serves both adapters.
 */
public final class SwingSprites {
    private final ClassLoader loader;
    private final Map<SpriteId, Optional<BufferedImage>> cache;

    public SwingSprites(final ClassLoader loader) {
        this.loader = loader;
        this.cache = new HashMap<>();
    }

    public Optional<BufferedImage> get(final SpriteId sprite) {
        return this.cache.computeIfAbsent(sprite, this::load);
    }

    private Optional<BufferedImage> load(final SpriteId sprite) {
        final String path = "assets/" + sprite.namespace() + "/textures/gui/sprites/" + sprite.path() + ".png";
        try (InputStream in = this.loader.getResourceAsStream(path)) {
            return in == null ? Optional.empty() : Optional.ofNullable(ImageIO.read(in));
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read sprite " + sprite + " from " + path, e);
        }
    }
}
