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

import org.inspirenxe.strata.api.text.FontId;
import org.inspirenxe.strata.api.text.TextMetrics;
import org.inspirenxe.strata.api.text.TextSpan;
import org.inspirenxe.strata.api.text.TextStyle;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Swing {@link TextMetrics}: picks a {@link SwingFont} for each text style and measures with it.
 * {@link SwingCanvas} draws with the same font, so text measures exactly as wide as it draws.
 *
 * <p>It holds one font for normal text, one for {@linkplain TextStyle#monospace() monospaced} styles, and the
 * families the application {@linkplain #register registers} under a {@link FontId}. A style's
 * {@linkplain TextStyle#font() font} picks a registered family, which wins over {@code monospace}; a family that is
 * not registered falls back to the other two. A style's {@linkplain TextStyle#size() size} scales the chosen font
 * from its own {@linkplain SwingFont#size() size}, and a size of 0 is the {@linkplain #defaultSize() default size}, the
 * normal font's. The canvas draws the font under that scale, so AWT's glyphs stay sharp. Underline, strikethrough and
 * shadow do not change widths.</p>
 *
 * <p>Registering, replacing or removing a family changes {@link #version()}, so open screens lay out again. Register
 * on the event thread.</p>
 */
public final class SwingTextMetrics implements TextMetrics {
    private final SwingFont text;
    private final SwingFont monospace;
    private final Map<FontId, SwingFont> families = new HashMap<>();
    private long version;

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
     * Registers a family under an id, replacing any family registered under it before, so styles that name the id
     * draw with it.
     *
     * @param id the id styles name
     * @param font the family
     * @return the family registered under the id before, if any
     */
    public Optional<SwingFont> register(final FontId id, final SwingFont font) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(font, "font");
        final SwingFont previous = this.families.put(id, font);
        if (previous != font) {
            this.version++;
        }
        return Optional.ofNullable(previous);
    }

    /**
     * Registers an AWT font under an id, as an {@link AwtFont} whose bold and italic are derived from it. Its size
     * does not matter: a font read with {@link Font#createFont} from TTF bytes, at 1 point, draws at the size a style
     * asks for, or at the default size for size 0, like any other family.
     *
     * @param id the id styles name
     * @param font the plain font
     * @return the family registered under the id before, if any
     */
    public Optional<SwingFont> register(final FontId id, final Font font) {
        return this.register(id, new AwtFont(font));
    }

    /**
     * Removes the family registered under an id, so styles that name it draw with the normal font again.
     *
     * @param id the id
     * @return the family that was registered, if any
     */
    public Optional<SwingFont> unregister(final FontId id) {
        final SwingFont removed = this.families.remove(Objects.requireNonNull(id, "id"));
        if (removed != null) {
            this.version++;
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Returns the family registered under an id.
     *
     * @param id the id
     * @return the family, or empty if none is registered under it
     */
    public Optional<SwingFont> registered(final FontId id) {
        return Optional.ofNullable(this.families.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Returns the size text of size 0 measures and draws at: the normal font's own size.
     *
     * @return the size in UI units
     */
    public float defaultSize() {
        return this.text.size();
    }

    /**
     * Returns the font a style is measured and drawn with, at the font's own size.
     *
     * @param style the style; its font and monospace choose between the fonts
     * @return the font
     */
    public SwingFont font(final TextStyle style) {
        final FontId id = style.font();
        if (id != null) {
            final SwingFont family = this.families.get(id);
            if (family != null) {
                return family;
            }
        }
        return style.monospace() ? this.monospace : this.text;
    }

    /**
     * Returns how much larger than its font's own size a style draws.
     *
     * @param style the style
     * @param font the font {@link #font} chose for it
     * @return the factor, positive
     */
    float scale(final TextStyle style, final SwingFont font) {
        final float size = style.size() > 0f ? style.size() : this.text.size();
        return size / font.size();
    }

    @Override
    public float width(final TextSpan span) {
        if (span.text().isEmpty()) {
            return 0f;
        }
        final SwingFont font = this.font(span.style());
        return font.width(span) * this.scale(span.style(), font);
    }

    @Override
    public float lineHeight(final TextStyle style) {
        final SwingFont font = this.font(style);
        return font.lineHeight(style) * this.scale(style, font);
    }

    @Override
    public long version() {
        return this.version;
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
        return (ascent > 0f ? Math.min(ascent, lineHeight) : lineHeight * 0.8f) * this.scale(style, font);
    }
}
