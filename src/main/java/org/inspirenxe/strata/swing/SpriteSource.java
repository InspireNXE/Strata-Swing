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
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Loads the image for a sprite id, from wherever the images live: the class path, a folder, a zip file, or images
 * made in code. {@link SwingSprites} asks once per sprite and caches the answer, missing images included, until
 * {@link SwingSprites#clear()}.
 *
 * <p>Implementations are called on the Swing event thread. They should not throw: an image that cannot be read is
 * reported as empty, and the canvas draws its placeholder.</p>
 */
@FunctionalInterface
public interface SpriteSource {
    /**
     * Loads a sprite's image.
     *
     * @param sprite the sprite
     * @return the image, or empty if there is none or it cannot be read
     */
    Optional<BufferedImage> load(SpriteId sprite);

    /**
     * Returns a source that reads images from the class path, at the resource a resolver names.
     *
     * @param loader the class loader to find images with
     * @param resolver where each sprite's image lives on the class path
     * @return the source
     */
    static SpriteSource classPath(final ClassLoader loader, final SpriteResolver resolver) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(resolver, "resolver");
        return sprite -> {
            try (InputStream in = loader.getResourceAsStream(resolver.resourcePath(sprite))) {
                return in == null ? Optional.empty() : Optional.ofNullable(ImageIO.read(in));
            } catch (final IOException e) {
                // A broken image is treated like a missing one, so the canvas draws its placeholder instead of failing
                return Optional.empty();
            }
        };
    }
}
