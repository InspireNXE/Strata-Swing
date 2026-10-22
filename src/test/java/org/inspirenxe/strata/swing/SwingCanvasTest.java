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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Swing-specific canvas behavior beyond the SPI contract: sprites, tints and nine-slice drawing, on images made for
 * the test.
 */
class SwingCanvasTest {
    private static final SpriteId WHITE = SpriteId.of("test", "white");
    private static final SpriteId FRAME = SpriteId.of("test", "frame");
    private static final SpriteId DOT = SpriteId.of("test", "dot");
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

        // A red dot, and a blue one drawn for displays of twice the density
        final BufferedImage dot = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        fillImage(dot, 0, 0, 1, 1, CORNER);
        ImageIO.write(dot, "png", dir.resolve("dot.png").toFile());
        final BufferedImage denseDot = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        fillImage(denseDot, 0, 0, 2, 2, CENTER);
        ImageIO.write(denseDot, "png", dir.resolve("dot@2x.png").toFile());

        // A frame drawn at twice the density: two pixels of red corner, green edge and blue center each
        final BufferedImage denseFrame = new BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB);
        fillImage(denseFrame, 0, 0, 6, 6, EDGE);
        fillImage(denseFrame, 0, 0, 2, 2, CORNER);
        fillImage(denseFrame, 4, 0, 2, 2, CORNER);
        fillImage(denseFrame, 0, 4, 2, 2, CORNER);
        fillImage(denseFrame, 4, 4, 2, 2, CORNER);
        fillImage(denseFrame, 2, 2, 2, 2, CENTER);
        ImageIO.write(denseFrame, "png", dir.resolve("frame@2x.png").toFile());

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
    void aDenserDisplayDrawsTheDenserImageAtTheSameSize() {
        final SwingContractTests.ImageSurface plain = surface(8, 8);
        plain.canvas().sprite(DOT, new Rect(0, 0, 4, 4), 0xFFFFFFFF);
        plain.finish();
        assertEquals(CORNER, plain.argb(1, 1), "one pixel per unit draws the plain image");
        assertEquals(0, plain.argb(5, 5));

        final SwingContractTests.ImageSurface dense = surface(8, 8);
        dense.canvas().pushScale(2f);
        dense.canvas().sprite(DOT, new Rect(0, 0, 2, 2), 0xFFFFFFFF);
        dense.canvas().popTransform();
        dense.finish();
        assertEquals(CENTER, dense.argb(1, 1), "two pixels per unit draw the @2x image");
        assertEquals(CENTER, dense.argb(3, 3), "at the same size in units");
        assertEquals(0, dense.argb(5, 5));
    }

    @Test
    void aDenseNineSliceKeepsItsBorderInUnits() {
        final SwingContractTests.ImageSurface surface = surface(20, 20);
        surface.canvas().pushScale(2f);
        surface.canvas().nineSlice(FRAME, new Rect(0, 0, 10, 10), Insets.of(1), 0xFFFFFFFF);
        surface.canvas().popTransform();
        surface.finish();

        assertEquals(CORNER, surface.argb(1, 1), "the corner is one unit, two device pixels");
        assertEquals(EDGE, surface.argb(10, 1), "the top edge");
        assertEquals(CENTER, surface.argb(10, 10), "the center");
        assertEquals(EDGE, surface.argb(1, 10), "the left edge is one unit wide");
        assertEquals(CENTER, surface.argb(2, 10), "the center starts after one unit");
    }

    @Test
    void denserImagesSitBesideThePlainOne() {
        assertEquals("assets/a/sprites/b@2x.png", SpriteSource.densePath("assets/a/sprites/b.png", 2f));
        assertEquals("assets/a/sprites/b@1.5x.png", SpriteSource.densePath("assets/a/sprites/b.png", 1.5f));
        assertEquals("assets/a.b/sprites/c@2x", SpriteSource.densePath("assets/a.b/sprites/c", 2f));
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
        assertEquals(java.awt.Cursor.NW_RESIZE_CURSOR, StrataPanel.cursorFor(CursorKind.RESIZE_DIAGONAL_DOWN).getType());
        assertEquals(java.awt.Cursor.NE_RESIZE_CURSOR, StrataPanel.cursorFor(CursorKind.RESIZE_DIAGONAL_UP).getType());
        assertEquals(java.awt.Cursor.WAIT_CURSOR, StrataPanel.cursorFor(CursorKind.WAIT).getType());
        assertEquals(java.awt.Cursor.DEFAULT_CURSOR, StrataPanel.cursorFor(null).getType());
        for (final CursorKind kind : CursorKind.values()) {
            StrataPanel.cursorFor(kind);
        }
    }

    @Test
    void theCanvasShowsWhatAwtHasAndFallsBackHeadless() {
        final SwingContractTests.ImageSurface surface = surface(4, 4);
        for (final CursorKind kind : java.util.List.of(CursorKind.ARROW, CursorKind.TEXT, CursorKind.HAND,
                CursorKind.CROSSHAIR, CursorKind.RESIZE_HORIZONTAL, CursorKind.RESIZE_VERTICAL, CursorKind.RESIZE_ALL,
                CursorKind.RESIZE_DIAGONAL_DOWN, CursorKind.RESIZE_DIAGONAL_UP, CursorKind.WAIT)) {
            assertTrue(surface.canvas().showsCursor(kind), kind + " is one of AWT's");
        }
        // Tests run headless, where AWT makes no custom cursors, so the drawn ones show their fallback
        assertFalse(surface.canvas().showsCursor(CursorKind.GRAB));
        assertEquals(java.awt.Cursor.HAND_CURSOR, StrataPanel.cursorFor(CursorKind.GRAB).getType());
        assertEquals(java.awt.Cursor.DEFAULT_CURSOR, StrataPanel.cursorFor(CursorKind.NO_DROP).getType());
    }
}
