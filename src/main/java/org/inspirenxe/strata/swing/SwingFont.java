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

import java.awt.Graphics2D;

/**
 * A font the Swing adapter measures and draws text with. {@link SwingTextMetrics} holds one for normal text and one
 * for monospaced text, and {@link SwingCanvas} draws with the same font the text was measured with.
 * {@link AwtFont} draws AWT fonts; {@link BitmapFont} draws glyphs cut from images.
 *
 * <p>All values are in UI units. A span's width must be what drawing it advances, so spans drawn side by side meet
 * without gaps or overlaps, and the width of two spans together must be the sum of their widths. Only a span's
 * text and its style's bold, italic and monospace flags change what a font measures; color, underline,
 * strikethrough and shadow are the canvas's work. Implementations are used on the Swing event thread.</p>
 */
public interface SwingFont {
    /**
     * Returns how far drawing a span advances.
     *
     * @param span the span; may be empty
     * @return the width, 0 for an empty span
     */
    float width(TextSpan span);

    /**
     * Returns the height of one line of text.
     *
     * @param style the style
     * @return the line height
     */
    float lineHeight(TextStyle style);

    /**
     * Returns how far the baseline sits below the top of a line.
     *
     * @param style the style
     * @return the ascent
     */
    float ascent(TextStyle style);

    /**
     * Draws a span in one color. Underline, strikethrough and shadow are left to the caller.
     *
     * @param graphics the graphics to draw on; its transform maps UI units to pixels
     * @param span the span; not empty
     * @param x the left edge of the text
     * @param baseline the baseline
     * @param argb the color
     */
    void draw(Graphics2D graphics, TextSpan span, float x, float baseline, int argb);
}
