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
 */
public final class SwingSprites {
    private final SpriteSource source;
    private final Map<SpriteId, Optional<BufferedImage>> cache;
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
     * Forgets every loaded image, so each is loaded from the source again when next drawn. Canvases drop their
     * tinted copies too.
     */
    public void clear() {
        this.cache.clear();
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
}
