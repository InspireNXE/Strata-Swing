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
 *     <li>Coordinates stay fractional for fills, clips and text; sprites snap to whole pixels.</li>
 *     <li>A sprite tint other than opaque white multiplies each pixel's channels; tinted copies are cached per
 *     sprite and tint.</li>
 *     <li>A nine-slice shrinks its borders to fit a rectangle smaller than them.</li>
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
        this.graphics.fill(rectangle(rect));
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
        this.graphics.drawImage(img,
                round(rect.x()), round(rect.y()), round(rect.right()), round(rect.bottom()),
                0, 0, img.getWidth(), img.getHeight(), null);
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

        final int[] dx = {round(rect.x()), round(rect.x() + l * horizontal), round(rect.right() - r * horizontal), round(rect.right())};
        final int[] dy = {round(rect.y()), round(rect.y() + t * vertical), round(rect.bottom() - b * vertical), round(rect.bottom())};
        final int[] sx = {0, l, w - r, w};
        final int[] sy = {0, t, h - b, h};

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (dx[column + 1] > dx[column] && dy[row + 1] > dy[row] && sx[column + 1] > sx[column] && sy[row + 1] > sy[row]) {
                    this.graphics.drawImage(img,
                            dx[column], dy[row], dx[column + 1], dy[row + 1],
                            sx[column], sy[row], sx[column + 1], sy[row + 1], null);
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
        this.graphics.setFont(this.fonts.font(style));
        final float baseline = y + this.fonts.metrics(style).getAscent();

        if (style.shadow()) {
            this.graphics.setColor(new Color(shadowOf(style.color()), true));
            this.graphics.drawString(span.text(), x + 1f, baseline + 1f);
        }
        this.graphics.setColor(new Color(style.color(), true));
        this.graphics.drawString(span.text(), x, baseline);

        if (style.underline() || style.strikethrough()) {
            final float width = this.fonts.width(span);
            if (style.underline()) {
                this.graphics.fill(new Rectangle2D.Float(x, baseline + 1f, width, 1f));
            }
            if (style.strikethrough()) {
                this.graphics.fill(new Rectangle2D.Float(x, baseline - this.fonts.metrics(style).getAscent() * 0.35f, width, 1f));
            }
        }
    }

    @Override
    public void pushClip(final Rect rect) {
        // getClip() is null when nothing clips yet, and ArrayDeque refuses null
        this.clips.add(Optional.ofNullable(this.graphics.getClip()));
        this.graphics.clip(rectangle(rect));
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
        this.graphics.drawImage(img,
                round(rect.x()), round(rect.y()), round(rect.right()), round(rect.bottom()),
                0, 0, img.getWidth(), img.getHeight(), null);
    }

    @Override
    public void nextLayer() {
        // Java2D draws in call order, so later calls are already on top
    }

    @Override
    public void requestCursor(final CursorKind cursor) {
        this.requestedCursor = cursor;
    }

    private Optional<BufferedImage> image(final SpriteId sprite, final int tint) {
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
