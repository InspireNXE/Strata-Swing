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

import org.inspirenxe.strata.api.ComponentFactory;
import org.inspirenxe.strata.api.component.Component;
import org.inspirenxe.strata.api.geometry.Insets;
import org.inspirenxe.strata.api.geometry.Rect;
import org.inspirenxe.strata.api.geometry.Size;
import org.inspirenxe.strata.api.text.TextSpan;
import org.inspirenxe.strata.api.text.TextStyle;
import org.inspirenxe.strata.api.theme.Paint;
import org.inspirenxe.strata.api.layout.Anchor;
import org.inspirenxe.strata.api.screen.Screen;
import org.inspirenxe.strata.api.theme.SpriteId;
import org.inspirenxe.strata.api.theme.ThemeId;
import org.inspirenxe.strata.api.theme.ThemeRef;
import org.inspirenxe.strata.api.theme.Themes;
import org.inspirenxe.strata.core.paint.CanvasPainter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprite sources, sized and tiled paints drawn through the Swing canvas, and bitmap fonts.
 */
class SwingAssetsTest {
    private static final SpriteId STRIPES = SpriteId.of("test", "stripes");
    private static final SpriteId FRAME = SpriteId.of("test", "frame");
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

    private static BufferedImage image(final int width, final int height, final int argb) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    /**
     * A 4 by 1 image: red, then three green columns, so tiling shows where each copy starts.
     */
    private static BufferedImage stripes() {
        final BufferedImage image = image(4, 1, GREEN);
        image.setRGB(0, 0, RED);
        return image;
    }

    /**
     * A 6 by 6 image standing for 3 by 3: red 2-pixel corners and edges, a blue 2 by 2 center.
     */
    private static BufferedImage frame() {
        final BufferedImage image = image(6, 6, RED);
        for (int y = 2; y < 4; y++) {
            for (int x = 2; x < 4; x++) {
                image.setRGB(x, y, BLUE);
            }
        }
        return image;
    }

    private static SwingSprites sprites() {
        final Map<SpriteId, BufferedImage> images = Map.of(STRIPES, stripes(), FRAME, frame());
        return new SwingSprites(sprite -> Optional.ofNullable(images.get(sprite)));
    }

    @Nested
    class Sources {
        @Test
        void aSourceIsAskedOnceUntilCleared() {
            final AtomicInteger loads = new AtomicInteger();
            final SwingSprites sprites = new SwingSprites(sprite -> {
                loads.incrementAndGet();
                return Optional.of(image(1, 1, RED));
            });
            sprites.get(STRIPES);
            sprites.get(STRIPES);
            assertEquals(1, loads.get());
            final long version = sprites.version();
            sprites.clear();
            sprites.get(STRIPES);
            assertEquals(2, loads.get());
            assertTrue(sprites.version() != version);
        }

        @Test
        void aMissingImageIsEmpty() {
            assertTrue(new SwingSprites(sprite -> Optional.empty()).get(STRIPES).isEmpty());
        }
    }

    @Nested
    class Paints {
        @Test
        void aTiledSpriteRepeatsAndCropsTheLastCopy() {
            final SwingContractTests.ImageSurface surface = new SwingContractTests.ImageSurface(10, 1, sprites());
            Paint.tiled(STRIPES, new Size(4, 1)).paint(new CanvasPainter().begin(surface.canvas()), new Rect(0, 0, 10, 1));
            surface.finish();
            for (int x = 0; x < 10; x++) {
                assertEquals(x % 4 == 0 ? RED : GREEN, surface.argb(x, 0), "pixel " + x);
            }
        }

        @Test
        void aSizedNineSliceDrawsTheSizedBorderFromALargerImage() {
            // The 6-pixel image stands for 3 units with a 1-unit border; drawn over 8 by 8 units at one pixel each
            final SwingContractTests.ImageSurface surface = new SwingContractTests.ImageSurface(8, 8, sprites());
            Paint.nineSlice(FRAME, new Size(3, 3), Insets.of(1))
                    .paint(new CanvasPainter().begin(surface.canvas()), new Rect(0, 0, 8, 8));
            surface.finish();
            assertEquals(RED, surface.argb(0, 0));
            assertEquals(RED, surface.argb(0, 4));
            assertEquals(RED, surface.argb(7, 7));
            // The border is one unit, so the center starts at the second pixel
            assertEquals(BLUE, surface.argb(1, 1));
            assertEquals(BLUE, surface.argb(6, 6));
            assertEquals(BLUE, surface.argb(4, 4));
        }

        @Test
        void aTiledNineSliceRepeatsItsCenter() {
            final SwingContractTests.ImageSurface surface = new SwingContractTests.ImageSurface(8, 8, sprites());
            Paint.tiledNineSlice(FRAME, new Size(3, 3), Insets.of(1))
                    .paint(new CanvasPainter().begin(surface.canvas()), new Rect(0, 0, 8, 8));
            surface.finish();
            // The center is one unit and blue all over, so every inner pixel is blue; the frame stays red
            for (int i = 1; i < 7; i++) {
                assertEquals(BLUE, surface.argb(i, i));
                assertEquals(RED, surface.argb(i, 0));
            }
        }
    }

    /**
     * A screen whose only component fills it with a paint.
     */
    private static Screen filled(final Paint paint) {
        return new Screen() {
            @Override
            public Component<?> build(final ComponentFactory ui) {
                return ui.anchorPanel().setBackground(paint).setAnchor(Anchor.FILL);
            }
        };
    }

