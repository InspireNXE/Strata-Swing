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

import org.inspirenxe.strata.api.geometry.Size;
import org.inspirenxe.strata.api.visual.Visual;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Makes {@link Visual}s that {@link SwingCanvas} draws, so Swing code can show its own images through the API.
 */
public final class SwingVisuals {

    private SwingVisuals() {
    }

    /**
     * Returns a visual of an image whose natural size is the image's size in pixels, one UI unit per pixel.
     *
     * @param image the image; it is not copied, so do not change it while it is shown
     * @return the visual
     */
    public static Visual image(final BufferedImage image) {
        return new ImageVisual(image, new Size(image.getWidth(), image.getHeight()));
    }

    /**
     * Returns a visual of an image with a natural size of its own, for images drawn smaller or larger than their
     * pixels.
     *
     * @param image the image; it is not copied, so do not change it while it is shown
     * @param width the natural width in UI units
     * @param height the natural height in UI units
     * @return the visual
     */
    public static Visual image(final BufferedImage image, final float width, final float height) {
        return new ImageVisual(image, new Size(width, height));
    }

    /**
     * An image for the Swing canvas. Two are equal when they hold the same image object and size.
     *
     * @param image the image
     * @param size the natural size
     */
    record ImageVisual(BufferedImage image, Size size) implements Visual {
        ImageVisual {
            Objects.requireNonNull(image, "image");
            Objects.requireNonNull(size, "size");
        }
    }
}
