package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.text.TextMetrics;
import org.inspirenxe.strata.spi.Capabilities;
import org.inspirenxe.strata.spi.Clipboard;
import org.inspirenxe.strata.spi.Clock;
import org.inspirenxe.strata.spi.Platform;
import org.inspirenxe.strata.spi.TextInputSink;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Optional;

/**
 * The Swing side of the SPI. AWT delivers key repeats itself; {@link StrataPanel} tells repeats from presses.
 */
public final class SwingPlatform implements Platform {
    private static final Capabilities CAPABILITIES = new Capabilities(true);

    private final SwingFonts fonts;
    private final Clipboard clipboard;

    public SwingPlatform(final SwingFonts fonts) {
        this.fonts = fonts;
        this.clipboard = GraphicsEnvironment.isHeadless() ? Clipboard.NONE : new SystemClipboard();
    }

    public SwingFonts fonts() {
        return this.fonts;
    }

    @Override
    public TextMetrics textMetrics() {
        return this.fonts;
    }

    @Override
    public Clipboard clipboard() {
        return this.clipboard;
    }

    @Override
    public Clock clock() {
        return Clock.SYSTEM;
    }

    @Override
    public TextInputSink textInput() {
        return TextInputSink.NONE;
    }

    @Override
    public Capabilities capabilities() {
        return CAPABILITIES;
    }

    private static final class SystemClipboard implements Clipboard {
        @Override
        public Optional<String> get() {
            try {
                final java.awt.datatransfer.Clipboard system = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (!system.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    return Optional.empty();
                }
                return Optional.of((String) system.getData(DataFlavor.stringFlavor));
            } catch (final UnsupportedFlavorException | IOException | IllegalStateException e) {
                return Optional.empty();
            }
        }

        @Override
        public void set(final String text) {
            try {
                final StringSelection selection = new StringSelection(text);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            } catch (final IllegalStateException ignored) {
                // Another application holds the clipboard; dropping the copy beats crashing the UI
            }
        }
    }
}
