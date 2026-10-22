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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The AWT cursor for each Strata cursor kind. AWT's own cursors cover the arrow, text, hand, crosshair, the four
 * resize kinds (the diagonals as the north-west and north-east resize cursors) and wait. The others (not allowed,
 * progress, grab, grabbing, copy, no drop and help) are drawn here once, at the toolkit's best cursor size, and made
 * with {@link Toolkit#createCustomCursor}. Where the toolkit makes no custom cursors, as in a headless JVM, those kinds
 * have none, and the host asks for their fallback.
 */
final class SwingCursors {
    /** The size the custom cursors are drawn at before they are scaled to the toolkit's best size. */
    private static final int DESIGN = 32;
    private static final Map<CursorKind, Optional<Cursor>> CURSORS = new ConcurrentHashMap<>();

    private SwingCursors() {
    }

    /**
     * Returns the AWT cursor for a kind.
     *
     * @param kind the kind
     * @return the cursor, or empty if this toolkit cannot show the kind
     */
    static Optional<Cursor> cursor(final CursorKind kind) {
        return CURSORS.computeIfAbsent(kind, SwingCursors::make);
    }

    /**
     * Returns whether the toolkit shows a kind as itself.
     *
     * @param kind the kind
     * @return true if {@link #cursor} has one
     */
    static boolean shows(final CursorKind kind) {
        return SwingCursors.cursor(kind).isPresent();
    }

    private static Optional<Cursor> make(final CursorKind kind) {
        return switch (kind) {
            case ARROW -> Optional.of(Cursor.getDefaultCursor());
            case TEXT -> predefined(Cursor.TEXT_CURSOR);
            case HAND -> predefined(Cursor.HAND_CURSOR);
            case CROSSHAIR -> predefined(Cursor.CROSSHAIR_CURSOR);
            case RESIZE_HORIZONTAL -> predefined(Cursor.E_RESIZE_CURSOR);
            case RESIZE_VERTICAL -> predefined(Cursor.N_RESIZE_CURSOR);
            case RESIZE_ALL -> predefined(Cursor.MOVE_CURSOR);
            case RESIZE_DIAGONAL_DOWN -> predefined(Cursor.NW_RESIZE_CURSOR);
            case RESIZE_DIAGONAL_UP -> predefined(Cursor.NE_RESIZE_CURSOR);
            case WAIT -> predefined(Cursor.WAIT_CURSOR);
            case NOT_ALLOWED -> custom("Strata not allowed", 16, 16, SwingCursors::notAllowed);
            case PROGRESS -> custom("Strata progress", 1, 1, g -> {
                arrow(g);
                badge(g);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Arc2D.Float(20f, 20f, 8f, 8f, 90f, 270f, java.awt.geom.Arc2D.OPEN));
            });
            case GRAB -> custom("Strata grab", 16, 16, g -> hand(g, true));
            case GRABBING -> custom("Strata grabbing", 16, 16, g -> hand(g, false));
            case COPY -> custom("Strata copy", 1, 1, g -> {
                arrow(g);
                badge(g);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2.5f));
                g.draw(new Line2D.Float(24f, 20f, 24f, 28f));
                g.draw(new Line2D.Float(20f, 24f, 28f, 24f));
            });
            case NO_DROP -> custom("Strata no drop", 1, 1, g -> {
                arrow(g);
                g.translate(16f, 16f);
                g.scale(0.5f, 0.5f);
                notAllowed(g);
            });
            case HELP -> custom("Strata help", 1, 1, g -> {
                arrow(g);
                badge(g);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                final Path2D.Float question = new Path2D.Float();
                question.moveTo(21.5f, 22f);
                question.curveTo(21.5f, 19f, 26.5f, 19f, 26.5f, 22f);
                question.curveTo(26.5f, 24f, 24f, 24f, 24f, 26f);
                g.draw(question);
                g.fill(new Ellipse2D.Float(23f, 27.5f, 2f, 2f));
            });
        };
    }

    private static Optional<Cursor> predefined(final int type) {
        return Optional.of(Cursor.getPredefinedCursor(type));
    }

    /**
     * Draws a cursor in a 32 by 32 space and makes it at the toolkit's best size.
     */
    private static Optional<Cursor> custom(final String name, final int hotX, final int hotY,
                                           final Consumer<Graphics2D> draw) {
        try {
            final Toolkit toolkit = Toolkit.getDefaultToolkit();
            final Dimension size = toolkit.getBestCursorSize(DESIGN, DESIGN);
            if (size.width <= 0 || size.height <= 0) {
                return Optional.empty();
            }
            final BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.scale(size.width / (double) DESIGN, size.height / (double) DESIGN);
                draw.accept(g);
            } finally {
                g.dispose();
            }
            final Point hotSpot = new Point(Math.min(size.width - 1, hotX * size.width / DESIGN),
                    Math.min(size.height - 1, hotY * size.height / DESIGN));
            return Optional.of(toolkit.createCustomCursor(image, hotSpot, name));
        } catch (final UnsupportedOperationException | IndexOutOfBoundsException e) {
            // A headless toolkit throws HeadlessException, which is one of these; its kinds fall back
            return Optional.empty();
        }
    }

    /**
     * Draws the arrow pointer with its tip at the top left.
     */
    private static void arrow(final Graphics2D g) {
        final Path2D.Float arrow = new Path2D.Float();
        arrow.moveTo(1f, 1f);
        arrow.lineTo(1f, 21f);
        arrow.lineTo(6f, 16.5f);
        arrow.lineTo(9.5f, 24.5f);
        arrow.lineTo(12.5f, 23.2f);
        arrow.lineTo(9f, 15.3f);
        arrow.lineTo(15.5f, 15.3f);
        arrow.closePath();
        outlined(g, arrow);
    }

    /**
     * Draws the dark disc at the bottom right that the copy, progress and help cursors draw their sign on.
     */
    private static void badge(final Graphics2D g) {
        final Ellipse2D.Float disc = new Ellipse2D.Float(18f, 18f, 12f, 12f);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(disc);
        g.setColor(new Color(0xFF202020, true));
        g.fill(disc);
    }

    /**
     * Draws a red circle with a slash across it, centered in the 32 by 32 space.
     */
    private static void notAllowed(final Graphics2D g) {
        final Ellipse2D.Float ring = new Ellipse2D.Float(5f, 5f, 22f, 22f);
        final Line2D.Float slash = new Line2D.Float(8.5f, 8.5f, 23.5f, 23.5f);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(6f));
        g.draw(ring);
        g.draw(slash);
        g.setColor(new Color(0xFFD02020, true));
        g.setStroke(new BasicStroke(3.5f));
        g.draw(ring);
        g.draw(slash);
    }

    /**
     * Draws a hand, open with its fingers up or closed into a fist.
     */
    private static void hand(final Graphics2D g, final boolean open) {
        // The palm, then the thumb, out to the side when open and folded in when closed, then each finger on top, so
        // the outlines between them show
        outlined(g, new RoundRectangle2D.Float(9f, 14f, 16f, 14f, 7f, 7f));
        outlined(g, open
                ? new RoundRectangle2D.Float(3f, 14f, 10f, 4.5f, 4.5f, 4.5f)
                : new RoundRectangle2D.Float(6f, 17f, 7f, 4.5f, 4.5f, 4.5f));
        for (int finger = 0; finger < 4; finger++) {
            final float x = 9f + finger * 4f;
            final float top = (open ? 3f : 11f) + (finger == 0 || finger == 3 ? 2f : 0f);
            outlined(g, new RoundRectangle2D.Float(x, top, 4f, open ? 14f : 6f, 4f, 4f));
        }
    }

    /**
     * Fills a shape white with a black outline.
     */
    private static void outlined(final Graphics2D g, final java.awt.Shape shape) {
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setColor(Color.WHITE);
        g.fill(shape);
    }
}
