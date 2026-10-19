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

import org.inspirenxe.strata.api.text.TextMetrics;
import org.inspirenxe.strata.api.text.TextSpan;
import org.inspirenxe.strata.api.text.TextStyle;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Picks AWT fonts for text styles and measures text with them, for the Swing {@link TextMetrics}.
 *
 * <p>One base font is derived into plain, bold, italic and bold italic, and a monospaced font the same way for
 * {@linkplain TextStyle#monospace() monospaced} styles. Widths are the advances of a {@link TextLayout} made with
 * fractional metrics and antialiasing on, and {@link SwingCanvas} draws the same layout, so text measures exactly
 * as wide as it draws and spans placed side by side meet without gaps or overlaps. Underline,
 * strikethrough and shadow do not change widths. The line height is the font's height (ascent, descent and
 * leading).</p>
 */
public final class SwingTextMetrics implements TextMetrics {
    private final Map<Variant, Font> fonts;
    private final Map<Variant, FontMetrics> metrics;
    private final FontRenderContext context;

    /**
     * Creates metrics for a base font and the logical Monospaced font at the same size. Sizes are in UI units.
     *
     * @param base the plain font; bold and italic variants are derived from it
     */
    public SwingTextMetrics(final Font base) {
        this(base, new Font(Font.MONOSPACED, Font.PLAIN, base.getSize()).deriveFont(base.getSize2D()));
    }

    /**
     * Creates metrics for a base font and a monospaced font. Sizes are in UI units.
     *
     * @param base the plain font; bold and italic variants are derived from it
     * @param monospace the plain monospaced font, used for monospaced styles; its variants are derived the same way
     */
    public SwingTextMetrics(final Font base, final Font monospace) {
        this.fonts = new EnumMap<>(Variant.class);
        this.metrics = new EnumMap<>(Variant.class);

        final Graphics2D scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        scratch.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        scratch.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        for (final Variant variant : Variant.values()) {
            final Font font = (variant.monospace ? monospace : base).deriveFont(variant.awtStyle);
            this.fonts.put(variant, font);
            this.metrics.put(variant, scratch.getFontMetrics(font));
        }
        this.context = scratch.getFontRenderContext();
        scratch.dispose();
    }

    /**
     * Returns metrics for the logical Dialog font at 9 units.
     *
     * @return the default metrics
     */
    public static SwingTextMetrics defaults() {
        return new SwingTextMetrics(new Font(Font.DIALOG, Font.PLAIN, 9));
    }

    /**
     * Returns the AWT font for a style.
     *
     * @param style the style; only bold, italic and monospace matter
     * @return the font
     */
    public Font font(final TextStyle style) {
        return this.fonts.get(Variant.of(style));
    }

    /**
     * Returns the AWT metrics for a style.
     *
     * @param style the style; only bold, italic and monospace matter
     * @return the font metrics
     */
    public FontMetrics metrics(final TextStyle style) {
        return this.metrics.get(Variant.of(style));
    }

    /**
     * Lays out a span for drawing: the glyphs and their positions in UI units, at the settings widths are measured
     * with.
     *
     * @param span the span; must not be empty
     * @return the layout
     */
    public TextLayout layout(final TextSpan span) {
        return new TextLayout(span.text(), this.font(span.style()), this.context);
    }

    @Override
    public float width(final TextSpan span) {
        // FontMetrics rounds each character's advance; a layout keeps the fractional advances text is drawn with
        return span.text().isEmpty() ? 0f : this.layout(span).getAdvance();
    }

    @Override
    public float lineHeight(final TextStyle style) {
        return this.metrics(style).getHeight();
    }

    private enum Variant {
        PLAIN(Font.PLAIN, false),
        BOLD(Font.BOLD, false),
        ITALIC(Font.ITALIC, false),
        BOLD_ITALIC(Font.BOLD | Font.ITALIC, false),
        MONO_PLAIN(Font.PLAIN, true),
        MONO_BOLD(Font.BOLD, true),
        MONO_ITALIC(Font.ITALIC, true),
        MONO_BOLD_ITALIC(Font.BOLD | Font.ITALIC, true);

        private final int awtStyle;
        private final boolean monospace;

        Variant(final int awtStyle, final boolean monospace) {
            this.awtStyle = awtStyle;
            this.monospace = monospace;
        }

        static Variant of(final TextStyle style) {
            final int index = (style.bold() ? 1 : 0) + (style.italic() ? 2 : 0) + (style.monospace() ? 4 : 0);
            return VALUES[index];
        }

        private static final Variant[] VALUES = values();
    }
}
