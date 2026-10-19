package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.component.CursorKind;
import org.inspirenxe.strata.api.input.Key;
import org.inspirenxe.strata.api.input.KeyAction;
import org.inspirenxe.strata.api.input.ScrollKind;
import org.inspirenxe.strata.api.theme.Theme;
import org.inspirenxe.strata.core.StrataCore;
import org.inspirenxe.strata.spi.HeldKeys;
import org.inspirenxe.strata.spi.ScreenStack;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A Swing component that shows a stack of Strata screens. Add it to a window and push screens onto
 * {@link #screens()}.
 *
 * <pre>{@code
 * StrataPanel panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 2f);
 * panel.screens().push(new SettingsScreen());
 * frame.setContentPane(panel);
 * }</pre>
 *
 * <p>How Swing maps onto Strata:</p>
 * <ul>
 *     <li>One UI unit is {@code scale} pixels. Drawing is scaled up and pointer positions
 *     scaled down by the same factor.</li>
 *     <li>The panel repaints about 60 times a second while it is shown.</li>
 *     <li>It takes keyboard focus when clicked, and turns off Swing's own Tab handling so Strata's tab order
 *     applies.</li>
 *     <li>AWT reports held keys as repeated presses; the panel turns them into repeats. Keys held when the panel
 *     loses focus are forgotten.</li>
 *     <li>Characters come from AWT's key-typed events, with surrogate pairs joined into one code point and control
 *     characters dropped.</li>
 *     <li>Shift plus the vertical wheel scrolls horizontally.</li>
 * </ul>
 *
 * <p>Use it on the Swing event thread only.</p>
 */
public final class StrataPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient ScreenStack screens;
    private final transient SwingCanvas canvas;
    private final transient HeldKeys heldKeys;
    private final transient Timer repaintTimer;
    /**
     * Pixels per UI unit.
     */
    private final float scale;
    /**
     * The high half of a surrogate pair that AWT typed, waiting for its low half; 0 when nothing is waiting.
     */
    private char pendingHighSurrogate;

    /**
     * Creates a panel with an empty screen stack. Sprites load from this class's class loader with
     * {@link SpriteResolver#DEFAULT}.
     *
     * @param platform the Swing platform; its text metrics also draw the text
     * @param theme the theme for every screen
     * @param scale pixels per UI unit, positive
     * @throws IllegalArgumentException if {@code scale} is not positive
     */
    public StrataPanel(final SwingPlatform platform, final Theme theme, final float scale) {
        this(platform, theme, scale, new SwingSprites(StrataPanel.class.getClassLoader()));
    }

    /**
     * Creates a panel with an empty screen stack.
     *
     * @param platform the Swing platform; its text metrics also draw the text
     * @param theme the theme for every screen
     * @param scale pixels per UI unit, positive
     * @param sprites where sprite images come from
     * @throws IllegalArgumentException if {@code scale} is not positive
     */
    @SuppressWarnings("this-escape")
    public StrataPanel(final SwingPlatform platform, final Theme theme, final float scale,
                       final SwingSprites sprites) {
        if (!(scale > 0f)) {
            throw new IllegalArgumentException("Scale must be positive: " + scale);
        }
        this.screens = StrataCore.createScreenStack(platform, theme);
        this.canvas = new SwingCanvas(platform.textMetrics(), sprites);
        this.heldKeys = new HeldKeys();
        this.repaintTimer = new Timer(16, event -> this.repaint());
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
        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent event) {
                StrataPanel.this.heldKeys.clear();
            }
        });
    }

    /**
     * Returns the screens this panel shows. Push a screen to show it; the top screen gets the input.
     *
     * @return the screen stack
     */
    public ScreenStack screens() {
        return this.screens;
    }

    /**
     * Returns the number of pixels per UI unit.
     *
     * @return the scale given to the constructor
     */
    public float scale() {
        return this.scale;
    }

    /**
     * Starts repainting when the panel is shown.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        this.resizeScreens();
        this.repaintTimer.start();
    }

    /**
     * Stops repainting when the panel is removed from its window.
     */
    @Override
    public void removeNotify() {
        this.repaintTimer.stop();
        super.removeNotify();
    }

    /**
     * Sets the panel's size and the screens' size to match.
     */
    @Override
    public void setBounds(final int x, final int y, final int width, final int height) {
        super.setBounds(x, y, width, height);
        this.resizeScreens();
    }

    private void resizeScreens() {
        // Swing may lay the panel out before the constructor has finished
        if (this.screens != null) {
            this.screens.resize(this.getWidth() / this.scale, this.getHeight() / this.scale);
        }
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

    /**
     * Maps a Strata cursor to the nearest AWT cursor. AWT has no "not allowed" cursor, so it shows the default.
     *
     * @param kind the cursor, or {@code null} for the default
     * @return the AWT cursor
     */
    static Cursor cursorFor(final CursorKind kind) {
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
            // AWT counts rotation toward the user as positive; Strata counts away from the user as positive
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
            final KeyAction action = StrataPanel.this.heldKeys.press(key);
            StrataPanel.this.screens.key(key, action, SwingInput.modifiers(event));
        }

        @Override
        public void keyReleased(final KeyEvent event) {
            final Key key = SwingInput.key(event);
            StrataPanel.this.heldKeys.release(key);
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
