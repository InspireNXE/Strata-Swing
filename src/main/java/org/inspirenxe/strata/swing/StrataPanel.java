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
import org.inspirenxe.strata.api.geometry.Rect;
import org.inspirenxe.strata.api.input.Key;
import org.inspirenxe.strata.api.input.KeyAction;
import org.inspirenxe.strata.api.input.ScrollKind;
import org.inspirenxe.strata.api.input.TextInputKind;
import org.inspirenxe.strata.api.geometry.Size;
import org.inspirenxe.strata.api.input.Modifier;
import org.inspirenxe.strata.api.input.Modifiers;
import org.inspirenxe.strata.api.transfer.DropEffect;
import org.inspirenxe.strata.api.transfer.Transfer;
import org.inspirenxe.strata.api.screen.Screen;
import org.inspirenxe.strata.api.screen.ScreenController;
import org.inspirenxe.strata.api.theme.Theme;
import org.inspirenxe.strata.core.StrataCore;
import org.inspirenxe.strata.spi.Clock;
import org.inspirenxe.strata.spi.HeldKeys;
import org.inspirenxe.strata.spi.ScreenStack;
import org.inspirenxe.strata.spi.TextInputSink;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Optional;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * A Swing component that shows a stack of Strata screens. Add it to a window and open screens on
 * {@link #screens()}, or mark it as the default panel so that {@code Strata.screens()} opens screens on it.
 *
 * <pre>{@code
 * StrataPanel panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 2f);
 * panel.setDefault();
 * frame.setContentPane(panel);
 * Strata.screens().open(new SettingsScreen());
 * }</pre>
 *
 * <p>{@code screens().push(screen)} shows a screen in the theme given to the constructor, or the one set later on
 * the stack. {@code open(screen)} shows it in the platform's default theme, which the platform's
 * {@link SwingThemeSource#setDefaultTheme} sets and is {@code strata:flat} until then. Swing has no screens of its
 * own, so with no default panel {@code Strata.screens()} throws {@link IllegalStateException}; it does not open a
 * window.</p>
 *
 * <p>How Swing maps onto Strata:</p>
 * <ul>
 *     <li>One UI unit is {@code scale} pixels, a zoom that {@link #setScale} changes. Drawing is scaled up and
 *     pointer positions scaled down by the same factor. Java2D applies the display's own scale on top, so the screens'
 *     density, the device pixels per unit that components and sprites read, is the scale times the display's. It
 *     follows the panel to another display.</li>
 *     <li>The panel repaints when its screens need a frame, and at the next timed change, such as a caret's blink,
 *     so an idle panel with a blinking caret paints twice a second and one with nothing timed does not paint.</li>
 *     <li>It takes keyboard focus when clicked, and turns off Swing's own Tab handling so Strata's tab order
 *     applies.</li>
 *     <li>AWT reports held keys as repeated presses; the panel turns them into repeats. Keys held when the panel
 *     loses focus are forgotten.</li>
 *     <li>Characters come from AWT's key-typed events, with surrogate pairs joined into one code point and control
 *     characters dropped.</li>
 *     <li>Shift plus the vertical wheel scrolls horizontally.</li>
 *     <li>It consumes Alt and F10 key events, so they reach Strata without putting the window into its native menu
 *     mode, which on Windows would swallow wheel input until the key is pressed again.</li>
 *     <li>It takes drops from other applications through AWT's drag and drop, in the formats
 *     {@link SwingPlatform}'s clipboard holds. AWT reports no modifier keys during a drag, so they are read from the
 *     drop action: Control when the user asks to copy what the source would move, and Control and Shift for a
 *     link. Data is read when a component asks for it while the drag moves, where AWT allows, and all of it at the
 *     drop. A headless JVM has no drag and drop.</li>
 *     <li>While a field that takes {@link TextInputKind#SECRET} text has focus, such as a password box, it turns
 *     input methods off on itself, as Swing's own password field does, so no composition window shows or keeps
 *     what is typed. They come back on when focus moves to another field or leaves the fields.</li>
 * </ul>
 *
 * <p>The panel's preferred size, which {@link Window#pack()} reads, is what the top screen's content asks for, in
 * pixels at the panel's scale, and 960 by 600 pixels while no screen is open. A size given with
 * {@link #setPreferredSize} wins, as for any Swing component. The panel revalidates when the top screen changes, so
 * a later {@code pack()} sees the new size.</p>
 *
 * <p>Give the panel its window with {@link #manageWindow(Window)} so that the window's close button asks the screens
 * first and its title follows theirs. An application that keeps its own window listener does the same itself: it
 * sets the window to do nothing on close, calls {@code screens().requestClose()} when the window is closing, and
 * disposes the window only when that returns true.</p>
 *
 * <p>Use it on the Swing event thread only. The platform's UI thread is the event thread, so work from other threads
 * goes through {@code Strata.uiThread()} or the factory's {@code uiThread()}, and a screen opened from another
 * thread is handed to the event thread.</p>
 */
public final class StrataPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /**
     * The panel marked with {@link #setDefault()}, or {@code null}.
     */
    private static StrataPanel defaultPanel;

    private final transient ScreenStack screens;
    private final transient SwingTextMetrics textMetrics;
    private transient SwingCanvas canvas;
    private final transient HeldKeys heldKeys;
    private final transient Clock clock;
    /**
     * Repaints once at the screens' next timed change.
     */
    private final transient Timer frameTimer;
    /**
     * Pixels per UI unit.
     */
    private float scale;
    /**
     * The device pixels per unit last given to the screens.
     */
    private float pixelsPerUnit;
    /**
     * The high half of a surrogate pair that AWT typed, waiting for its low half; 0 when nothing is waiting.
     */
    private char pendingHighSurrogate;
    /**
     * Whether a field that takes secret text has focus, so input methods are off.
     */
    private boolean secretInput;
    /**
     * The top screen at the last paint, so a change revalidates the panel.
     */
    private transient Screen shownScreen;
    /**
     * The window {@link #manageWindow} gave the panel, or {@code null}.
     */
    private transient Window window;
    /**
     * The window's own title, shown while the top screen has none.
     */
    private String windowTitle;
    private final transient WindowListener windowCloser;

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
        this.screens = StrataCore.createScreenStack(new HostPlatform(platform, new SecretInputSink(), true), theme);
        this.textMetrics = platform.textMetrics();
        this.canvas = new SwingCanvas(this.textMetrics, sprites);
        this.heldKeys = new HeldKeys();
        this.clock = platform.clock();
        this.frameTimer = new Timer(0, event -> this.repaint());
        this.frameTimer.setRepeats(false);
        this.scale = scale;
        this.updatePixelsPerUnit();

        this.windowCloser = new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                StrataPanel.this.closeWindow();
            }
        };

        this.setBackground(new Color(0xFF1E1F22, true));
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
        // Any thread may ask, such as one that sets a state; repaint() is safe from all of them
        this.screens.setFrameRequester(this::repaint);
        // The panel moved to a display with another scale
        this.addPropertyChangeListener("graphicsConfiguration", event -> this.updatePixelsPerUnit());
        if (!GraphicsEnvironment.isHeadless()) {
            this.setDropTarget(new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK,
                    new Drops(), true));
        }
    }

    /**
     * Returns the screens this panel shows, which is also this panel's {@link ScreenController}. Push or open a
     * screen to show it; the top screen gets the input.
     *
     * @return the screen stack
     */
    public ScreenStack screens() {
        return this.screens;
    }

    /**
     * Marks this panel as the default, so that {@code Strata.screens()} returns its {@link #screens()} and screens
     * opened through it show here. A later call on another panel moves the mark. The mark stays when the panel
     * leaves its window; mark another panel to move it.
     */
    public void setDefault() {
        synchronized (StrataPanel.class) {
            defaultPanel = this;
            StrataCore.setScreenController(this.screens);
        }
    }

    /**
     * Returns whether this panel is the default, the one {@code Strata.screens()} opens screens on.
     *
     * @return true if {@link #setDefault()} marked this panel last
     */
    public boolean isDefault() {
        synchronized (StrataPanel.class) {
            return defaultPanel == this;
        }
    }

    /**
     * Removes the default mark from whichever panel has it, so that {@code Strata.screens()} throws again. For
     * tests.
     */
    static void clearDefault() {
        synchronized (StrataPanel.class) {
            defaultPanel = null;
            StrataCore.clearScreenController();
        }
    }

    /**
     * Changes where sprite images come from, starting with the next frame, such as when a tool switches between
     * sets of images. The screens stay open; pair it with {@code screens().setTheme(...)} to change look in place.
     *
     * @param sprites where sprite images come from
     */
    public void setSprites(final SwingSprites sprites) {
        this.canvas = new SwingCanvas(this.textMetrics, sprites);
        this.repaint();
    }

    /**
     * Returns the number of pixels per UI unit, before the display's own scale.
     *
     * @return the scale given to the constructor or {@link #setScale}
     */
    public float scale() {
        return this.scale;
    }

    /**
     * Changes the number of pixels per UI unit, such as when the user zooms. The screens get the panel's size in the
     * new units and the new density, lay out again and repaint. The panel revalidates, since its preferred size
     * changes with the scale.
     *
     * @param scale pixels per UI unit, positive
     * @throws IllegalArgumentException if {@code scale} is not positive and finite
     */
    public void setScale(final float scale) {
        if (!(scale > 0f) || Float.isInfinite(scale)) {
            throw new IllegalArgumentException("Scale must be positive: " + scale);
        }
        this.scale = scale;
        this.resizeScreens();
        this.updatePixelsPerUnit();
        this.revalidate();
        this.repaint();
    }

    /**
     * Returns how many device pixels one UI unit covers: the scale times the display's scale, which Java2D applies to
     * everything the panel draws. It is the screens' density, which components and painters read and sprites choose
     * their image by. While the panel is not on a display, the display's scale counts as 1.
     *
     * @return device pixels per UI unit
     */
    public float pixelsPerUnit() {
        final GraphicsConfiguration configuration = this.getGraphicsConfiguration();
        final double display = configuration == null ? 1d : configuration.getDefaultTransform().getScaleX();
        return (float) (this.scale * (display > 0d ? display : 1d));
    }

    /**
     * Gives the screens the density when it changed.
     */
    private void updatePixelsPerUnit() {
        // Swing may ask before the constructor has finished
        if (this.screens == null) {
            return;
        }
        final float density = this.pixelsPerUnit();
        if (density != this.pixelsPerUnit) {
            this.pixelsPerUnit = density;
            this.screens.setPixelsPerUnit(density);
        }
    }

    /**
     * Lets the panel run a window. The window's close button then asks the screens with
     * {@link ScreenStack#requestClose()} and disposes the window only when every screen agreed, and the title of a
     * {@link Frame} or {@link Dialog} follows the top screen's {@linkplain ScreenStack#title() title}, translated,
     * shown again whenever it changes. While the top screen has no title, or no screen is open, the window keeps the
     * title it had when it was given. A {@link JFrame} or {@link JDialog} is set to do nothing on close, since the
     * panel decides.
     *
     * <p>The panel need not be inside the window. Giving it another window lets go of the first, which keeps the
     * title it shows then.</p>
     *
     * @param window the window
     */
    public void manageWindow(final Window window) {
        if (this.window != null) {
            this.window.removeWindowListener(this.windowCloser);
        }
        this.window = window;
        this.windowTitle = switch (window) {
            case final Frame frame -> frame.getTitle();
            case final Dialog dialog -> dialog.getTitle();
            default -> "";
        };
        if (window instanceof final JFrame frame) {
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        } else if (window instanceof final JDialog dialog) {
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        }
        window.addWindowListener(this.windowCloser);
        this.showTitle();
    }

    /**
     * Asks the screens to close, as the managed window's close button does, and disposes the window if they all
     * agreed.
     *
     * @return true if the stack is empty afterwards
     */
    boolean closeWindow() {
        if (!this.screens.requestClose()) {
            return false;
        }
        if (this.window != null) {
            this.window.dispose();
        }
        return true;
    }

    /**
     * Returns the title the managed window shows now: the top screen's, or the window's own while it has none.
     *
     * @return the title
     */
    String shownTitle() {
        final String title = this.screens.title().plain();
        return title.isEmpty() && this.windowTitle != null ? this.windowTitle : title;
    }

    private void showTitle() {
        if (this.window == null) {
            return;
        }
        final String title = this.shownTitle();
        if (this.window instanceof final Frame frame && !title.equals(frame.getTitle())) {
            frame.setTitle(title);
        } else if (this.window instanceof final Dialog dialog && !title.equals(dialog.getTitle())) {
            dialog.setTitle(title);
        }
    }

    /**
     * Returns what the top screen's content asks for, in pixels at the panel's scale plus the insets, or 960 by 600
     * pixels while no screen is open. A size set with {@link #setPreferredSize} wins.
     */
    @Override
    public Dimension getPreferredSize() {
        // Swing may ask before the constructor has finished
        if (this.isPreferredSizeSet() || this.screens == null) {
            return super.getPreferredSize();
        }
        if (this.screens.isEmpty()) {
            return new Dimension(960, 600);
        }
        final Size size = this.screens.preferredSize(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        final Insets insets = this.getInsets();
        return new Dimension(pixels(size.width() * this.scale) + insets.left + insets.right,
                pixels(size.height() * this.scale) + insets.top + insets.bottom);
    }

    private static int pixels(final float value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(value));
    }

    /**
     * A drag from another application entered the panel.
     *
     * @param at where, in pixels
     * @param flavors the flavors it offers
     * @param reader reads a flavor, where AWT allows before the drop
     * @param sourceActions the actions the source allows
     * @param dropAction the action the user asks for
     * @return the effect a component would take
     */
    Optional<DropEffect> dragEntered(final Point at, final DataFlavor[] flavors,
                                     final SwingTransfers.FlavorReader reader, final int sourceActions,
                                     final int dropAction) {
        final java.util.Set<DropEffect> allowed = SwingTransfers.effects(sourceActions);
        if (allowed.isEmpty()) {
            return Optional.empty();
        }
        final Transfer transfer = SwingTransfers.transfer(flavors, reader, false);
        return this.screens.dragEntered(at.x / this.scale, at.y / this.scale, transfer, allowed,
                dragModifiers(sourceActions, dropAction));
    }

    /**
     * A drag from another application moved over the panel, or the user changed the action it asks for.
     *
     * @param at where, in pixels
     * @param sourceActions the actions the source allows
     * @param dropAction the action the user asks for
     * @return the effect a component would take
     */
    Optional<DropEffect> dragMoved(final Point at, final int sourceActions, final int dropAction) {
        return this.screens.dragMoved(at.x / this.scale, at.y / this.scale, dragModifiers(sourceActions, dropAction));
    }

    /**
     * Something from another application was dropped on the panel.
     *
     * @param at where, in pixels
     * @param flavors the flavors it offers
     * @param reader reads a flavor
     * @param sourceActions the actions the source allows
     * @param dropAction the action the user asks for
     * @return the effect the target took
     */
    Optional<DropEffect> dropped(final Point at, final DataFlavor[] flavors, final SwingTransfers.FlavorReader reader,
                                 final int sourceActions, final int dropAction) {
        // AWT's data is valid only during the drop, so every value is read now
        final Transfer transfer = SwingTransfers.transfer(flavors, reader, true);
        return this.screens.dropped(at.x / this.scale, at.y / this.scale, transfer,
                dragModifiers(sourceActions, dropAction));
    }

    /**
     * Returns the modifiers a drop action stands for, since AWT reports none during a drag: Control and Shift for a
     * link, Control for a copy where the source would also move, and none otherwise.
     */
    private static Modifiers dragModifiers(final int sourceActions, final int dropAction) {
        if (dropAction == DnDConstants.ACTION_LINK) {
            return Modifiers.NONE.with(Modifier.CONTROL).with(Modifier.SHIFT);
        }
        if (dropAction == DnDConstants.ACTION_COPY && (sourceActions & DnDConstants.ACTION_MOVE) != 0) {
            return Modifiers.NONE.with(Modifier.CONTROL);
        }
        return Modifiers.NONE;
    }

    /**
     * Returns whether input methods are off because a field that takes secret text has focus.
     *
     * @return true while a secret field has focus
     */
    boolean isSecretInput() {
        return this.secretInput;
    }

    /**
     * Returns whether a repaint waits for the screens' next timed change. For tests.
     *
     * @return true while the frame timer runs
     */
    boolean isFrameTimerRunning() {
        return this.frameTimer.isRunning();
    }

    /**
     * Returns how long the frame timer waits, in milliseconds. For tests.
     *
     * @return the delay set after the last paint
     */
    int frameTimerDelay() {
        return this.frameTimer.getInitialDelay();
    }

    /**
     * Sizes the screens and paints the first frame when the panel is shown.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        this.resizeScreens();
        this.updatePixelsPerUnit();
        this.repaint();
    }

    /**
     * Stops waiting for timed changes when the panel is removed from its window.
     */
    @Override
    public void removeNotify() {
        this.frameTimer.stop();
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
        this.updatePixelsPerUnit();
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
        this.scheduleNextFrame();
        final Screen top = this.screens.top().orElse(null);
        if (top != this.shownScreen) {
            this.shownScreen = top;
            this.revalidate();
        }
        this.showTitle();
    }

    /**
     * Starts the frame timer for the screens' next timed change, or stops it when nothing timed waits.
     */
    private void scheduleNextFrame() {
        final long next = this.screens.nextFrameNanos();
        if (next == Long.MAX_VALUE) {
            this.frameTimer.stop();
            return;
        }
        final long nanos = Math.max(0L, next - this.clock.nanoTime());
        // Rounded up, so the frame never comes before the change is due
        final long millis = Math.min(Integer.MAX_VALUE, (nanos + 999_999L) / 1_000_000L);
        this.frameTimer.setInitialDelay((int) millis);
        this.frameTimer.restart();
    }

    /**
     * Maps a Strata cursor to its AWT cursor, or to its fallback's where the toolkit makes no custom cursors.
     *
     * @param kind the cursor, or {@code null} for the default
     * @return the AWT cursor
     */
    static Cursor cursorFor(final CursorKind kind) {
        CursorKind at = kind == null ? CursorKind.ARROW : kind;
        Optional<Cursor> cursor = SwingCursors.cursor(at);
        while (cursor.isEmpty()) {
            at = at.fallback();
            cursor = SwingCursors.cursor(at);
        }
        return cursor.get();
    }

    /**
     * Turns the panel's input methods off while a secret field has focus and on again otherwise.
     */
    private final class SecretInputSink implements TextInputSink {
        @Override
        public void start(final Rect caretArea) {
            this.start(caretArea, TextInputKind.TEXT);
        }

        @Override
        public void start(final Rect caretArea, final TextInputKind kind) {
            this.secret(kind == TextInputKind.SECRET);
        }

        @Override
        public void stop() {
            this.secret(false);
        }

        private void secret(final boolean secret) {
            if (StrataPanel.this.secretInput != secret) {
                StrataPanel.this.secretInput = secret;
                StrataPanel.this.enableInputMethods(!secret);
            }
        }
    }

    /**
     * Hands AWT's drag and drop to the screens. AWT calls it on the event thread.
     */
    private final class Drops implements DropTargetListener {
        @Override
        public void dragEnter(final DropTargetDragEvent event) {
            this.answer(event, StrataPanel.this.dragEntered(event.getLocation(), event.getCurrentDataFlavors(),
                    flavor -> event.getTransferable().getTransferData(flavor), event.getSourceActions(),
                    event.getDropAction()));
        }

        @Override
        public void dragOver(final DropTargetDragEvent event) {
            this.answer(event, StrataPanel.this.dragMoved(event.getLocation(), event.getSourceActions(),
                    event.getDropAction()));
        }

        @Override
        public void dropActionChanged(final DropTargetDragEvent event) {
            this.dragOver(event);
        }

        @Override
        public void dragExit(final DropTargetEvent event) {
            StrataPanel.this.screens.dragExited();
            StrataPanel.this.repaint();
        }

        @Override
        public void drop(final DropTargetDropEvent event) {
            event.acceptDrop(event.getDropAction());
            boolean taken = false;
            try {
                taken = StrataPanel.this.dropped(event.getLocation(), event.getCurrentDataFlavors(),
                        flavor -> event.getTransferable().getTransferData(flavor), event.getSourceActions(),
                        event.getDropAction()).isPresent();
            } finally {
                event.dropComplete(taken);
                StrataPanel.this.repaint();
            }
        }

        private void answer(final DropTargetDragEvent event, final Optional<DropEffect> effect) {
            if (effect.isPresent()) {
                event.acceptDrag(SwingTransfers.action(effect.get()));
            } else {
                event.rejectDrag();
            }
            StrataPanel.this.repaint();
        }
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
            consumeMenuKey(event);
        }

        @Override
        public void keyReleased(final KeyEvent event) {
            final Key key = SwingInput.key(event);
            StrataPanel.this.heldKeys.release(key);
            StrataPanel.this.screens.key(key, KeyAction.RELEASE, SwingInput.modifiers(event));
            consumeMenuKey(event);
        }

        /**
         * Consumes the keys that would otherwise put the window into its native menu mode.
         */
        private static void consumeMenuKey(final KeyEvent event) {
            final int code = event.getKeyCode();
            if (code == KeyEvent.VK_ALT || code == KeyEvent.VK_F10) {
                event.consume();
            }
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
