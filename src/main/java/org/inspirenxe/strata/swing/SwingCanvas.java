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

import org.inspirenxe.strata.api.component.CursorKind;
import org.inspirenxe.strata.api.geometry.Insets;
import org.inspirenxe.strata.api.geometry.Rect;
import org.inspirenxe.strata.api.text.TextSpan;
import org.inspirenxe.strata.api.text.TextStyle;
import org.inspirenxe.strata.api.theme.SpriteId;
import org.inspirenxe.strata.api.visual.Visual;
import org.inspirenxe.strata.spi.Canvas;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Swing {@link Canvas}: draws straight onto a {@link Graphics2D}, in call order.
 *
 * <p>How it meets the canvas contract on Java2D:</p>
 * <ul>
 *     <li>Coordinates stay fractional for text. Fills, clips, sprites and visuals snap each edge to the nearest
 *     device pixel, so pieces that share an edge meet without gaps or overlaps at any scale.</li>
 *     <li>A sprite tint other than opaque white multiplies each pixel's channels; tinted copies are cached per
 *     sprite and tint.</li>
 *     <li>A nine-slice shrinks its borders to fit a rectangle smaller than them. Its border is in the image's
 *     pixels, each drawn one UI unit wide; sized and tiled nine-slices are drawn by the host from
 *     {@link #spriteRegion} calls, since Swing stores no slicing with its images.</li>
 *     <li>Text is drawn with the {@link SwingFont} its style's metrics measured it with, the shadow one unit down
 *     and to the right in a quarter-brightness copy of the color.</li>
 *     <li>A missing sprite draws as magenta, so it shows on screen.</li>
 *     <li>Visuals made by {@link SwingVisuals} draw their image stretched over the rectangle; any other visual draws
 *     as magenta. A tinted visual is recolored on every draw, without a cache.</li>
 *     <li>Scales go through {@link Graphics2D#scale}, so text, clips and images scale with the rest.</li>
 *     <li>{@link #nextLayer()} does nothing, since Java2D already draws in order.</li>
 *     <li>The requested cursor is kept for the host component to apply after the frame; see
 *     {@link #requestedCursor()}.</li>
 * </ul>
 *
 * <p>Call {@link #begin(Graphics2D)} at the start of every frame. Not thread-safe; use it on the Swing event
 * thread.</p>
 */
public final class SwingCanvas implements Canvas {
    /** What a sprite that cannot be found draws as. */
    public static final int MISSING_SPRITE_COLOR = 0xFFFF00FF;

    private final SwingTextMetrics fonts;
    private final SwingSprites sprites;
    private long spritesVersion;
    private final Deque<Optional<Shape>> clips;
    private final Deque<AffineTransform> transforms;
    private final Map<TintKey, BufferedImage> tinted;
    private Graphics2D graphics;
    private CursorKind requestedCursor;

    /**
     * Creates a canvas.
     *
     * @param fonts the fonts and metrics to draw text with; use the same instance as the platform's
     * @param sprites where sprites are loaded from
     */
    public SwingCanvas(final SwingTextMetrics fonts, final SwingSprites sprites) {
        this.fonts = fonts;
        this.sprites = sprites;
        this.clips = new ArrayDeque<>();
        this.transforms = new ArrayDeque<>();
        this.tinted = new HashMap<>();
    }

    /**
     * Points the canvas at the graphics for a new frame, and forgets the last frame's clip, transform and cursor
     * stacks.
     *
     * @param graphics the graphics to draw on; its current transform maps UI units to pixels
     */
    public void begin(final Graphics2D graphics) {
        this.graphics = graphics;
        this.clips.clear();
        this.transforms.clear();
        this.requestedCursor = null;
    }

    /**
     * Returns the cursor the host asked for during the frame.
     *
     * @return the cursor, or {@code null} if none was asked for (show the default)
     */
    public CursorKind requestedCursor() {
        return this.requestedCursor;
    }

    @Override
    public void fill(final Rect rect, final int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        this.graphics.setColor(new Color(argb, true));
        this.graphics.fill(this.snapped(rect));
    }

    /**
     * Returns the rectangle with each edge moved to the nearest device pixel edge, so fills and clips cover whole
     * pixels and shapes laid out symmetrically in UI units stay symmetric at any scale. A rotated or sheared transform
     * gets the rectangle unchanged.
     */
    private Shape snapped(final Rect rect) {
        final AffineTransform transform = this.graphics.getTransform();
        final double scaleX = transform.getScaleX();
        final double scaleY = transform.getScaleY();
        if (transform.getShearX() != 0d || transform.getShearY() != 0d || scaleX == 0d || scaleY == 0d) {
            return rectangle(rect);
        }
        final double translateX = transform.getTranslateX();
        final double translateY = transform.getTranslateY();
        final double left = (Math.round(rect.x() * scaleX + translateX) - translateX) / scaleX;
        final double top = (Math.round(rect.y() * scaleY + translateY) - translateY) / scaleY;
        final double right = (Math.round(rect.right() * scaleX + translateX) - translateX) / scaleX;
        final double bottom = (Math.round(rect.bottom() * scaleY + translateY) - translateY) / scaleY;
        return new Rectangle2D.Double(left, top, right - left, bottom - top);
    }

    @Override
    public void fillGradient(final Rect rect, final int topArgb, final int bottomArgb) {
        this.graphics.setPaint(new GradientPaint(0f, rect.y(), new Color(topArgb, true), 0f, rect.bottom(), new Color(bottomArgb, true)));
        this.graphics.fill(rectangle(rect));
    }

    @Override
    public void sprite(final SpriteId sprite, final Rect rect, final int tint) {
        final Optional<BufferedImage> image = this.image(sprite, tint);
        if (image.isEmpty()) {
            this.fill(rect, MISSING_SPRITE_COLOR);
            return;
        }
        final BufferedImage img = image.get();
        this.drawImage(img, rect.x(), rect.y(), rect.right(), rect.bottom(), 0, 0, img.getWidth(), img.getHeight());
    }

    /**
     * Draws the part straight from the image, with the part's edges rounded to the nearest image pixel, so pieces
     * drawn side by side show no pixels from beyond their part at any scale.
     */
    @Override
    public void spriteRegion(final SpriteId sprite, final Rect rect, final float u0, final float v0,
                             final float u1, final float v1, final int tint) {
        final Optional<BufferedImage> image = this.image(sprite, tint);
        if (image.isEmpty()) {
            this.fill(rect, MISSING_SPRITE_COLOR);
            return;
        }
        final BufferedImage img = image.get();
        final int w = img.getWidth();
        final int h = img.getHeight();
        final int sx0 = Math.clamp(Math.round(u0 * w), 0, w - 1);
        final int sy0 = Math.clamp(Math.round(v0 * h), 0, h - 1);
        final int sx1 = Math.clamp(Math.round(u1 * w), sx0 + 1, w);
        final int sy1 = Math.clamp(Math.round(v1 * h), sy0 + 1, h);
        this.drawImage(img, rect.x(), rect.y(), rect.right(), rect.bottom(), sx0, sy0, sx1, sy1);
    }

    @Override
    public void nineSlice(final SpriteId sprite, final Rect rect, final Insets border, final int tint) {
        final Optional<BufferedImage> image = this.image(sprite, tint);
        if (image.isEmpty()) {
            this.fill(rect, MISSING_SPRITE_COLOR);
            return;
        }
        final BufferedImage img = image.get();
        final int w = img.getWidth();
        final int h = img.getHeight();
        final int l = clampBorder(border.left(), w);
        final int r = clampBorder(border.right(), w - l);
        final int t = clampBorder(border.top(), h);
        final int b = clampBorder(border.bottom(), h - t);

        // Borders wider than the rectangle shrink in proportion, so nothing draws outside it
        final float horizontal = l + r > rect.width() ? rect.width() / (l + r) : 1f;
        final float vertical = t + b > rect.height() ? rect.height() / (t + b) : 1f;

        final float[] dx = {rect.x(), rect.x() + l * horizontal, rect.right() - r * horizontal, rect.right()};
        final float[] dy = {rect.y(), rect.y() + t * vertical, rect.bottom() - b * vertical, rect.bottom()};
        final int[] sx = {0, l, w - r, w};
        final int[] sy = {0, t, h - b, h};

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (dx[column + 1] > dx[column] && dy[row + 1] > dy[row] && sx[column + 1] > sx[column] && sy[row + 1] > sy[row]) {
                    this.drawImage(img, dx[column], dy[row], dx[column + 1], dy[row + 1],
                            sx[column], sy[row], sx[column + 1], sy[row + 1]);
                }
            }
        }
    }

    @Override
    public void text(final TextSpan span, final float x, final float y) {
        final TextStyle style = span.style();
        if ((style.color() >>> 24) == 0 || span.text().isEmpty()) {
            return;
        }
        // Draw with the font the width was measured with, so spans side by side meet exactly
        final SwingFont font = this.fonts.font(style);
        final float baseline = y + font.ascent(style);
        if (style.shadow()) {
            font.draw(this.graphics, span, x + 1f, baseline + 1f, shadowOf(style.color()));
        }
        font.draw(this.graphics, span, x, baseline, style.color());

        if (style.underline() || style.strikethrough()) {
            final float width = font.width(span);
            this.graphics.setColor(new Color(style.color(), true));
            if (style.underline()) {
                this.graphics.fill(new Rectangle2D.Float(x, baseline + 1f, width, 1f));
            }
            if (style.strikethrough()) {
                this.graphics.fill(new Rectangle2D.Float(x, baseline - font.ascent(style) * 0.35f, width, 1f));
            }
        }
    }

    @Override
    public void pushClip(final Rect rect) {
        // getClip() is null when nothing clips yet, and ArrayDeque refuses null
        this.clips.add(Optional.ofNullable(this.graphics.getClip()));
        this.graphics.clip(this.snapped(rect));
    }

    @Override
    public void popClip() {
        this.graphics.setClip(this.clips.removeLast().orElse(null));
    }

    @Override
    public void pushTranslation(final float dx, final float dy) {
        this.transforms.push(this.graphics.getTransform());
        this.graphics.translate(dx, dy);
    }

    @Override
    public void pushScale(final float scale) {
        this.transforms.push(this.graphics.getTransform());
        this.graphics.scale(scale, scale);
    }

    @Override
    public void popTransform() {
        this.graphics.setTransform(this.transforms.pop());
    }

    @Override
    public void visual(final Visual visual, final Rect rect, final int tint) {
        if (!(visual instanceof SwingVisuals.ImageVisual image)) {
            this.fill(rect, MISSING_SPRITE_COLOR);
            return;
        }
        final BufferedImage img = tint == 0xFFFFFFFF ? image.image() : tint(image.image(), tint);
        this.drawImage(img, rect.x(), rect.y(), rect.right(), rect.bottom(), 0, 0, img.getWidth(), img.getHeight());
    }

    @Override
    public void nextLayer() {
        // Java2D draws in call order, so later calls are already on top
    }

    @Override
    public void requestCursor(final CursorKind cursor) {
        this.requestedCursor = cursor;
    }

    /**
     * Draws part of an image over a rectangle in UI units, with each edge moved to the nearest device pixel as
     * {@link #snapped} does. A rotated, sheared or flipped transform draws at the nearest whole units instead.
     */
    private void drawImage(final BufferedImage image, final float left, final float top, final float right,
                           final float bottom, final int sx0, final int sy0, final int sx1, final int sy1) {
        final AffineTransform transform = this.graphics.getTransform();
        final double scaleX = transform.getScaleX();
        final double scaleY = transform.getScaleY();
        if (transform.getShearX() != 0d || transform.getShearY() != 0d || !(scaleX > 0d) || !(scaleY > 0d)) {
            this.graphics.drawImage(image, round(left), round(top), round(right), round(bottom),
                    sx0, sy0, sx1, sy1, null);
            return;
        }
        final int x0 = (int) Math.round(left * scaleX + transform.getTranslateX());
        final int y0 = (int) Math.round(top * scaleY + transform.getTranslateY());
        final int x1 = (int) Math.round(right * scaleX + transform.getTranslateX());
        final int y1 = (int) Math.round(bottom * scaleY + transform.getTranslateY());
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        // The clip is kept in device space, so it still applies after the transform is reset
        this.graphics.setTransform(new AffineTransform());
        try {
            this.graphics.drawImage(image, x0, y0, x1, y1, sx0, sy0, sx1, sy1, null);
        } finally {
            this.graphics.setTransform(transform);
        }
    }

    private Optional<BufferedImage> image(final SpriteId sprite, final int tint) {
        if (this.sprites.version() != this.spritesVersion) {
            this.spritesVersion = this.sprites.version();
            this.tinted.clear();
        }
        final Optional<BufferedImage> image = this.sprites.get(sprite);
        if (image.isEmpty() || tint == 0xFFFFFFFF) {
            return image;
        }
        return Optional.of(this.tinted.computeIfAbsent(new TintKey(sprite, tint), key -> tint(image.get(), tint)));
    }

    /**
     * Multiplies every pixel's channels by the tint's.
     */
    private static BufferedImage tint(final BufferedImage source, final int tint) {
        final int ta = tint >>> 24;
        final int tr = (tint >> 16) & 0xFF;
        final int tg = (tint >> 8) & 0xFF;
        final int tb = tint & 0xFF;
        final BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                final int pixel = source.getRGB(x, y);
                final int a = (pixel >>> 24) * ta / 255;
                final int r = ((pixel >> 16) & 0xFF) * tr / 255;
                final int g = ((pixel >> 8) & 0xFF) * tg / 255;
                final int b = (pixel & 0xFF) * tb / 255;
                result.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return result;
    }

    private static int clampBorder(final float border, final int available) {
        return Math.max(0, Math.min(available, (int) border));
    }

    private static Rectangle2D.Float rectangle(final Rect rect) {
        return new Rectangle2D.Float(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private static int round(final float value) {
        return Math.round(value);
    }

    /**
     * A quarter-brightness copy of the color, for text shadows.
     */
    private static int shadowOf(final int argb) {
        final int rgb = (argb & 0xFCFCFC) >> 2;
        return (argb & 0xFF000000) | rgb;
    }

    private record TintKey(SpriteId sprite, int tint) {
    }
}
