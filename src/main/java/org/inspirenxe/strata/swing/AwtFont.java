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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.text.AttributedString;

/**
 * A {@link SwingFont} that draws an AWT font. One base font is derived into plain, bold, italic and bold italic.
 *
 * <p>Widths are the advances of a {@link TextLayout} made with fractional metrics and antialiasing on, and drawing
 * uses the same layout, so text measures exactly as wide as it draws. The line height is the font's height (ascent,
 * descent and leading). Sizes are in UI units. A right-to-left run is laid out with a right-to-left run direction,
 * so Java2D orders, shapes and joins it.</p>
 */
public final class AwtFont implements SwingFont {
    private final Font[] fonts;
    private final FontMetrics[] metrics;
    private final FontRenderContext context;

    /**
     * Creates a font.
     *
     * @param base the plain font; bold and italic variants are derived from it
     */
    public AwtFont(final Font base) {
        this.fonts = new Font[4];
        this.metrics = new FontMetrics[4];
        final Graphics2D scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            scratch.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            scratch.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            for (int variant = 0; variant < 4; variant++) {
                // Bit 0 is bold and bit 1 italic, as AWT's style constants are
                this.fonts[variant] = base.deriveFont(variant);
                this.metrics[variant] = scratch.getFontMetrics(this.fonts[variant]);
            }
            this.context = scratch.getFontRenderContext();
        } finally {
            scratch.dispose();
        }
    }

    /**
     * Returns the AWT font for a style.
     *
     * @param style the style; only bold and italic matter
     * @return the font
     */
    public Font font(final TextStyle style) {
        return this.fonts[AwtFont.variant(style)];
    }

    /**
     * Returns the AWT metrics for a style.
     *
     * @param style the style; only bold and italic matter
     * @return the font metrics
     */
    public FontMetrics metrics(final TextStyle style) {
        return this.metrics[AwtFont.variant(style)];
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

    /**
     * Returns the base font's point size, in UI units.
     *
     * @return the size
     */
    @Override
    public float size() {
        return this.fonts[Font.PLAIN].getSize2D();
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

    @Override
    public float ascent(final TextStyle style) {
        return this.metrics(style).getAscent();
    }

    @Override
    public void draw(final Graphics2D graphics, final TextSpan span, final float x, final float baseline,
                     final int argb) {
        graphics.setFont(this.font(span.style()));
        graphics.setColor(new Color(argb, true));
        this.layout(span).draw(graphics, x, baseline);
    }

    /**
     * Draws the run laid out right to left, so Java2D orders it, mirrors its brackets and joins its letters.
     */
    @Override
    public void drawRightToLeft(final Graphics2D graphics, final TextSpan span, final float x, final float baseline,
                                final int argb) {
        final AttributedString text = new AttributedString(span.text());
        text.addAttribute(TextAttribute.FONT, this.font(span.style()));
        text.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        graphics.setFont(this.font(span.style()));
        graphics.setColor(new Color(argb, true));
        new TextLayout(text.getIterator(), this.context).draw(graphics, x, baseline);
    }

    private static int variant(final TextStyle style) {
        return (style.bold() ? Font.BOLD : 0) | (style.italic() ? Font.ITALIC : 0);
    }
}
