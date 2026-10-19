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
