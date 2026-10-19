package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.input.Key;
import org.inspirenxe.strata.api.input.Modifier;
import org.inspirenxe.strata.api.input.Modifiers;
import org.inspirenxe.strata.api.input.MouseButton;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Maps AWT key codes, mouse buttons and modifier masks to Strata's input types.
 *
 * <p>AWT's Meta modifier (Command on macOS) maps to {@link Modifier#SUPER}, and AltGr counts as
 * {@link Modifier#ALT}. Keys AWT reports on the right side or the keypad map to Strata's right and keypad keys.</p>
 */
public final class SwingInput {

    private SwingInput() {
    }

    /**
     * Reads the modifiers held during an AWT input event.
     *
     * @param event the event
     * @return the held modifiers
     */
    public static Modifiers modifiers(final InputEvent event) {
        final int ex = event.getModifiersEx();
        Modifiers modifiers = Modifiers.NONE;
        if ((ex & InputEvent.SHIFT_DOWN_MASK) != 0) {
            modifiers = modifiers.with(Modifier.SHIFT);
        }
        if ((ex & InputEvent.CTRL_DOWN_MASK) != 0) {
            modifiers = modifiers.with(Modifier.CONTROL);
        }
        if ((ex & (InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK)) != 0) {
            modifiers = modifiers.with(Modifier.ALT);
        }
        if ((ex & InputEvent.META_DOWN_MASK) != 0) {
            modifiers = modifiers.with(Modifier.SUPER);
        }
        return modifiers;
    }

    /**
     * Maps the button of an AWT mouse event.
     *
     * @param event the event
     * @return the button; buttons 4 and 5 are back and forward, anything else unknown
     */
    public static MouseButton button(final MouseEvent event) {
        return switch (event.getButton()) {
            case MouseEvent.BUTTON1 -> MouseButton.LEFT;
            case MouseEvent.BUTTON2 -> MouseButton.MIDDLE;
            case MouseEvent.BUTTON3 -> MouseButton.RIGHT;
            case 4 -> MouseButton.BACK;
            case 5 -> MouseButton.FORWARD;
            default -> MouseButton.UNKNOWN;
        };
    }

    /**
     * Maps the key of an AWT key event, using its key code and location.
     *
     * @param event a key-pressed or key-released event
     * @return the key, or {@link Key#UNKNOWN} for keys Strata has no constant for
     */
    public static Key key(final KeyEvent event) {
        final boolean right = event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT;
        final boolean numpad = event.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD;
        final int code = event.getKeyCode();

        if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) {
            return Key.values()[Key.A.ordinal() + (code - KeyEvent.VK_A)];
        }
        if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
            return Key.values()[Key.NUM_0.ordinal() + (code - KeyEvent.VK_0)];
        }
        if (code >= KeyEvent.VK_NUMPAD0 && code <= KeyEvent.VK_NUMPAD9) {
            return Key.values()[Key.KP_0.ordinal() + (code - KeyEvent.VK_NUMPAD0)];
        }
        if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12) {
            return Key.values()[Key.F1.ordinal() + (code - KeyEvent.VK_F1)];
        }

        return switch (code) {
            case KeyEvent.VK_SPACE -> Key.SPACE;
            case KeyEvent.VK_QUOTE -> Key.APOSTROPHE;
            case KeyEvent.VK_COMMA -> Key.COMMA;
            case KeyEvent.VK_MINUS -> Key.MINUS;
            case KeyEvent.VK_PERIOD -> Key.PERIOD;
            case KeyEvent.VK_SLASH -> Key.SLASH;
            case KeyEvent.VK_SEMICOLON -> Key.SEMICOLON;
            case KeyEvent.VK_EQUALS -> Key.EQUALS;
            case KeyEvent.VK_OPEN_BRACKET -> Key.LEFT_BRACKET;
            case KeyEvent.VK_BACK_SLASH -> Key.BACKSLASH;
            case KeyEvent.VK_CLOSE_BRACKET -> Key.RIGHT_BRACKET;
            case KeyEvent.VK_BACK_QUOTE -> Key.GRAVE;
            case KeyEvent.VK_ESCAPE -> Key.ESCAPE;
            case KeyEvent.VK_ENTER -> numpad ? Key.KP_ENTER : Key.ENTER;
            case KeyEvent.VK_TAB -> Key.TAB;
            case KeyEvent.VK_BACK_SPACE -> Key.BACKSPACE;
            case KeyEvent.VK_INSERT -> Key.INSERT;
            case KeyEvent.VK_DELETE -> Key.DELETE;
            case KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> Key.LEFT;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> Key.RIGHT;
            case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> Key.UP;
            case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> Key.DOWN;
            case KeyEvent.VK_PAGE_UP -> Key.PAGE_UP;
            case KeyEvent.VK_PAGE_DOWN -> Key.PAGE_DOWN;
            case KeyEvent.VK_HOME -> Key.HOME;
            case KeyEvent.VK_END -> Key.END;
            case KeyEvent.VK_CAPS_LOCK -> Key.CAPS_LOCK;
            case KeyEvent.VK_NUM_LOCK -> Key.NUM_LOCK;
            case KeyEvent.VK_SCROLL_LOCK -> Key.SCROLL_LOCK;
            case KeyEvent.VK_PRINTSCREEN -> Key.PRINT_SCREEN;
            case KeyEvent.VK_PAUSE -> Key.PAUSE;
            case KeyEvent.VK_DECIMAL -> Key.KP_DECIMAL;
            case KeyEvent.VK_DIVIDE -> Key.KP_DIVIDE;
            case KeyEvent.VK_MULTIPLY -> Key.KP_MULTIPLY;
            case KeyEvent.VK_SUBTRACT -> Key.KP_SUBTRACT;
            case KeyEvent.VK_ADD -> Key.KP_ADD;
            case KeyEvent.VK_SHIFT -> right ? Key.RIGHT_SHIFT : Key.LEFT_SHIFT;
            case KeyEvent.VK_CONTROL -> right ? Key.RIGHT_CONTROL : Key.LEFT_CONTROL;
            case KeyEvent.VK_ALT, KeyEvent.VK_ALT_GRAPH -> right ? Key.RIGHT_ALT : Key.LEFT_ALT;
            case KeyEvent.VK_META, KeyEvent.VK_WINDOWS -> right ? Key.RIGHT_SUPER : Key.LEFT_SUPER;
            case KeyEvent.VK_CONTEXT_MENU -> Key.MENU;
            default -> Key.UNKNOWN;
        };
    }
}
