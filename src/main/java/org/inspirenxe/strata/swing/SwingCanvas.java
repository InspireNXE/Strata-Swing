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
import org.inspirenxe.strata.api.extension.Gradient;
import org.inspirenxe.strata.api.extension.Shape;
import org.inspirenxe.strata.api.extension.Stroke;
import org.inspirenxe.strata.api.geometry.Insets;
import org.inspirenxe.strata.api.geometry.Rect;
import org.inspirenxe.strata.api.text.TextSpan;
import org.inspirenxe.strata.api.text.TextStyle;
import org.inspirenxe.strata.api.theme.SpriteId;
import org.inspirenxe.strata.api.visual.PixelBuffer;
import org.inspirenxe.strata.api.visual.Visual;
import org.inspirenxe.strata.spi.Canvas;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The Swing {@link Canvas}: draws straight onto a {@link Graphics2D}, in call order.
 *
 * <p>How it meets the canvas contract on Java2D:</p>
 * <ul>
 *     <li>Coordinates stay fractional for text. Fills, clips, sprites and visuals snap each edge to the nearest
 *     device pixel, so pieces that share an edge meet without gaps or overlaps at any scale.</li>
 *     <li>A sprite tint other than opaque white multiplies each pixel's channels; tinted copies are cached per
 *     sprite and tint.</li>
 *     <li>Sprites draw the densest image {@link SwingSprites} has for the device pixels under them, read from the
 *     current transform, so pushed scales count too.</li>
 *     <li>A nine-slice shrinks its borders to fit a rectangle smaller than them. Its border is in the plain image's
 *     pixels, each drawn one UI unit wide, and in proportion in a denser image; sized and tiled nine-slices are drawn by core from
 *     {@link #spriteRegion} calls, since Swing stores no slicing with its images.</li>
 *     <li>Text is drawn with the {@link SwingFont} its style's metrics measured it with, the shadow one unit down
 *     and to the right in a quarter-brightness copy of the color.</li>
 *     <li>A missing sprite draws as magenta, so it shows on screen.</li>
 *     <li>Visuals made by {@link SwingVisuals} draw their image stretched over the rectangle; any other visual draws
 *     as magenta. A tinted visual is recolored on every draw, without a cache.</li>
 *     <li>Scales go through {@link Graphics2D#scale}, so text, clips and images scale with the rest.</li>
 *     <li>{@link #nextLayer()} does nothing, since Java2D already draws in order.</li>
 *     <li>It draws every shape call, so the platform reports {@code drawsShapes}. Shapes, gradients at an angle and
 *     shadows are anti-aliased unless {@link #pushSmoothing pushSmoothing(false)} turns it off, and strokes use pure
 *     stroke control, so they land where the contract puts them. Shapes are kept as Java2D paths by identity.
 *     Round clips are hard-edged. A shadow is a rounded rectangle blurred three times with a box blur, cached for
 *     the 64 sizes and colors drawn last.</li>
 *     <li>Opacity is an {@link AlphaComposite}, so a fade covers fills, gradients, shapes, sprites, nine-slices,
 *     text, visuals and pixel buffers alike.</li>
 *     <li>A pixel buffer is drawn from an image that shares its array, so a new version needs no copy, with nearest
 *     neighbor scaling so that pixel art stays sharp. A tinted copy is made again when the version moves.</li>
 *     <li>The requested cursor is kept for the host component to apply after the frame; see
 *     {@link #requestedCursor()}. {@link #showsCursor} answers for AWT's cursors and the ones the adapter draws.</li>
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
    /** How many shadow images are kept, most recently drawn first. */
    private static final int SHADOW_CACHE = 64;
    /** How far apart two gradient stops at the same offset are drawn, since Java2D needs rising fractions. */
    private static final float STOP_GAP = 1e-4f;

    private final Deque<Optional<java.awt.Shape>> clips;
    private final Deque<AffineTransform> transforms;
    private final Map<TintKey, BufferedImage> tinted;
    private final Map<Shape, Path2D.Float> paths;
    private final Map<Stroke, BasicStroke> strokes;
    private final Map<ShadowKey, BufferedImage> shadows;
    private final Map<PixelBuffer, PixelImage> pixelImages;
    private final Path2D.Float polyline;
    private boolean[] smoothing;
    private int smoothingDepth;
    private float[] opacities;
    private int opacityDepth;
    private Composite baseComposite;
    private float baseOpacity;
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
        this.paths = new WeakHashMap<>();
        this.strokes = new HashMap<>();
        this.shadows = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(final Map.Entry<ShadowKey, BufferedImage> eldest) {
                return this.size() > SHADOW_CACHE;
            }
        };
        this.pixelImages = new WeakHashMap<>();
        this.polyline = new Path2D.Float();
        this.smoothing = new boolean[8];
        this.opacities = new float[8];
    }

    /**
     * Points the canvas at the graphics for a new frame, and forgets the last frame's clip, transform, opacity,
     * smoothing and cursor stacks. Opacity multiplies the graphics' own alpha composite, if it has one.
     *
     * @param graphics the graphics to draw on; its current transform maps UI units to pixels
     */
    public void begin(final Graphics2D graphics) {
        this.graphics = graphics;
        this.clips.clear();
        this.transforms.clear();
        this.smoothingDepth = 0;
        this.opacityDepth = 0;
        this.baseComposite = graphics.getComposite();
        this.baseOpacity = this.baseComposite instanceof final AlphaComposite alpha
                && alpha.getRule() == AlphaComposite.SRC_OVER ? alpha.getAlpha() : 1f;
        this.requestedCursor = null;
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
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
    private Rectangle2D snapped(final Rect rect) {
        final AffineTransform transform = this.graphics.getTransform();
        final double scaleX = transform.getScaleX();
        final double scaleY = transform.getScaleY();
        if (transform.getShearX() != 0d || transform.getShearY() != 0d || scaleX == 0d || scaleY == 0d) {
            return new Rectangle2D.Float(rect.x(), rect.y(), rect.width(), rect.height());
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
        final Optional<SwingSprites.SpriteImage> image = this.image(sprite, tint);
        if (image.isEmpty()) {
            this.fill(rect, MISSING_SPRITE_COLOR);
            return;
        }
        final BufferedImage img = image.get().image();
        this.drawImage(img, rect.x(), rect.y(), rect.right(), rect.bottom(), 0, 0, img.getWidth(), img.getHeight());
    }

    /**
     * Draws the part straight from the image, with the part's edges rounded to the nearest image pixel, so pieces
     * drawn side by side show no pixels from beyond their part at any scale.
     */
    @Override
    public void spriteRegion(final SpriteId sprite, final Rect rect, final float u0, final float v0,
                             final float u1, final float v1, final int tint) {
        final Optional<SwingSprites.SpriteImage> image = this.image(sprite, tint);
        if (image.isEmpty()) {
            this.fill(rect, MISSING_SPRITE_COLOR);
            return;
        }
        final BufferedImage img = image.get().image();
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
        final Optional<SwingSprites.SpriteImage> image = this.image(sprite, tint);
        if (image.isEmpty()) {
            this.fill(rect, MISSING_SPRITE_COLOR);
            return;
        }
        final BufferedImage img = image.get().image();
        // The border counts the plain image's pixels; a denser image has more of them under each unit
        final float density = image.get().density();
        final int w = img.getWidth();
        final int h = img.getHeight();
        final int l = clampBorder(border.left() * density, w);
        final int r = clampBorder(border.right() * density, w - l);
        final int t = clampBorder(border.top() * density, h);
        final int b = clampBorder(border.bottom() * density, h - t);
        final float left = l / density;
        final float right = r / density;
        final float top = t / density;
        final float bottom = b / density;

        // Borders wider than the rectangle shrink in proportion, so nothing draws outside it
        final float horizontal = left + right > rect.width() ? rect.width() / (left + right) : 1f;
        final float vertical = top + bottom > rect.height() ? rect.height() / (top + bottom) : 1f;

        final float[] dx = {rect.x(), rect.x() + left * horizontal, rect.right() - right * horizontal, rect.right()};
        final float[] dy = {rect.y(), rect.y() + top * vertical, rect.bottom() - bottom * vertical, rect.bottom()};
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
    public void pushRoundClip(final Rect rect, final float radius) {
        this.clips.add(Optional.ofNullable(this.graphics.getClip()));
        final Rectangle2D bounds = this.snapped(rect);
        final float r = radius(rect, radius);
        if (r <= 0f) {
            this.graphics.clip(bounds);
            return;
        }
        this.graphics.clip(new RoundRectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(),
                bounds.getHeight(), 2d * r, 2d * r));
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
    public void pushOpacity(final float opacity) {
        if (this.opacityDepth == this.opacities.length) {
            this.opacities = Arrays.copyOf(this.opacities, this.opacityDepth * 2);
        }
        final float factor = this.opacity() * opacity;
        this.opacities[this.opacityDepth++] = factor;
        this.graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                Math.clamp(factor * this.baseOpacity, 0f, 1f)));
    }

    @Override
    public void popOpacity() {
        if (this.opacityDepth == 0) {
            return;
        }
        this.opacityDepth--;
        this.graphics.setComposite(this.opacityDepth == 0
                ? this.baseComposite
                : AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.clamp(this.opacity() * this.baseOpacity, 0f, 1f)));
    }

    private float opacity() {
        return this.opacityDepth == 0 ? 1f : this.opacities[this.opacityDepth - 1];
    }

    @Override
    public void pushSmoothing(final boolean smooth) {
        if (this.smoothingDepth == this.smoothing.length) {
            this.smoothing = Arrays.copyOf(this.smoothing, this.smoothingDepth * 2);
        }
        this.smoothing[this.smoothingDepth++] = smooth;
    }

    @Override
    public void popSmoothing() {
        if (this.smoothingDepth > 0) {
            this.smoothingDepth--;
        }
    }

    /**
     * Sets the color and anti-aliasing for a shape.
     */
    private void shapeColor(final int argb) {
        final boolean smooth = this.smoothingDepth == 0 || this.smoothing[this.smoothingDepth - 1];
        this.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                smooth ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        this.graphics.setColor(new Color(argb, true));
    }

    @Override
    public void fillGradient(final Rect rect, final Gradient gradient) {
        if (!(rect.width() > 0f) || !(rect.height() > 0f)) {
            return;
        }
        final double cos = Math.cos(gradient.angleRadians());
        final double sin = Math.sin(gradient.angleRadians());
        final double half = (Math.abs(rect.width() * cos) + Math.abs(rect.height() * sin)) / 2d;
        final double centerX = rect.x() + rect.width() / 2d;
        final double centerY = rect.y() + rect.height() / 2d;
        final List<Gradient.Stop> stops = gradient.stops();
        final float[] fractions = new float[stops.size() + 2];
        final Color[] colors = new Color[stops.size() + 2];
        int count = 0;
        fractions[count] = 0f;
        colors[count++] = new Color(gradient.colorAt(0f), true);
        for (final Gradient.Stop stop : stops) {
            if (stop.offset() > 0f && stop.offset() < 1f) {
                // Java2D needs rising fractions, so a stop at the same offset as the one before makes a hard step
                final float fraction = Math.max(stop.offset(), fractions[count - 1] + STOP_GAP);
                if (fraction < 1f) {
                    fractions[count] = fraction;
                    colors[count++] = new Color(stop.argb(), true);
                }
            }
        }
        fractions[count] = 1f;
        colors[count++] = new Color(gradient.colorAt(1f), true);
        this.shapeColor(gradient.colorAt(0f));
        this.graphics.setPaint(new LinearGradientPaint(
                new Point2D.Double(centerX - half * cos, centerY - half * sin),
                new Point2D.Double(centerX + half * cos, centerY + half * sin),
                Arrays.copyOf(fractions, count), Arrays.copyOf(colors, count)));
        this.graphics.fill(this.snapped(rect));
    }

    @Override
    public void line(final float x1, final float y1, final float x2, final float y2, final Stroke stroke,
                     final int argb) {
        if (!(stroke.width() > 0f) || (argb >>> 24) == 0) {
            return;
        }
        this.shapeColor(argb);
        this.graphics.setStroke(this.basic(stroke));
        this.graphics.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    @Override
    public void polyline(final float[] points, final int count, final Stroke stroke, final int argb) {
        if (!(stroke.width() > 0f) || (argb >>> 24) == 0 || count < 2) {
            return;
        }
        final Path2D.Float path = this.polyline;
        path.reset();
        path.moveTo(points[0], points[1]);
        for (int i = 1; i < count; i++) {
            path.lineTo(points[2 * i], points[2 * i + 1]);
        }
        this.shapeColor(argb);
        this.graphics.setStroke(this.basic(stroke));
        this.graphics.draw(path);
        path.reset();
    }

    @Override
    public void fillRoundRect(final Rect rect, final float radius, final int argb) {
        if ((argb >>> 24) == 0 || !(rect.width() > 0f) || !(rect.height() > 0f)) {
            return;
        }
        final float r = radius(rect, radius);
        this.shapeColor(argb);
        this.graphics.fill(r > 0f
                ? new RoundRectangle2D.Float(rect.x(), rect.y(), rect.width(), rect.height(), 2f * r, 2f * r)
                : rectangle(rect));
    }

    @Override
    public void strokeRect(final Rect rect, final float radius, final Stroke stroke, final int argb) {
        final float width = stroke.width();
        if (!(width > 0f) || (argb >>> 24) == 0 || !(rect.width() > 0f) || !(rect.height() > 0f)) {
            return;
        }
        final float r = radius(rect, radius);
        if (2f * width >= Math.min(rect.width(), rect.height())) {
            this.fillRoundRect(rect, r, argb);
            return;
        }
        // The stroke is centered on its path, so the path runs half the width inside the rectangle
        final float inset = width / 2f;
        final float inner = Math.max(0f, r - inset);
        final float x = rect.x() + inset;
        final float y = rect.y() + inset;
        final float w = rect.width() - width;
        final float h = rect.height() - width;
        this.shapeColor(argb);
        this.graphics.setStroke(this.inside(width));
        this.graphics.draw(inner > 0f
                ? new RoundRectangle2D.Float(x, y, w, h, 2f * inner, 2f * inner)
                : new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void fillEllipse(final Rect oval, final int argb) {
        if ((argb >>> 24) == 0 || !(oval.width() > 0f) || !(oval.height() > 0f)) {
            return;
        }
        this.shapeColor(argb);
        this.graphics.fill(new Ellipse2D.Float(oval.x(), oval.y(), oval.width(), oval.height()));
    }

    @Override
    public void strokeEllipse(final Rect oval, final Stroke stroke, final int argb) {
        final float width = stroke.width();
        if (!(width > 0f) || (argb >>> 24) == 0 || !(oval.width() > 0f) || !(oval.height() > 0f)) {
            return;
        }
        if (2f * width >= Math.min(oval.width(), oval.height())) {
            this.fillEllipse(oval, argb);
            return;
        }
        final float inset = width / 2f;
        this.shapeColor(argb);
        this.graphics.setStroke(this.inside(width));
        this.graphics.draw(new Ellipse2D.Float(oval.x() + inset, oval.y() + inset, oval.width() - width,
                oval.height() - width));
    }

    @Override
    public void strokeArc(final Rect oval, final float startRadians, final float sweepRadians, final Stroke stroke,
                          final int argb) {
        final float width = stroke.width();
        if (!(width > 0f) || (argb >>> 24) == 0 || !(oval.width() > 0f) || !(oval.height() > 0f)) {
            return;
        }
        final float inset = Math.min(width, Math.min(oval.width(), oval.height())) / 2f;
        // Strata turns clockwise on screen from 3 o'clock in radians; Arc2D turns counterclockwise in degrees
        final Arc2D.Float arc = new Arc2D.Float(oval.x() + inset, oval.y() + inset, oval.width() - 2f * inset,
                oval.height() - 2f * inset, (float) -Math.toDegrees(startRadians),
                (float) -Math.toDegrees(sweepRadians), Arc2D.OPEN);
        this.shapeColor(argb);
        this.graphics.setStroke(this.basic(stroke));
        this.graphics.draw(arc);
    }

    @Override
    public void fillShape(final Shape shape, final int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        this.shapeColor(argb);
        this.graphics.fill(this.path(shape));
    }

    @Override
    public void strokeShape(final Shape shape, final Stroke stroke, final int argb) {
        if (!(stroke.width() > 0f) || (argb >>> 24) == 0) {
            return;
        }
        this.shapeColor(argb);
        this.graphics.setStroke(this.basic(stroke));
        this.graphics.draw(this.path(shape));
    }

    @Override
    public void shadow(final Rect rect, final float radius, final float blur, final int argb) {
        if ((argb >>> 24) == 0 || !(rect.width() > 0f) || !(rect.height() > 0f)) {
            return;
        }
        final AffineTransform transform = this.graphics.getTransform();
        final boolean axisAligned = transform.getShearX() == 0d && transform.getShearY() == 0d
                && transform.getScaleX() > 0d && transform.getScaleY() > 0d;
        // The image is made in device pixels, so the blur stays smooth at any scale
        final double scale = axisAligned ? transform.getScaleX() : Math.hypot(transform.getScaleX(),
                transform.getShearY());
        final int width = (int) Math.round(rect.width() * scale);
        final int height = (int) Math.round(rect.height() * scale);
        final int r = (int) Math.round(radius(rect, radius) * scale);
        final int b = Math.max(1, (int) Math.round(blur * scale));
        if (width <= 0 || height <= 0) {
            return;
        }
        final ShadowKey key = new ShadowKey(width, height, r, b, argb);
        final BufferedImage image = this.shadows.computeIfAbsent(key, SwingCanvas::shadowImage);
        final int margin = shadowMargin(b);
        if (axisAligned) {
            final int x0 = (int) Math.round(rect.x() * transform.getScaleX() + transform.getTranslateX()) - margin;
            final int y0 = (int) Math.round(rect.y() * transform.getScaleY() + transform.getTranslateY()) - margin;
            this.graphics.setTransform(new AffineTransform());
            try {
                this.graphics.drawImage(image, x0, y0, null);
            } finally {
                this.graphics.setTransform(transform);
            }
            return;
        }
        final double unitMargin = margin / scale;
        this.graphics.drawImage(image, (int) Math.round(rect.x() - unitMargin), (int) Math.round(rect.y() - unitMargin),
                (int) Math.round(rect.width() + 2d * unitMargin), (int) Math.round(rect.height() + 2d * unitMargin),
                null);
    }

    @Override
    public void pixels(final PixelBuffer buffer, final Rect rect, final int tint) {
        final PixelImage pixels = this.pixelImages.computeIfAbsent(buffer, PixelImage::new);
        final BufferedImage image = pixels.image(buffer, tint);
        final Object interpolation = this.graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        this.graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        try {
            this.drawImage(image, rect.x(), rect.y(), rect.right(), rect.bottom(), 0, 0, image.getWidth(),
                    image.getHeight());
        } finally {
            if (interpolation != null) {
                this.graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            }
        }
    }

    /**
     * Returns the Java2D stroke for a Strata stroke, with Java2D's default miter limit.
     */
    private BasicStroke basic(final Stroke stroke) {
        return this.strokes.computeIfAbsent(stroke, key -> new BasicStroke(key.width(), switch (key.cap()) {
            case BUTT -> BasicStroke.CAP_BUTT;
            case ROUND -> BasicStroke.CAP_ROUND;
            case SQUARE -> BasicStroke.CAP_SQUARE;
        }, switch (key.join()) {
            case MITER -> BasicStroke.JOIN_MITER;
            case ROUND -> BasicStroke.JOIN_ROUND;
            case BEVEL -> BasicStroke.JOIN_BEVEL;
        }, 10f));
    }

    /**
     * Returns the stroke for an outline drawn inside its shape, whose caps and joins do not apply.
     */
    private BasicStroke inside(final float width) {
        return this.basic(Stroke.of(width));
    }

    /**
     * Returns a shape as a Java2D path, made once per shape.
     */
    private Path2D.Float path(final Shape shape) {
        return this.paths.computeIfAbsent(shape, key -> {
            final Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO);
            key.walk(new Shape.Sink() {
                @Override
                public void moveTo(final float x, final float y) {
                    path.moveTo(x, y);
                }

                @Override
                public void lineTo(final float x, final float y) {
                    path.lineTo(x, y);
                }

                @Override
                public void quadTo(final float cx, final float cy, final float x, final float y) {
                    path.quadTo(cx, cy, x, y);
                }

                @Override
                public void cubicTo(final float c1x, final float c1y, final float c2x, final float c2y,
                                    final float x, final float y) {
                    path.curveTo(c1x, c1y, c2x, c2y, x, y);
                }

                @Override
                public void close() {
                    path.closePath();
                }
            });
            return path;
        });
    }

    /**
     * Returns a corner radius limited to 0 and half the rectangle's shorter side.
     */
    private static float radius(final Rect rect, final float radius) {
        return Math.clamp(radius, 0f, Math.max(0f, Math.min(rect.width(), rect.height()) / 2f));
    }

    /**
     * Returns the box radius of each of the three passes that blur a shadow fading over {@code blur} pixels: the
     * shape is grown by half the blur, and the passes spread it over half the blur each way.
     */
    private static int boxRadius(final int blur) {
        return Math.max(1, Math.round(blur / 6f));
    }

    /**
     * Returns how many pixels a shadow image reaches past the rectangle on each side.
     */
    private static int shadowMargin(final int blur) {
        return (blur + 1) / 2 + 3 * boxRadius(blur) + 1;
    }

    /**
     * Makes a shadow image: the rectangle grown by half the blur, blurred three times with a box blur, in the
     * key's color.
     */
    private static BufferedImage shadowImage(final ShadowKey key) {
        final int margin = shadowMargin(key.blur());
        final int width = key.width() + 2 * margin;
        final int height = key.height() + 2 * margin;
        final BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = mask.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            final float grow = key.blur() / 2f;
            final float r = key.radius() + grow;
            g.fill(new RoundRectangle2D.Float(margin - grow, margin - grow, key.width() + 2f * grow,
                    key.height() + 2f * grow, 2f * r, 2f * r));
        } finally {
            g.dispose();
        }
        final int[] alpha = new int[width * height];
        final int[] argb = mask.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < alpha.length; i++) {
            alpha[i] = argb[i] >>> 24;
        }
        final int box = boxRadius(key.blur());
        final int[] scratch = new int[alpha.length];
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(alpha, scratch, width, height, box, true);
            boxBlur(scratch, alpha, width, height, box, false);
        }
        final int color = key.argb() & 0xFFFFFF;
        final int strength = key.argb() >>> 24;
        for (int i = 0; i < alpha.length; i++) {
            argb[i] = ((alpha[i] * strength / 255) << 24) | color;
        }
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }

    /**
     * Averages each value with its neighbors within a radius along rows or columns, counting values past the edge
     * as 0.
     */
    private static void boxBlur(final int[] source, final int[] target, final int width, final int height,
                                final int radius, final boolean horizontal) {
        final int lines = horizontal ? height : width;
        final int length = horizontal ? width : height;
        final int step = horizontal ? 1 : width;
        final int window = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            final int start = horizontal ? line * width : line;
            int sum = 0;
            for (int i = 0; i < Math.min(radius, length); i++) {
                sum += source[start + i * step];
            }
            for (int i = 0; i < length; i++) {
                final int incoming = i + radius;
                if (incoming < length) {
                    sum += source[start + incoming * step];
                }
                final int outgoing = i - radius - 1;
                if (outgoing >= 0) {
                    sum -= source[start + outgoing * step];
                }
                target[start + i * step] = sum / window;
            }
        }
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
     * Answers true for the arrow, text, hand, crosshair, the resize kinds, the diagonals included, and wait, which
     * AWT has, and for not allowed, progress, grab, grabbing, copy, no drop and help where the toolkit makes custom
     * cursors, which a headless one does not.
     */
    @Override
    public boolean showsCursor(final CursorKind cursor) {
        return SwingCursors.shows(cursor);
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

    private Optional<SwingSprites.SpriteImage> image(final SpriteId sprite, final int tint) {
        if (this.sprites.version() != this.spritesVersion) {
            this.spritesVersion = this.sprites.version();
            this.tinted.clear();
        }
        final Optional<SwingSprites.SpriteImage> image = this.sprites.image(sprite, this.devicePixelsPerUnit());
        if (image.isEmpty() || tint == 0xFFFFFFFF) {
            return image;
        }
        final SwingSprites.SpriteImage plain = image.get();
        return Optional.of(new SwingSprites.SpriteImage(this.tinted.computeIfAbsent(
                new TintKey(sprite, tint, plain.density()), key -> tint(plain.image(), tint)), plain.density()));
    }

    /**
     * Returns how many device pixels one unit covers under the current transform.
     */
    private float devicePixelsPerUnit() {
        return (float) Math.sqrt(Math.abs(this.graphics.getTransform().getDeterminant()));
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

    private record TintKey(SpriteId sprite, int tint, float density) {
    }

    /**
     * A shadow image's size, corner radius and blur in device pixels, and its color.
     */
    private record ShadowKey(int width, int height, int radius, int blur, int argb) {
    }

    /**
     * The image a pixel buffer draws from, which shares the buffer's array, and its last tinted copy.
     */
    private static final class PixelImage {
        private final BufferedImage image;
        private BufferedImage tinted;
        private int tint;
        private long version;

        PixelImage(final PixelBuffer buffer) {
            final int width = buffer.width();
            final int height = buffer.height();
            // An image over the caller's array is never cached elsewhere, so new pixels show on the next draw
            final DataBufferInt data = new DataBufferInt(buffer.array(), width * height);
            final ColorModel model = ColorModel.getRGBdefault();
            final WritableRaster raster = Raster.createPackedRaster(data, width, height, width,
                    new int[] {0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000}, null);
            this.image = new BufferedImage(model, raster, false, null);
        }

        BufferedImage image(final PixelBuffer buffer, final int tint) {
            if (tint == 0xFFFFFFFF) {
                return this.image;
            }
            if (this.tinted == null || this.tint != tint || this.version != buffer.version()) {
                this.tinted = SwingCanvas.tint(this.image, tint);
                this.tint = tint;
                this.version = buffer.version();
            }
            return this.tinted;
        }
    }
}
