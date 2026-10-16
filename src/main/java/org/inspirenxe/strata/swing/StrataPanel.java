package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.component.CursorKind;
import org.inspirenxe.strata.api.input.Key;
import org.inspirenxe.strata.api.input.KeyAction;
import org.inspirenxe.strata.api.input.ScrollKind;
import org.inspirenxe.strata.api.theme.Theme;
import org.inspirenxe.strata.core.StrataCore;
import org.inspirenxe.strata.spi.ScreenStack;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A Swing component that hosts a {@link ScreenStack}. Draws at a fixed UI scale (like Minecraft's GUI scale) and
 * repaints about 60 times a second.
 */
public final class StrataPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient ScreenStack screens;
    private final transient SwingCanvas canvas;
    private final transient Set<Key> keysDown;
    private final float scale;
    private char pendingHighSurrogate;

    public StrataPanel(final SwingPlatform platform, final Theme theme, final float scale) {
        this.screens = StrataCore.createScreenStack(platform, theme);
        this.canvas = new SwingCanvas(platform.fonts(), new SwingSprites(StrataPanel.class.getClassLoader()));
        this.keysDown = EnumSet.noneOf(Key.class);
        this.scale = scale;

        this.setBackground(new Color(0xFF1E1F22, true));
        this.setPreferredSize(new Dimension(960, 600));
        this.setFocusable(true);
        // Strata does its own tab navigation
        this.setFocusTraversalKeysEnabled(false);

        final Mouse mouse = new Mouse();
        this.addMouseListener(mouse);
        this.addMouseMotionListener(mouse);
        this.addMouseWheelListener(mouse);
        this.addKeyListener(new Keyboard());
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent event) {
                StrataPanel.this.screens.resize(StrataPanel.this.getWidth() / scale, StrataPanel.this.getHeight() / scale);
            }
        });

        new Timer(16, event -> this.repaint()).start();
    }

    public ScreenStack screens() {
        return this.screens;
    }

    public float scale() {
        return this.scale;
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.scale(this.scale, this.scale);
            this.canvas.begin(g);
            this.screens.render(this.canvas);
        } finally {
            g.dispose();
        }
        this.setCursor(cursorFor(this.canvas.requestedCursor()));
    }

    private static Cursor cursorFor(final CursorKind kind) {
        if (kind == null) {
            return Cursor.getDefaultCursor();
        }
        return Cursor.getPredefinedCursor(switch (kind) {
            case ARROW, NOT_ALLOWED -> Cursor.DEFAULT_CURSOR;
            case TEXT -> Cursor.TEXT_CURSOR;
            case HAND -> Cursor.HAND_CURSOR;
            case CROSSHAIR -> Cursor.CROSSHAIR_CURSOR;
            case RESIZE_HORIZONTAL -> Cursor.E_RESIZE_CURSOR;
            case RESIZE_VERTICAL -> Cursor.N_RESIZE_CURSOR;
            case RESIZE_ALL -> Cursor.MOVE_CURSOR;
        });
    }

    private final class Mouse extends MouseAdapter {
        private float x(final MouseEvent event) {
            return event.getX() / StrataPanel.this.scale;
        }

        private float y(final MouseEvent event) {
            return event.getY() / StrataPanel.this.scale;
        }

        @Override
        public void mouseMoved(final MouseEvent event) {
            StrataPanel.this.screens.pointerMoved(this.x(event), this.y(event), SwingInput.modifiers(event));
        }

        @Override
        public void mouseDragged(final MouseEvent event) {
            this.mouseMoved(event);
        }

        @Override
        public void mousePressed(final MouseEvent event) {
            StrataPanel.this.requestFocusInWindow();
            StrataPanel.this.screens.pointerButton(this.x(event), this.y(event), SwingInput.button(event), true,
                    SwingInput.modifiers(event));
        }

        @Override
        public void mouseReleased(final MouseEvent event) {
            StrataPanel.this.screens.pointerButton(this.x(event), this.y(event), SwingInput.button(event), false,
                    SwingInput.modifiers(event));
        }

        @Override
        public void mouseExited(final MouseEvent event) {
            StrataPanel.this.screens.pointerLeft();
        }

        @Override
        public void mouseWheelMoved(final MouseWheelEvent event) {
            final double rotation = event.getPreciseWheelRotation();
            final ScrollKind kind = rotation == Math.rint(rotation) ? ScrollKind.WHEEL : ScrollKind.PRECISE;
            final float amount = (float) -rotation;
            if (event.isShiftDown()) {
                StrataPanel.this.screens.scroll(this.x(event), this.y(event), amount, 0f, kind, SwingInput.modifiers(event));
            } else {
                StrataPanel.this.screens.scroll(this.x(event), this.y(event), 0f, amount, kind, SwingInput.modifiers(event));
            }
        }
    }

    private final class Keyboard extends KeyAdapter {
        @Override
        public void keyPressed(final KeyEvent event) {
            final Key key = SwingInput.key(event);
            // AWT reports a held key as repeated presses; the first one is the real press
            final KeyAction action = StrataPanel.this.keysDown.add(key) ? KeyAction.PRESS : KeyAction.REPEAT;
            StrataPanel.this.screens.key(key, action, SwingInput.modifiers(event));
        }

        @Override
        public void keyReleased(final KeyEvent event) {
            final Key key = SwingInput.key(event);
            StrataPanel.this.keysDown.remove(key);
            StrataPanel.this.screens.key(key, KeyAction.RELEASE, SwingInput.modifiers(event));
        }

        @Override
        public void keyTyped(final KeyEvent event) {
            final char c = event.getKeyChar();
            if (c == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(c)) {
                return;
            }
            if (Character.isHighSurrogate(c)) {
                StrataPanel.this.pendingHighSurrogate = c;
                return;
            }
            int codePoint = c;
            if (Character.isLowSurrogate(c) && StrataPanel.this.pendingHighSurrogate != 0) {
                codePoint = Character.toCodePoint(StrataPanel.this.pendingHighSurrogate, c);
            }
            StrataPanel.this.pendingHighSurrogate = 0;
            StrataPanel.this.screens.charTyped(codePoint, SwingInput.modifiers(event));
        }
    }
}
