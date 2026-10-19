package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.ComponentFactory;
import org.inspirenxe.strata.api.component.Component;
import org.inspirenxe.strata.api.control.button.Button;
import org.inspirenxe.strata.api.control.field.TextBox;
import org.inspirenxe.strata.api.event.EventTypes;
import org.inspirenxe.strata.api.input.KeyAction;
import org.inspirenxe.strata.api.layout.AbsolutePanel;
import org.inspirenxe.strata.api.screen.Screen;
import org.inspirenxe.strata.api.theme.Themes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Drives a {@link StrataPanel} with synthetic AWT events, calling its listeners directly so no display or focus
 * owner is needed. The panel runs at scale 2, so AWT pixel (20, 20) is UI point (10, 10).
 */
class StrataPanelTest {
    private StrataPanel panel;

    @BeforeEach
    void createPanel() {
        this.panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 2f);
        this.panel.setBounds(0, 0, 400, 200);
    }

    private void show(final Function<ComponentFactory, Component<?>> build) {
        this.panel.screens().push(new Screen() {
            @Override
            public Component<?> build(final ComponentFactory factory) {
                return build.apply(factory);
            }
        });
    }

    private void mouse(final int id, final int x, final int y, final int button, final int modifiers) {
        final MouseEvent event = new MouseEvent(this.panel, id, 0L, modifiers, x, y, 1, false, button);
        for (final MouseListener listener : this.panel.getMouseListeners()) {
            switch (id) {
                case MouseEvent.MOUSE_PRESSED -> listener.mousePressed(event);
                case MouseEvent.MOUSE_RELEASED -> listener.mouseReleased(event);
                default -> throw new IllegalArgumentException("unsupported id " + id);
            }
        }
    }

    private void click(final int x, final int y) {
        this.mouse(MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK);
        this.mouse(MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1, 0);
    }

    private void key(final int id, final int code, final char character) {
        // AWT requires key-typed events to have no key code and an unknown location
        final int location = id == KeyEvent.KEY_TYPED ? KeyEvent.KEY_LOCATION_UNKNOWN : KeyEvent.KEY_LOCATION_STANDARD;
        final KeyEvent event = new KeyEvent(this.panel, id, 0L, 0, code, character, location);
        for (final KeyListener listener : this.panel.getKeyListeners()) {
            switch (id) {
                case KeyEvent.KEY_PRESSED -> listener.keyPressed(event);
                case KeyEvent.KEY_RELEASED -> listener.keyReleased(event);
                case KeyEvent.KEY_TYPED -> listener.keyTyped(event);
                default -> throw new IllegalArgumentException("unsupported id " + id);
            }
        }
    }

    @Test
    void rejectsAScaleThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 0f));
    }

    @Test
    void sizesScreensInUiUnits() {
        final Button[] button = new Button[1];
        this.show(factory -> {
            button[0] = factory.button("Fill");
            return button[0].setAnchor(org.inspirenxe.strata.api.layout.Anchor.FILL);
        });
        this.panel.setBounds(0, 0, 300, 120);
        // Render once so layout runs at the new size
        this.panel.screens().render(new SwingContractTests.ImageSurface(150, 60,
                new SwingSprites(StrataPanelTest.class.getClassLoader())).canvas());

        assertEquals(150f, button[0].bounds().width());
        assertEquals(60f, button[0].bounds().height());
    }

    @Test
    void clickAtScaledPositionPressesTheButton() {
        final int[] presses = {0};
        this.show(factory -> factory.absolutePanel()
                .add(factory.button("Go").onEvent(Button.PRESSED, event -> presses[0]++), 5, 5));

        this.click(20, 20);
        assertEquals(1, presses[0], "AWT (20, 20) is UI (10, 10), inside the button");

        this.click(4, 4);
        assertEquals(1, presses[0], "AWT (4, 4) is UI (2, 2), outside the button");
    }

    @Test
    void heldKeysBecomeRepeats() {
        final List<KeyAction> actions = new ArrayList<>();
        this.show(factory -> {
            final Button button = factory.button("Focus me");
            button.onEvent(EventTypes.KEY, event -> actions.add(event.action()));
            return factory.absolutePanel().add(button);
        });
        this.click(10, 10);

        this.key(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED);
        this.key(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED);
        this.key(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED);
        this.key(KeyEvent.KEY_RELEASED, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED);
        this.key(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED);

        assertEquals(List.of(KeyAction.PRESS, KeyAction.REPEAT, KeyAction.REPEAT, KeyAction.RELEASE, KeyAction.PRESS), actions);
    }

    @Test
    void typedCharactersReachTheFocusedTextBox() {
        final TextBox[] box = new TextBox[1];
        this.show(factory -> {
            box[0] = factory.textBox();
            return factory.absolutePanel().add(box[0]);
        });
        this.click(10, 10);

        this.key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'h');
        this.key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'i');
        // An emoji arrives from AWT as two key-typed events, one per surrogate
        this.key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\uD83D');
        this.key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\uDE00');
        // Control characters are dropped
        this.key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\u0008');

        assertEquals("hi😀", box[0].text());
    }

    @Test
    void shiftWheelScrollsHorizontally() {
        final List<float[]> scrolls = new ArrayList<>();
        this.show(factory -> {
            final AbsolutePanel area = factory.absolutePanel();
            area.setAnchor(org.inspirenxe.strata.api.layout.Anchor.FILL);
            area.onEvent(EventTypes.SCROLL, event -> scrolls.add(new float[] {event.scrollX(), event.scrollY()}));
            return area;
        });

        this.wheel(1, 0);
        this.wheel(1, InputEvent.SHIFT_DOWN_MASK);

        assertEquals(2, scrolls.size());
        assertEquals(0f, scrolls.get(0)[0]);
        assertEquals(-1f, scrolls.get(0)[1], "a notch toward the user scrolls down");
        assertEquals(-1f, scrolls.get(1)[0], "with Shift it scrolls sideways");
        assertEquals(0f, scrolls.get(1)[1]);
    }

    private void wheel(final int rotation, final int modifiers) {
        final MouseWheelEvent event = new MouseWheelEvent(this.panel, MouseEvent.MOUSE_WHEEL, 0L, modifiers, 50, 50, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation);
        for (final MouseWheelListener listener : this.panel.getMouseWheelListeners()) {
            listener.mouseWheelMoved(event);
        }
    }
}
