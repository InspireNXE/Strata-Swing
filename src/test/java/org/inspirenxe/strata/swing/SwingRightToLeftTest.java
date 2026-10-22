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

import org.inspirenxe.strata.api.text.TextSpan;
import org.inspirenxe.strata.api.text.TextStyle;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Right-to-left runs on the Swing canvas.
 */
class SwingRightToLeftTest {
    private static final TextStyle WHITE = TextStyle.DEFAULT.withColor(0xFFFFFFFF);

    @Test
    void thePlatformShapesRightToLeftText() {
        assertTrue(new SwingPlatform(SwingTextMetrics.defaults()).capabilities().shapesRightToLeft());
    }

    @Test
    void anArabicRunDrawsAsJava2dShapesIt() {
        final SwingTextMetrics metrics = new SwingTextMetrics(new Font(Font.DIALOG, Font.PLAIN, 12));
        final TextSpan span = new TextSpan("\u0645\u0631\u062D\u0628\u0627", WHITE);
        final int width = (int) Math.ceil(metrics.width(span)) + 8;

        final BufferedImage ordered = draw(metrics, width, 20, span, true);
        final BufferedImage plain = draw(metrics, width, 20, span, false);

        // Java2D's own bidirectional layout orders a run of Arabic alike, so both come out the same
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < width; x++) {
                assertEquals(plain.getRGB(x, y), ordered.getRGB(x, y), "pixel " + x + ", " + y);
            }
        }
    }

    @Test
    void aRightToLeftRunStaysWithinItsMeasuredWidth() {
        final SwingTextMetrics metrics = new SwingTextMetrics(new Font(Font.DIALOG, Font.PLAIN, 12));
        final TextSpan span = new TextSpan("\u05E9\u05DC\u05D5\u05DD (1)", WHITE);
        final float measured = metrics.width(span);
        final int width = (int) Math.ceil(measured) + 20;
        final BufferedImage image = draw(metrics, width, 20, span, true);

        int leftmost = width;
        int rightmost = -1;
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    leftmost = Math.min(leftmost, x);
                    rightmost = Math.max(rightmost, x);
                }
            }
        }
        assertTrue(leftmost >= 1, "the run starts at its left edge: " + leftmost);
        assertTrue(rightmost <= 2 + measured + 1.5f, "the run ends within its width: " + rightmost);
    }

    @Test
    void aBitmapFontDrawsItsGlyphsFromTheRight() {
        // A wide glyph and a glyph one pixel wide, each drawn two units per pixel
        final BufferedImage sheet = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                sheet.setRGB(x, y, 0xFFFFFFFF);
            }
            sheet.setRGB(4, y, 0xFFFFFFFF);
        }
        final BitmapFont font = BitmapFont.builder(9f, 7f).sheet(sheet, 8f, 7f, List.of("ab")).build();
        final SwingTextMetrics metrics = new SwingTextMetrics(font, font);
        final TextSpan span = new TextSpan("ab", WHITE);

        final BufferedImage leftToRight = draw(metrics, 16, 10, span, false);
        final BufferedImage rightToLeft = draw(metrics, 16, 10, span, true);

        // The text starts at 2. Left to right the wide glyph covers 2 to 9 and the narrow one 11 and 12; right to
        // left the narrow one covers 2 and 3 and the wide one 5 to 12
        assertNotEquals(0, leftToRight.getRGB(4, 3) >>> 24, "left to right, the wide glyph comes first");
        assertEquals(0, leftToRight.getRGB(10, 3) >>> 24, "with the gap before the narrow one");
        assertEquals(0, rightToLeft.getRGB(4, 3) >>> 24, "right to left, the narrow glyph comes first");
        assertNotEquals(0, rightToLeft.getRGB(10, 3) >>> 24, "and the wide one after it");
    }

    private static BufferedImage draw(final SwingTextMetrics metrics, final int width, final int height,
                                      final TextSpan span, final boolean rightToLeft) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            final SwingCanvas canvas = new SwingCanvas(metrics, new SwingSprites(SwingRightToLeftTest.class.getClassLoader()));
            canvas.begin(g);
            if (rightToLeft) {
                canvas.text(span, 2f, 0f, true);
            } else {
                canvas.text(span, 2f, 0f);
            }
        } finally {
            g.dispose();
        }
        return image;
    }
}
