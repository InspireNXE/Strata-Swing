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

import org.inspirenxe.strata.api.component.CursorKind;
import org.inspirenxe.strata.api.geometry.Insets;
import org.inspirenxe.strata.api.geometry.Rect;
import org.inspirenxe.strata.api.theme.SpriteId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLClassLoader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Swing-specific canvas behavior beyond the SPI contract: sprites, tints and nine-slice drawing, on images made for
 * the test.
 */
class SwingCanvasTest {
    private static final SpriteId WHITE = SpriteId.of("test", "white");
    private static final SpriteId FRAME = SpriteId.of("test", "frame");
    private static final int CORNER = 0xFFFF0000;
    private static final int EDGE = 0xFF00FF00;
    private static final int CENTER = 0xFF0000FF;

    @TempDir
    static Path assets;
    private static SwingSprites sprites;

    @BeforeAll
    static void writeSprites() throws IOException {
        final Path dir = assets.resolve("assets/test/sprites");
        Files.createDirectories(dir);

        final BufferedImage white = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        fillImage(white, 0, 0, 4, 4, 0xFFFFFFFF);
        ImageIO.write(white, "png", dir.resolve("white.png").toFile());

        // A 3x3 frame: red corners, green edges, a blue center
        final BufferedImage frame = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        fillImage(frame, 0, 0, 3, 3, EDGE);
        frame.setRGB(0, 0, CORNER);
        frame.setRGB(2, 0, CORNER);
        frame.setRGB(0, 2, CORNER);
        frame.setRGB(2, 2, CORNER);
        frame.setRGB(1, 1, CENTER);
        ImageIO.write(frame, "png", dir.resolve("frame.png").toFile());

        sprites = new SwingSprites(new URLClassLoader(new URL[] {assets.toUri().toURL()}, null));
    }

    private static void fillImage(final BufferedImage image, final int x, final int y, final int w, final int h, final int argb) {
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                image.setRGB(px, py, argb);
            }
        }
    }

    private static SwingContractTests.ImageSurface surface(final int width, final int height) {
        return new SwingContractTests.ImageSurface(width, height, sprites);
    }

    @Test
    void spriteStretchesOverItsRectangle() {
        final SwingContractTests.ImageSurface surface = surface(20, 20);
        surface.canvas().sprite(WHITE, new Rect(2, 2, 10, 10), 0xFFFFFFFF);
        surface.finish();

        assertEquals(0xFFFFFFFF, surface.argb(2, 2));
        assertEquals(0xFFFFFFFF, surface.argb(11, 11));
        assertEquals(0, surface.argb(12, 12));
    }

    @Test
    void tintMultipliesTheSprite() {
        final SwingContractTests.ImageSurface surface = surface(10, 10);
        surface.canvas().sprite(WHITE, new Rect(0, 0, 10, 10), 0xFFFF8000);
        surface.finish();

        assertEquals(0xFFFF8000, surface.argb(5, 5));
    }

    @Test
    void nineSliceKeepsCornersAndStretchesTheCenter() {
        final SwingContractTests.ImageSurface surface = surface(30, 30);
        surface.canvas().nineSlice(FRAME, new Rect(0, 0, 30, 30), Insets.of(1), 0xFFFFFFFF);
        surface.finish();

        assertEquals(CORNER, surface.argb(0, 0), "a corner stays one pixel");
        assertEquals(CORNER, surface.argb(29, 29), "a corner stays one pixel");
        assertEquals(EDGE, surface.argb(15, 0), "the top edge stretches along its length");
        assertEquals(EDGE, surface.argb(0, 15), "the left edge stretches along its length");
        assertEquals(CENTER, surface.argb(15, 15), "the center stretches both ways");
        assertEquals(CENTER, surface.argb(1, 1), "corners do not grow");
    }

    @Test
    void opacityFadesWhatIsDrawn() {
        final SwingContractTests.ImageSurface surface = surface(10, 10);
        surface.canvas().fill(new Rect(0, 0, 10, 10), 0xFFFFFFFF);
        surface.canvas().pushOpacity(0.5f);
        surface.canvas().fill(new Rect(0, 0, 5, 10), 0xFFFF0000);
        surface.canvas().pushOpacity(0.5f);
        surface.canvas().fill(new Rect(5, 0, 5, 10), 0xFFFF0000);
        surface.canvas().popOpacity();
        surface.canvas().popOpacity();
        surface.canvas().fill(new Rect(0, 9, 10, 1), 0xFF0000FF);
        surface.finish();

        final int half = surface.argb(2, 2);
        assertEquals(0xFF, (half >> 16) & 0xFF, "red stays full over white");
        assertEquals(0x80, half & 0xFF, 2, "a half fade lets half the white through");
        final int quarter = surface.argb(7, 2);
        assertEquals(0xC0, quarter & 0xFF, 2, "nested fades multiply");
        assertEquals(0xFF0000FF, surface.argb(5, 9), "drawing after the pops is opaque again");
    }

    @Test
    void aShapeIsDrawnFromOnePathEachTime() {
        final org.inspirenxe.strata.api.extension.Shape square = org.inspirenxe.strata.api.extension.Shape.builder()
                .moveTo(2, 2).lineTo(8, 2).lineTo(8, 8).lineTo(2, 8).close().build();
        final SwingContractTests.ImageSurface surface = surface(10, 10);
        surface.canvas().fillShape(square, 0xFFFF0000);
        surface.canvas().fillShape(square, 0xFF0000FF);
        surface.finish();

        assertEquals(0xFF0000FF, surface.argb(5, 5));
        assertEquals(0, surface.argb(0, 0));
    }

    @Test
    void aTintedPixelBufferIsCopiedAgainWhenItChanges() {
        final org.inspirenxe.strata.api.visual.PixelBuffer buffer =
                org.inspirenxe.strata.api.visual.PixelBuffer.of(1, 1).fill(0xFFFFFFFF).changed();
        final SwingContractTests.ImageSurface surface = surface(4, 4);
        surface.canvas().pixels(buffer, new Rect(0, 0, 4, 4), 0xFFFF0000);
        buffer.fill(0xFF000000).changed();
        surface.canvas().pixels(buffer, new Rect(0, 0, 2, 4), 0xFFFF0000);
        surface.finish();

        assertEquals(0xFF000000, surface.argb(1, 1), "the new pixels, tinted");
        assertEquals(0xFFFF0000, surface.argb(3, 1), "the old pixels, tinted, where nothing drew over them");
    }

    @Test
    void requestedCursorIsKeptForTheFrame() {
        final SwingContractTests.ImageSurface surface = surface(4, 4);
        assertNull(surface.swingCanvas().requestedCursor());

        surface.canvas().requestCursor(CursorKind.TEXT);
        assertEquals(CursorKind.TEXT, surface.swingCanvas().requestedCursor());
    }

    @Test
    void cursorsMapToAwtCursors() {
        assertEquals(java.awt.Cursor.TEXT_CURSOR, StrataPanel.cursorFor(CursorKind.TEXT).getType());
        assertEquals(java.awt.Cursor.HAND_CURSOR, StrataPanel.cursorFor(CursorKind.HAND).getType());
        assertEquals(java.awt.Cursor.DEFAULT_CURSOR, StrataPanel.cursorFor(null).getType());
        for (final CursorKind kind : CursorKind.values()) {
            StrataPanel.cursorFor(kind);
        }
    }
}
