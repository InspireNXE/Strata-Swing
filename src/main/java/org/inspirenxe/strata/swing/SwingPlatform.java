package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.spi.Capabilities;
import org.inspirenxe.strata.spi.Clipboard;
import org.inspirenxe.strata.spi.Clock;
import org.inspirenxe.strata.spi.Platform;
import org.inspirenxe.strata.spi.TextInputSink;
import org.inspirenxe.strata.spi.ThemeSource;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * The Swing {@link Platform}.
 *
 * <ul>
 *     <li>Text is measured with the given {@link SwingTextMetrics}, which the canvas also draws with.</li>
 *     <li>The clipboard is the system clipboard. In a headless JVM there is none, and the clipboard is
 *     {@link Clipboard#NONE}.</li>
 *     <li>The clock is {@link Clock#SYSTEM}.</li>
 *     <li>AWT delivers typed characters without being told, so text input needs no signal.</li>
 *     <li>AWT reports held keys as repeated presses, which {@link StrataPanel} turns into repeats, so
 *     {@link Capabilities#sendsKeyRepeat()} is true.</li>
 *     <li>Themes named by id come from a {@link SwingThemeSource}, which reads theme files from the class
 *     path.</li>
 * </ul>
 */
public final class SwingPlatform implements Platform {
    private static final Capabilities CAPABILITIES = new Capabilities(true);

    private final SwingTextMetrics textMetrics;
    private final Clipboard clipboard;
    private final ThemeSource themes;

    /**
     * Creates the platform with a {@link SwingThemeSource} on this class's class loader.
     *
     * @param textMetrics the fonts to measure and draw text with
     */
    public SwingPlatform(final SwingTextMetrics textMetrics) {
        this(textMetrics, new SwingThemeSource(SwingPlatform.class.getClassLoader()));
    }

    /**
     * Creates the platform.
     *
     * @param textMetrics the fonts to measure and draw text with
     * @param themes where themes named by id come from
     */
    public SwingPlatform(final SwingTextMetrics textMetrics, final ThemeSource themes) {
        this.textMetrics = textMetrics;
        this.themes = Objects.requireNonNull(themes, "themes");
        this.clipboard = GraphicsEnvironment.isHeadless() ? Clipboard.NONE : new SystemClipboard();
    }

    @Override
    public ThemeSource themes() {
        return this.themes;
    }

    @Override
    public SwingTextMetrics textMetrics() {
        return this.textMetrics;
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

    /**
     * The system clipboard. Reads and writes that fail (another program holds the clipboard, or it holds no text)
     * read as empty and are dropped.
     */
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
