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

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link SwingFont} that draws glyphs cut from images, for previewing engines whose fonts are bitmaps.
 *
 * <p>A sheet is an image split into a grid, one character per cell, named row by row. Each glyph is drawn with its
 * cell scaled to the sheet's glyph height, without smoothing. A glyph advances by its rightmost column that has
 * any opaque pixel, plus one, scaled the same way and rounded to the nearest unit, plus one unit of spacing. A cell
 * with no opaque pixel advances one unit. Characters can also be given a fixed advance with nothing drawn, as
 * spaces usually are.</p>
 *
 * <p>Bold draws each glyph again one unit to the right and adds one unit to every advance. Italic slants glyphs by
 * a quarter unit per unit above the baseline. Characters with no glyph and no fixed advance are measured and drawn
 * with the fallback font. When several sheets or advances name the same character, the first one added wins.</p>
 *
 * <p>A right-to-left run draws its glyphs from the right with paired brackets mirrored, and hands the characters
 * it has no glyph for to the fallback's own right-to-left drawing. Glyphs are never joined.</p>
 *
 * <p>Tinted copies of the sheets are cached for the most recent {@value #TINT_CACHE} colors. Use it on the Swing
 * event thread.</p>
 */
public final class BitmapFont implements SwingFont {
    /** How many tinted copies of each sheet are kept, most recently used first. */
    public static final int TINT_CACHE = 256;

    private static final float ITALIC_SLANT = 0.25f;

    private final float lineHeight;
    private final float ascent;
    private final List<BufferedImage> sheets;
    private final Map<Integer, Glyph> glyphs;
    private final SwingFont fallback;
    private final Map<Long, BufferedImage> tinted;

    private BitmapFont(final Builder builder) {
        this.lineHeight = builder.lineHeight;
        this.ascent = builder.ascent;
        this.sheets = List.copyOf(builder.sheets);
        this.glyphs = Map.copyOf(builder.glyphs);
        this.fallback = builder.fallback != null
                ? builder.fallback
                : new AwtFont(new Font(Font.DIALOG, Font.PLAIN, 1).deriveFont(builder.lineHeight));
        this.tinted = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(final Map.Entry<Long, BufferedImage> eldest) {
                return this.size() > TINT_CACHE * Math.max(1, BitmapFont.this.sheets.size());
            }
        };
    }

    /**
     * Starts a font.
     *
     * @param lineHeight the height of one line, in UI units
     * @param ascent how far the baseline sits below the top of a line, in UI units
     * @return the builder
     * @throws IllegalArgumentException if the line height is not positive
     */
    public static Builder builder(final float lineHeight, final float ascent) {
        return new Builder(lineHeight, ascent);
    }

    /**
     * Returns whether the font has a glyph or a fixed advance for a character, so the fallback is not used for it.
     *
     * @param codePoint the character
     * @return true if this font draws it
     */
    public boolean has(final int codePoint) {
        return this.glyphs.containsKey(codePoint);
    }

    @Override
    public float width(final TextSpan span) {
        final String text = span.text();
        float width = 0f;
        int index = 0;
        while (index < text.length()) {
            final int codePoint = text.codePointAt(index);
            final Glyph glyph = this.glyphs.get(codePoint);
            if (glyph != null) {
                width += glyph.advance() + (span.style().bold() ? 1f : 0f);
                index += Character.charCount(codePoint);
            } else {
                final int end = this.runEnd(text, index);
                width += this.fallback.width(new TextSpan(text.substring(index, end), span.style()));
                index = end;
            }
        }
        return width;
    }

    @Override
    public float lineHeight(final TextStyle style) {
        return this.lineHeight;
    }

    @Override
    public float ascent(final TextStyle style) {
        return this.ascent;
    }

    @Override
    public void draw(final Graphics2D graphics, final TextSpan span, final float x, final float baseline,
                     final int argb) {
        final TextStyle style = span.style();
        final String text = span.text();
        final Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        final AffineTransform base = graphics.getTransform();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        try {
            float pen = x;
            int index = 0;
            while (index < text.length()) {
                final int codePoint = text.codePointAt(index);
                final Glyph glyph = this.glyphs.get(codePoint);
                if (glyph != null) {
                    if (glyph.sheet() >= 0) {
                        final BufferedImage sheet = this.sheet(glyph.sheet(), argb);
                        BitmapFont.drawGlyph(graphics, base, sheet, glyph, pen, baseline, style.italic());
                        if (style.bold()) {
                            BitmapFont.drawGlyph(graphics, base, sheet, glyph, pen + 1f, baseline, style.italic());
                        }
                    }
                    pen += glyph.advance() + (style.bold() ? 1f : 0f);
                    index += Character.charCount(codePoint);
                } else {
                    final int end = this.runEnd(text, index);
                    final TextSpan run = new TextSpan(text.substring(index, end), style);
                    this.fallback.draw(graphics, run, pen, baseline, argb);
                    pen += this.fallback.width(run);
                    index = end;
                }
            }
        } finally {
            graphics.setTransform(base);
            if (interpolation != null) {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            }
        }
    }

    /**
     * Draws the run from the right: each stretch of glyphs with its characters reversed and its brackets mirrored,
     * and each stretch the fallback draws through the fallback's own right-to-left drawing.
     */
    @Override
    public void drawRightToLeft(final Graphics2D graphics, final TextSpan span, final float x, final float baseline,
                                final int argb) {
        final String text = span.text();
        float pen = x + this.width(span);
        int index = 0;
        while (index < text.length()) {
            final int codePoint = text.codePointAt(index);
            final int end = this.glyphs.containsKey(codePoint) ? this.glyphEnd(text, index) : this.runEnd(text, index);
            final TextSpan run = new TextSpan(text.substring(index, end), span.style());
            pen -= this.width(run);
            if (this.glyphs.containsKey(codePoint)) {
                this.draw(graphics, new TextSpan(BitmapFont.mirrored(run.text()), span.style()), pen, baseline, argb);
            } else {
                this.fallback.drawRightToLeft(graphics, run, pen, baseline, argb);
            }
            index = end;
        }
    }

    /**
     * Returns where a run of characters this font draws ends.
     */
    private int glyphEnd(final String text, final int start) {
        int index = start;
        while (index < text.length()) {
            final int codePoint = text.codePointAt(index);
            if (!this.glyphs.containsKey(codePoint)) {
                break;
            }
            index += Character.charCount(codePoint);
        }
        return index;
    }

    /**
     * Returns text with its characters in reverse order and paired brackets swapped, as a right-to-left run shows
     * them.
     */
    private static String mirrored(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        int index = text.length();
        while (index > 0) {
            final int codePoint = text.codePointBefore(index);
            index -= Character.charCount(codePoint);
            out.appendCodePoint(switch (codePoint) {
                case '(' -> ')';
                case ')' -> '(';
                case '[' -> ']';
                case ']' -> '[';
                case '{' -> '}';
                case '}' -> '{';
                case '<' -> '>';
                case '>' -> '<';
                case '\u00AB' -> '\u00BB';
                case '\u00BB' -> '\u00AB';
                case '\u2039' -> '\u203A';
                case '\u203A' -> '\u2039';
                default -> codePoint;
            });
        }
        return out.toString();
    }

    /**
     * Draws a glyph's cell straight from its whole sheet. A sub-image of the sheet would not be kept on the
     * graphics card, so every glyph would be copied there again on every frame.
     */
    private static void drawGlyph(final Graphics2D graphics, final AffineTransform base, final BufferedImage sheet,
                                  final Glyph glyph, final float x, final float baseline, final boolean italic) {
        graphics.setTransform(base);
        graphics.translate(x, baseline);
        if (italic) {
            graphics.shear(-ITALIC_SLANT, 0d);
        }
        graphics.translate(0d, -glyph.top());
        graphics.scale(glyph.scale(), glyph.scale());
        graphics.drawImage(sheet, 0, 0, glyph.width(), glyph.height(),
                glyph.x(), glyph.y(), glyph.x() + glyph.width(), glyph.y() + glyph.height(), null);
    }

    /**
     * Returns where a run of characters this font cannot draw ends.
     */
    private int runEnd(final String text, final int start) {
        int index = start;
        while (index < text.length()) {
            final int codePoint = text.codePointAt(index);
            if (this.glyphs.containsKey(codePoint)) {
                break;
            }
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private BufferedImage sheet(final int sheet, final int argb) {
        if (argb == 0xFFFFFFFF) {
            return this.sheets.get(sheet);
        }
        final long key = ((long) sheet << 32) | (argb & 0xFFFFFFFFL);
        return this.tinted.computeIfAbsent(key, ignored -> BitmapFont.tint(this.sheets.get(sheet), argb));
    }

    private static BufferedImage tint(final BufferedImage source, final int argb) {
        final int ta = argb >>> 24;
        final int tr = (argb >> 16) & 0xFF;
        final int tg = (argb >> 8) & 0xFF;
        final int tb = argb & 0xFF;
        final int width = source.getWidth();
        final int height = source.getHeight();
        final int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];
            final int a = (pixel >>> 24) * ta / 255;
            final int r = ((pixel >> 16) & 0xFF) * tr / 255;
            final int g = ((pixel >> 8) & 0xFF) * tg / 255;
            final int b = (pixel & 0xFF) * tb / 255;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

    /**
     * One character: a cell of a sheet, or a fixed advance with nothing to draw when {@code sheet} is negative.
     *
     * @param top how far the cell's top sits above the baseline, in UI units
     */
    private record Glyph(int sheet, int x, int y, int width, int height, float scale, float top, float advance) {
    }

    /**
     * Collects a font's sheets and advances.
     */
    public static final class Builder {
        private final float lineHeight;
        private final float ascent;
        private final List<BufferedImage> sheets;
        private final Map<Integer, Glyph> glyphs;
        private SwingFont fallback;

        private Builder(final float lineHeight, final float ascent) {
            if (!(lineHeight > 0f)) {
                throw new IllegalArgumentException("Line height must be positive: " + lineHeight);
            }
            this.lineHeight = lineHeight;
            this.ascent = ascent;
            this.sheets = new ArrayList<>();
            this.glyphs = new HashMap<>();
        }

        /**
         * Adds a sheet of glyphs. The image is split into as many rows as {@code rows} has strings and as many
         * columns as the first string has characters; every string must have the same number. The character
         * {@code U+0000} marks a cell with no glyph.
         *
         * @param image the sheet; a white glyph on a transparent ground draws in the text's color
         * @param height the height in UI units that each cell is drawn at
         * @param ascent how far each cell's top sits above the baseline, in UI units
         * @param rows the characters in each row of cells, top to bottom
         * @return this builder
         * @throws IllegalArgumentException if there are no rows, the rows differ in length, or the image does not
         *     divide into whole cells
         */
        public Builder sheet(final BufferedImage image, final float height, final float ascent,
                             final List<String> rows) {
            Objects.requireNonNull(image, "image");
            if (rows.isEmpty() || !(height > 0f)) {
                throw new IllegalArgumentException("A sheet needs rows and a positive height");
            }
            final int columns = rows.getFirst().codePointCount(0, rows.getFirst().length());
            for (final String row : rows) {
                if (row.codePointCount(0, row.length()) != columns) {
                    throw new IllegalArgumentException("Every row of a sheet needs " + columns + " characters: " + row);
                }
            }
            if (columns == 0 || image.getWidth() % columns != 0 || image.getHeight() % rows.size() != 0) {
                throw new IllegalArgumentException("A " + image.getWidth() + " by " + image.getHeight()
                        + " image does not divide into " + columns + " by " + rows.size() + " cells");
            }
            final int sheet = this.sheets.size();
            this.sheets.add(image);
            final int cellWidth = image.getWidth() / columns;
            final int cellHeight = image.getHeight() / rows.size();
            final float scale = height / cellHeight;
            for (int row = 0; row < rows.size(); row++) {
                final int[] characters = rows.get(row).codePoints().toArray();
                for (int column = 0; column < columns; column++) {
                    if (characters[column] == 0 || this.glyphs.containsKey(characters[column])) {
                        continue;
                    }
                    final int x = column * cellWidth;
                    final int y = row * cellHeight;
                    final int width = Builder.inkWidth(image, x, y, cellWidth, cellHeight);
                    final float advance = (int) (0.5f + width * scale) + 1f;
                    this.glyphs.put(characters[column],
                            new Glyph(sheet, x, y, cellWidth, cellHeight, scale, ascent, advance));
                }
            }
            return this;
        }

        /**
         * Gives a character a fixed advance with nothing drawn, unless it already has a glyph or advance.
         *
         * @param codePoint the character
         * @param advance how far it advances, in UI units, before bold adds its unit
         * @return this builder
         */
        public Builder advance(final int codePoint, final float advance) {
            this.glyphs.putIfAbsent(codePoint, new Glyph(-1, 0, 0, 0, 0, 1f, 0f, advance));
            return this;
        }

        /**
         * Sets the font for characters with no glyph. By default it is the logical Dialog font at the line height.
         *
         * @param fallback the font
         * @return this builder
         */
        public Builder fallback(final SwingFont fallback) {
            this.fallback = Objects.requireNonNull(fallback, "fallback");
            return this;
        }

        /**
         * Builds the font.
         *
         * @return the font
         */
        public BitmapFont build() {
            return new BitmapFont(this);
        }

        /**
         * Returns one more than the rightmost column of a cell that has any opaque pixel, or 0 for an empty cell.
         */
        private static int inkWidth(final BufferedImage image, final int x, final int y, final int width,
                                    final int height) {
            for (int column = width - 1; column >= 0; column--) {
                for (int row = 0; row < height; row++) {
                    if ((image.getRGB(x + column, y + row) >>> 24) != 0) {
                        return column + 1;
                    }
                }
            }
            return 0;
        }
    }
}