    @Nested
    @ExtendWith(OnEventThread.class)
    class Hosts {
        @Test
        void aPanelDrawsNewSpritesAfterSetSprites() {
            final StrataPanel panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 1f,
                    new SwingSprites(sprite -> Optional.of(image(1, 1, RED))));
            panel.setBounds(0, 0, 8, 8);
            panel.screens().push(filled(Paint.sprite(STRIPES)));
            assertEquals(RED, this.pixel(panel));
            panel.setSprites(new SwingSprites(sprite -> Optional.of(image(1, 1, BLUE))));
            assertEquals(BLUE, this.pixel(panel));
            assertFalse(panel.screens().isEmpty());
        }

        private int pixel(final StrataPanel panel) {
            final BufferedImage target = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = target.createGraphics();
            try {
                panel.paint(g);
            } finally {
                g.dispose();
            }
            return target.getRGB(4, 4);
        }

        @Test
        void aSnapshotResolvesAThemeRefFromThePlatform(@TempDir final Path root) throws IOException {
            final Path file = root.resolve("themes/test/look.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "{ \"parent\": \"strata:flat\", \"paints\": { \"x.fill\": \"#FF0000\" } }",
                    StandardCharsets.UTF_8);
            try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
                final SwingThemeSource themes = new SwingThemeSource(loader,
                        theme -> "themes/" + theme.namespace() + "/" + theme.path() + ".json");
                final SwingPlatform platform = new SwingPlatform(SwingTextMetrics.defaults(), themes);
                final Screen screen = new Screen() {
                    @Override
                    public Component<?> build(final ComponentFactory ui) {
                        final Component<?> fill = ui.anchorPanel().setAnchor(Anchor.FILL);
                        fill.onEvent(Component.PAINT, event -> event.painter().fill(event.bounds(),
                                ((Paint.Solid) ui.theme().paint("x.fill").orElseThrow()).argb()));
                        return fill;
                    }
                };
                final BufferedImage image = SwingSnapshot.render(platform, screen, ThemeRef.of(ThemeId.of("test", "look")),
                        8, 8, 1f, sprites(), stack -> { });
                assertEquals(RED, image.getRGB(4, 4));
            }
        }
    }

    @Nested
    class Fonts {
        /**
         * A sheet of two 4 by 4 cells: "a" with ink in its first two columns, "b" with ink in all four.
         */
        private BitmapFont font() {
            final BufferedImage sheet = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 4; y++) {
                sheet.setRGB(0, y, 0xFFFFFFFF);
                sheet.setRGB(1, y, 0xFFFFFFFF);
                for (int x = 4; x < 8; x++) {
                    sheet.setRGB(x, y, 0xFFFFFFFF);
                }
            }
            return BitmapFont.builder(9, 7)
                    .sheet(sheet, 8, 7, List.of("ab"))
                    .advance(' ', 4)
                    .build();
        }

        @Test
        void advancesFollowTheInkScaledToTheHeight() {
            final BitmapFont font = this.font();
            // Cells are drawn twice their size: "a" has 2 columns of ink, 4 units, plus 1
            assertEquals(5f, font.width(new TextSpan("a", TextStyle.DEFAULT)));
            assertEquals(9f, font.width(new TextSpan("b", TextStyle.DEFAULT)));
            assertEquals(18f, font.width(new TextSpan("a b", TextStyle.DEFAULT)));
            // Bold adds a unit to each character
            assertEquals(21f, font.width(new TextSpan("a b", TextStyle.DEFAULT.withBold(true))));
            assertEquals(9f, font.lineHeight(TextStyle.DEFAULT));
            assertEquals(7f, font.ascent(TextStyle.DEFAULT));
            assertTrue(font.has('a'));
            assertFalse(font.has('z'));
        }

        @Test
        void missingCharactersUseTheFallback() {
            final SwingFont fallback = new AwtFont(new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 9));
            final BitmapFont font = this.font();
            final float z = fallback.width(new TextSpan("zz", TextStyle.DEFAULT));
            final BitmapFont withFallback = BitmapFont.builder(9, 7)
                    .sheet(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), 8, 7, List.of("a"))
                    .fallback(fallback)
                    .build();
            assertEquals(1f + z, withFallback.width(new TextSpan("azz", TextStyle.DEFAULT)), 1e-4);
            assertTrue(font.width(new TextSpan("z", TextStyle.DEFAULT)) > 0f);
        }

        @Test
        void glyphsDrawInTheTextColorWithTheirTopAboveTheBaseline() {
            final BufferedImage target = new BufferedImage(20, 12, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = target.createGraphics();
            try {
                this.font().draw(g, new TextSpan("a", TextStyle.DEFAULT), 1, 8, GREEN);
            } finally {
                g.dispose();
            }
            // The cell top is 7 above the baseline at 8, so the glyph covers rows 1 to 8, columns 1 to 4
            assertEquals(GREEN, target.getRGB(1, 1));
            assertEquals(GREEN, target.getRGB(4, 8));
            assertEquals(0, target.getRGB(5, 4) >>> 24);
            assertEquals(0, target.getRGB(1, 0) >>> 24);
        }

        @Test
        void badSheetsAreRefused() {
            final BufferedImage sheet = new BufferedImage(6, 4, BufferedImage.TYPE_INT_ARGB);
            assertThrows(IllegalArgumentException.class,
                    () -> BitmapFont.builder(9, 7).sheet(sheet, 8, 7, List.of("abcd")));
            assertThrows(IllegalArgumentException.class,
                    () -> BitmapFont.builder(9, 7).sheet(sheet, 8, 7, List.of("ab", "c")));
        }

        @Test
        void metricsUseTheBitmapFontForNormalText() {
            final SwingTextMetrics metrics = new SwingTextMetrics(this.font(),
                    new AwtFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 9)));
            assertEquals(5f, metrics.width(new TextSpan("a", TextStyle.DEFAULT)));
            assertTrue(metrics.font(TextStyle.DEFAULT.withMonospace(true)) instanceof AwtFont);
        }
    }
}
