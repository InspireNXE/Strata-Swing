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
import java.util.Objects;

/**
 * The Swing {@link TextMetrics}: picks a {@link SwingFont} for each text style and measures with it.
 * {@link SwingCanvas} draws with the same font, so text measures exactly as wide as it draws.
 *
 * <p>It holds one font for normal text and one for {@linkplain TextStyle#monospace() monospaced} styles. Underline,
 * strikethrough and shadow do not change widths.</p>
 */
public final class SwingTextMetrics implements TextMetrics {
    private final SwingFont text;
    private final SwingFont monospace;

    /**
     * Creates metrics for an AWT base font and the logical Monospaced font at the same size. Sizes are in UI units.
     *
     * @param base the plain font; bold and italic variants are derived from it
     */
    public SwingTextMetrics(final Font base) {
        this(base, new Font(Font.MONOSPACED, Font.PLAIN, base.getSize()).deriveFont(base.getSize2D()));
    }

    /**
     * Creates metrics for an AWT base font and an AWT monospaced font. Sizes are in UI units.
     *
     * @param base the plain font; bold and italic variants are derived from it
     * @param monospace the plain monospaced font, used for monospaced styles; its variants are derived the same way
     */
    public SwingTextMetrics(final Font base, final Font monospace) {
        this(new AwtFont(base), new AwtFont(monospace));
    }

    /**
     * Creates metrics for any two fonts, such as a {@link BitmapFont} for normal text.
     *
     * @param text the font for normal text
     * @param monospace the font for monospaced styles
     */
    public SwingTextMetrics(final SwingFont text, final SwingFont monospace) {
        this.text = Objects.requireNonNull(text, "text");
        this.monospace = Objects.requireNonNull(monospace, "monospace");
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
     * Returns the font a style is measured and drawn with.
     *
     * @param style the style; only monospace chooses between the two fonts
     * @return the font
     */
    public SwingFont font(final TextStyle style) {
        return style.monospace() ? this.monospace : this.text;
    }

    @Override
    public float width(final TextSpan span) {
        return span.text().isEmpty() ? 0f : this.font(span.style()).width(span);
    }

    @Override
    public float lineHeight(final TextStyle style) {
        return this.font(style).lineHeight(style);
    }

    /**
     * Returns the ascent of the font the style is drawn with, where the canvas puts the baseline. A font whose ascent
     * is more than its line height answers the line height, and one whose ascent is not positive answers four fifths
     * of it.
     */
    @Override
    public float ascent(final TextStyle style) {
        final SwingFont font = this.font(style);
        final float lineHeight = font.lineHeight(style);
        final float ascent = font.ascent(style);
        return ascent > 0f ? Math.min(ascent, lineHeight) : lineHeight * 0.8f;
    }
}
