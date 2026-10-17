package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.text.TextMetrics;
import org.inspirenxe.strata.api.text.TextSpan;
import org.inspirenxe.strata.api.text.TextStyle;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Picks AWT fonts for text styles and measures text with them, for the Swing {@link TextMetrics}.
 *
 * <p>One base font is derived into plain, bold, italic and bold italic. Widths are advance widths with fractional
 * metrics and antialiasing on, the same settings {@link StrataPanel} and {@link SwingSnapshot} draw with. Underline,
 * strikethrough and shadow do not change widths. The line height is the font's height (ascent, descent and
 * leading).</p>
 */
public final class SwingTextMetrics implements TextMetrics {
    private final Map<Variant, Font> fonts;
    private final Map<Variant, FontMetrics> metrics;

    /**
     * Creates metrics for a base font. Its size is in UI units.
     *
     * @param base the plain font; bold and italic variants are derived from it
     */
    public SwingTextMetrics(final Font base) {
        this.fonts = new EnumMap<>(Variant.class);
        this.metrics = new EnumMap<>(Variant.class);

        final Graphics2D scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        scratch.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        scratch.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        for (final Variant variant : Variant.values()) {
            final Font font = base.deriveFont(variant.awtStyle);
            this.fonts.put(variant, font);
            this.metrics.put(variant, scratch.getFontMetrics(font));
        }
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
     * @param style the style; only bold and italic matter
     * @return the font
     */
    public Font font(final TextStyle style) {
        return this.fonts.get(Variant.of(style));
    }

    /**
     * Returns the AWT metrics for a style.
     *
     * @param style the style; only bold and italic matter
     * @return the font metrics
     */
    public FontMetrics metrics(final TextStyle style) {
        return this.metrics.get(Variant.of(style));
    }

    @Override
    public float width(final TextSpan span) {
        return (float) this.metrics(span.style()).getStringBounds(span.text(), null).getWidth();
    }

    @Override
    public float lineHeight(final TextStyle style) {
        return this.metrics(style).getHeight();
    }

    private enum Variant {
        PLAIN(Font.PLAIN),
        BOLD(Font.BOLD),
        ITALIC(Font.ITALIC),
        BOLD_ITALIC(Font.BOLD | Font.ITALIC);

        private final int awtStyle;

        Variant(final int awtStyle) {
            this.awtStyle = awtStyle;
        }

        static Variant of(final TextStyle style) {
            if (style.bold()) {
                return style.italic() ? BOLD_ITALIC : BOLD;
            }
            return style.italic() ? ITALIC : PLAIN;
        }
    }
}
