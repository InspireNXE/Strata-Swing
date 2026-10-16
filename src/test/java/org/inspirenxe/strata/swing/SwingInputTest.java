package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.input.Key;
import org.inspirenxe.strata.api.input.Modifier;
import org.inspirenxe.strata.api.input.Modifiers;
import org.inspirenxe.strata.api.input.MouseButton;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingInputTest {
    private static final Component SOURCE = new java.awt.Canvas();

    private static KeyEvent key(final int code, final int location) {
        return new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0L, 0, code, KeyEvent.CHAR_UNDEFINED, location);
    }

    private static Key map(final int code) {
        return SwingInput.key(key(code, KeyEvent.KEY_LOCATION_STANDARD));
    }

    @Test
    void lettersDigitsAndFunctionKeysMapInOrder() {
        for (int i = 0; i < 26; i++) {
            assertEquals(Key.valueOf(String.valueOf((char) ('A' + i))), map(KeyEvent.VK_A + i));
        }
        for (int i = 0; i < 10; i++) {
            assertEquals(Key.valueOf("NUM_" + i), map(KeyEvent.VK_0 + i));
            assertEquals(Key.valueOf("KP_" + i), map(KeyEvent.VK_NUMPAD0 + i));
        }
        for (int i = 0; i < 12; i++) {
            assertEquals(Key.valueOf("F" + (i + 1)), map(KeyEvent.VK_F1 + i));
        }
    }

    @Test
    void editingAndNavigationKeysMap() {
        assertEquals(Key.ENTER, map(KeyEvent.VK_ENTER));
        assertEquals(Key.TAB, map(KeyEvent.VK_TAB));
        assertEquals(Key.ESCAPE, map(KeyEvent.VK_ESCAPE));
        assertEquals(Key.BACKSPACE, map(KeyEvent.VK_BACK_SPACE));
        assertEquals(Key.DELETE, map(KeyEvent.VK_DELETE));
        assertEquals(Key.LEFT, map(KeyEvent.VK_LEFT));
        assertEquals(Key.RIGHT, map(KeyEvent.VK_KP_RIGHT));
        assertEquals(Key.HOME, map(KeyEvent.VK_HOME));
        assertEquals(Key.END, map(KeyEvent.VK_END));
        assertEquals(Key.PAGE_UP, map(KeyEvent.VK_PAGE_UP));
        assertEquals(Key.SPACE, map(KeyEvent.VK_SPACE));
    }

    @Test
    void locationPicksTheSideAndKeypad() {
        assertEquals(Key.LEFT_SHIFT, SwingInput.key(key(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT)));
        assertEquals(Key.RIGHT_SHIFT, SwingInput.key(key(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT)));
        assertEquals(Key.RIGHT_CONTROL, SwingInput.key(key(KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_RIGHT)));
        assertEquals(Key.KP_ENTER, SwingInput.key(key(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_NUMPAD)));
    }

    @Test
    void unmappedKeysAreUnknown() {
        assertEquals(Key.UNKNOWN, map(KeyEvent.VK_F24));
        assertEquals(Key.UNKNOWN, map(KeyEvent.VK_UNDEFINED));
    }

    @Test
    void everyMappedKeyIsDistinct() {
        final Set<Key> seen = EnumSet.noneOf(Key.class);
        final int[] codes = {KeyEvent.VK_A, KeyEvent.VK_Z, KeyEvent.VK_0, KeyEvent.VK_9, KeyEvent.VK_ENTER, KeyEvent.VK_TAB,
                KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE, KeyEvent.VK_INSERT, KeyEvent.VK_LEFT,
                KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_SPACE,
                KeyEvent.VK_COMMA, KeyEvent.VK_PERIOD, KeyEvent.VK_SLASH, KeyEvent.VK_SEMICOLON, KeyEvent.VK_QUOTE,
                KeyEvent.VK_MINUS, KeyEvent.VK_EQUALS, KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_CLOSE_BRACKET,
                KeyEvent.VK_BACK_SLASH, KeyEvent.VK_BACK_QUOTE};
        for (final int code : codes) {
            final Key mapped = map(code);
            assertTrue(mapped != Key.UNKNOWN, "VK " + code + " maps to a key");
            assertTrue(seen.add(mapped), "VK " + code + " maps to " + mapped + ", which another code already uses");
        }
    }

    @Test
    void modifierMasksMap() {
        final KeyEvent event = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0L,
                InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.META_DOWN_MASK,
                KeyEvent.VK_A, 'a', KeyEvent.KEY_LOCATION_STANDARD);

        assertEquals(Modifiers.of(Modifier.SHIFT, Modifier.CONTROL, Modifier.ALT, Modifier.SUPER), SwingInput.modifiers(event));
        assertEquals(Modifiers.NONE, SwingInput.modifiers(key(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD)));
    }

    @Test
    void mouseButtonsMap() {
        assertEquals(MouseButton.LEFT, SwingInput.button(mouse(MouseEvent.BUTTON1)));
        assertEquals(MouseButton.MIDDLE, SwingInput.button(mouse(MouseEvent.BUTTON2)));
        assertEquals(MouseButton.RIGHT, SwingInput.button(mouse(MouseEvent.BUTTON3)));
        assertEquals(MouseButton.BACK, SwingInput.button(mouse(4)));
        assertEquals(MouseButton.FORWARD, SwingInput.button(mouse(5)));
        assertEquals(MouseButton.UNKNOWN, SwingInput.button(mouse(MouseEvent.NOBUTTON)));
    }

    private static MouseEvent mouse(final int button) {
        // Headless AWT refuses to construct events for buttons past 3, so report the button by override instead
        return new MouseEvent(SOURCE, MouseEvent.MOUSE_PRESSED, 0L, 0, 0, 0, 1, false, MouseEvent.NOBUTTON) {
            private static final long serialVersionUID = 1L;

            @Override
            public int getButton() {
                return button;
            }
        };
    }
}
