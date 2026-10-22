/*
 * Copyright (c) InspireNXE <https://github.com/InspireNXE>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.theme.SpriteId;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads sprites for the Swing canvas from a {@link SpriteSource}: by default the class path, at the resource a
 * {@link SpriteResolver} names ({@link SpriteResolver#DEFAULT} unless one is given). Images are loaded once and
 * cached, including the fact that one is missing, until {@link #clear()}.
 *
 * <p>Where one UI unit covers 2 device pixels or more the canvas draws the source's image of density 2 if it has one,
 * from 1.5 its image of density 1.5, and otherwise the plain image, each at the same size in UI units.</p>
 */
public final class SwingSprites {
    private static final float[] DENSITIES = {2f, 1.5f};

    private final SpriteSource source;
    private final Map<SpriteId, Optional<BufferedImage>> cache;
    private final Map<DenseKey, Optional<BufferedImage>> dense;
    private long version;

    /**
     * Creates a loader that reads the class path with {@link SpriteResolver#DEFAULT}.
     *
     * @param loader the class loader to find images with
     */
    public SwingSprites(final ClassLoader loader) {
        this(loader, SpriteResolver.DEFAULT);
    }

    /**
     * Creates a loader that reads the class path.
     *
     * @param loader the class loader to find images with
     * @param resolver where each sprite's image lives on the class path
     */
    public SwingSprites(final ClassLoader loader, final SpriteResolver resolver) {
        this(SpriteSource.classPath(loader, resolver));
    }

    /**
     * Creates a loader that reads from any source.
     *
     * @param source where images come from
     */
    public SwingSprites(final SpriteSource source) {
        this.source = Objects.requireNonNull(source, "source");
        this.cache = new HashMap<>();
        this.dense = new HashMap<>();
    }

    /**
     * Returns a sprite's image.
     *
     * @param sprite the sprite
     * @return the image, or empty if the source has none
     */
    public Optional<BufferedImage> get(final SpriteId sprite) {
        return this.cache.computeIfAbsent(sprite, this.source::load);
    }

    /**
     * Returns the densest image of a sprite that a density can show: the one of density 2 from 2 device pixels per
     * unit, of 1.5 from 1.5, and the plain image otherwise or when the denser one is missing.
     *
     * @param sprite the sprite
     * @param pixelsPerUnit device pixels per UI unit where it is drawn
     * @return the image, or empty if the source has none at all
     */
    public Optional<BufferedImage> get(final SpriteId sprite, final float pixelsPerUnit) {
        return this.image(sprite, pixelsPerUnit).map(SpriteImage::image);
    }

    /**
     * Returns the image {@link #get(SpriteId, float)} picks, with its density.
     */
    Optional<SpriteImage> image(final SpriteId sprite, final float pixelsPerUnit) {
        for (final float density : DENSITIES) {
            // A little below, so a scale of 1.5 stored as 1.4999 still counts
            if (pixelsPerUnit >= density - 0.01f) {
                final Optional<BufferedImage> image = this.dense.computeIfAbsent(new DenseKey(sprite, density),
                        key -> this.source.loadDense(key.sprite(), key.density()));
                if (image.isPresent()) {
                    return Optional.of(new SpriteImage(image.get(), density));
                }
            }
        }
        return this.get(sprite).map(image -> new SpriteImage(image, 1f));
    }

    /**
     * Forgets every loaded image, so each is loaded from the source again when next drawn. Canvases drop their
     * tinted copies too.
     */
    public void clear() {
        this.cache.clear();
        this.dense.clear();
        this.version++;
    }

    /**
     * Returns a number that changes on every {@link #clear()}, so a canvas knows when its tinted copies are stale.
     *
     * @return the version
     */
    public long version() {
        return this.version;
    }

    /**
     * An image and how many of its pixels cover one pixel of the plain image.
     *
     * @param image the image
     * @param density 1 for the plain image
     */
    record SpriteImage(BufferedImage image, float density) {
    }

    private record DenseKey(SpriteId sprite, float density) {
    }
}
