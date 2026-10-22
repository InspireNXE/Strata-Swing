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

import org.inspirenxe.strata.api.input.Modifier;
import org.inspirenxe.strata.spi.Capabilities;
import org.inspirenxe.strata.spi.Clipboard;
import org.inspirenxe.strata.spi.Clock;
import org.inspirenxe.strata.spi.Conventions;
import org.inspirenxe.strata.spi.Platform;
import org.inspirenxe.strata.spi.TextInputSink;
import org.inspirenxe.strata.spi.ThemeSource;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

/**
 * The Swing {@link Platform}.
 *
 * <ul>
 *     <li>Text is measured with the given {@link SwingTextMetrics}, which the canvas also draws with.</li>
 *     <li>The clipboard is the system clipboard. In a headless JVM there is none, and the clipboard is
 *     {@link Clipboard#NONE}.</li>
 *     <li>The clock is {@link Clock#SYSTEM}.</li>
 *     <li>AWT delivers typed characters without being told, so text input needs no signal. A {@link StrataPanel}
 *     replaces this platform's sink with its own, which turns input methods off while a secret field has focus.</li>
 *     <li>AWT reports held keys as repeated presses, which {@link StrataPanel} turns into repeats, so
 *     {@link Capabilities#sendsKeyRepeat()} is true.</li>
 *     <li>Themes named by id come from a {@link SwingThemeSource}, which reads theme files from the class
 *     path.</li>
 *     <li>{@link Capabilities#monospaceFont()} is true, since the metrics hold a monospaced font, and
 *     {@link Capabilities#slicesSprites()} is false, since Swing stores no slicing with its images.</li>
 *     <li>The UI thread is the Swing event thread: {@link #uiExecutor()} is {@link SwingUtilities#invokeLater} and
 *     {@link #uiThreadCheck()} is {@link SwingUtilities#isEventDispatchThread}.</li>
 *     <li>Shortcuts use Command where the toolkit's menu shortcut key is Meta, as on macOS, and Control elsewhere.
 *     In a headless JVM the operating system's name decides. Buttons act on release and mnemonics are on.</li>
 * </ul>
 *
 * <p>The first platform made registers the adapter's appearance for {@code BufferedImage}: the image as its own
 * icon, at its pixel size.</p>
 */
public final class SwingPlatform implements Platform {
    private static final Capabilities CAPABILITIES = new Capabilities(true, true);
    private static final Optional<Executor> UI_EXECUTOR = Optional.of(SwingUtilities::invokeLater);
    private static final Optional<BooleanSupplier> UI_THREAD_CHECK =
            Optional.of(SwingUtilities::isEventDispatchThread);

    private final SwingTextMetrics textMetrics;
    private final Clipboard clipboard;
    private final ThemeSource themes;
    private final Conventions conventions;

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
        SwingAppearances.install();
        this.textMetrics = textMetrics;
        this.themes = Objects.requireNonNull(themes, "themes");
        this.clipboard = GraphicsEnvironment.isHeadless() ? Clipboard.NONE : new SystemClipboard();
        this.conventions = new Conventions(false, SwingPlatform.shortcutModifier(), true);
    }

    /**
     * Returns the modifier the platform's shortcuts hold: Super (Command) where the toolkit's menu shortcut key is
     * Meta, else Control. Headless, where the toolkit cannot tell, macOS counts as Command.
     */
    private static Modifier shortcutModifier() {
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() == InputEvent.META_DOWN_MASK
                        ? Modifier.SUPER
                        : Modifier.CONTROL;
            } catch (final UnsupportedOperationException ignored) {
                // HeadlessException is one
                // Fall through to the operating system's name
            }
        }
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")
                ? Modifier.SUPER
                : Modifier.CONTROL;
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

    @Override
    public Conventions conventions() {
        return this.conventions;
    }

    @Override
    public Optional<Executor> uiExecutor() {
        return UI_EXECUTOR;
    }

    @Override
    public Optional<BooleanSupplier> uiThreadCheck() {
        return UI_THREAD_CHECK;
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
