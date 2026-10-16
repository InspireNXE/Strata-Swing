package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.component.CursorKind;
import org.inspirenxe.strata.api.geom.Insets;
import org.inspirenxe.strata.api.geom.Rect;
import org.inspirenxe.strata.api.text.TextSpan;
import org.inspirenxe.strata.api.text.TextStyle;
import org.inspirenxe.strata.api.theme.SpriteId;
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
import java.util.Optional;

/**
 * Draws straight onto a {@link Graphics2D}. Missing sprites draw as magenta so they are easy to spot.
 *
 * <p>Sprite tints other than opaque white are not applied yet.</p>
 */
public final class SwingCanvas implements Canvas {
    private static final int MISSING = 0xFFFF00FF;

    private final SwingFonts fonts;
    private final SwingSprites sprites;
    private final Deque<Optional<Shape>> clips;
    private final Deque<AffineTransform> transforms;
    private Graphics2D graphics;
    private CursorKind requestedCursor;

    public SwingCanvas(final SwingFonts fonts, final SwingSprites sprites) {
        this.fonts = fonts;
        this.sprites = sprites;
        this.clips = new ArrayDeque<>();
        this.transforms = new ArrayDeque<>();
    }

    /**
     * Points the canvas at the graphics for this frame.
     */
    public void begin(final Graphics2D graphics) {
        this.graphics = graphics;
        this.clips.clear();
        this.transforms.clear();
        this.requestedCursor = null;
    }

    /**
     * The cursor requested during the last frame, or {@code null}.
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
        final Optional<BufferedImage> image = this.sprites.get(sprite);
        if (image.isEmpty()) {
            this.fill(rect, MISSING);
            return;
        }
        final BufferedImage img = image.get();
        this.graphics.drawImage(img,
                round(rect.x()), round(rect.y()), round(rect.right()), round(rect.bottom()),
                0, 0, img.getWidth(), img.getHeight(), null);
    }

    @Override
    public void nineSlice(final SpriteId sprite, final Rect rect, final Insets border, final int tint) {
        final Optional<BufferedImage> image = this.sprites.get(sprite);
        if (image.isEmpty()) {
            this.fill(rect, MISSING);
            return;
        }
        final BufferedImage img = image.get();
        final int w = img.getWidth();
        final int h = img.getHeight();
        final int l = (int) border.left();
        final int t = (int) border.top();
        final int r = (int) border.right();
        final int b = (int) border.bottom();

        final int[] dx = {round(rect.x()), round(rect.x() + l), round(rect.right() - r), round(rect.right())};
        final int[] dy = {round(rect.y()), round(rect.y() + t), round(rect.bottom() - b), round(rect.bottom())};
        final int[] sx = {0, l, w - r, w};
        final int[] sy = {0, t, h - b, h};

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (dx[column + 1] > dx[column] && dy[row + 1] > dy[row]) {
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
    public void popTransform() {
        this.graphics.setTransform(this.transforms.pop());
    }

    @Override
    public void nextLayer() {
        // Java2D draws in call order, so later calls are already on top
    }

    @Override
    public void requestCursor(final CursorKind cursor) {
        this.requestedCursor = cursor;
    }

    private static Rectangle2D.Float rectangle(final Rect rect) {
        return new Rectangle2D.Float(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private static int round(final float value) {
        return Math.round(value);
    }

    /**
     * A quarter-brightness copy of the color, as Minecraft draws text shadows.
     */
    private static int shadowOf(final int argb) {
        final int rgb = (argb & 0xFCFCFC) >> 2;
        return (argb & 0xFF000000) | rgb;
    }
}
