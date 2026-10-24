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
import org.inspirenxe.strata.api.transfer.DataFormat;
import org.inspirenxe.strata.api.transfer.Transfer;
import org.inspirenxe.strata.spi.Capabilities;
import org.inspirenxe.strata.spi.Clipboard;
import org.inspirenxe.strata.spi.Clock;
import org.inspirenxe.strata.spi.Conventions;
import org.inspirenxe.strata.spi.FileDialogSource;
import org.inspirenxe.strata.spi.Platform;
import org.inspirenxe.strata.spi.Scheduler;
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
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * The Swing {@link Platform}.
 *
 * <ul>
 *     <li>Text is measured with the given {@link SwingTextMetrics}, which the canvas also draws with.</li>
 *     <li>The clipboard is the system clipboard. It holds plain text, files, images and text in other MIME types,
 *     with styled text going out as its plain text. In a headless JVM there is none, and the clipboard is
 *     {@link Clipboard#NONE}.</li>
 *     <li>The clock is {@link Clock#SYSTEM}.</li>
 *     <li>Repeating tasks run on the event thread, each from a {@link javax.swing.Timer} that coalesces the runs it
 *     falls behind on into one.</li>
 *     <li>AWT delivers typed characters without being told, so text input needs no signal. A {@link StrataPanel}
 *     replaces this platform's sink with its own, which turns input methods off while a secret field has focus.</li>
 *     <li>AWT reports held keys as repeated presses, which {@link StrataPanel} turns into repeats, so
 *     {@link Capabilities#sendsKeyRepeat()} is true.</li>
 *     <li>Themes named by id come from a {@link SwingThemeSource}, which reads theme files from the class
 *     path.</li>
 *     <li>{@link Capabilities#monospaceFont()} is true, since the metrics hold a monospaced font, and
 *     {@link Capabilities#slicesSprites()} is false, since Swing stores no slicing with its images.
 *     {@link Capabilities#drawsShapes()} is true: {@link SwingCanvas} draws shapes, gradients at an angle and
 *     shadows with Java2D. {@link Capabilities#shapesRightToLeft()} is true: each
 *     {@link SwingFont} draws right-to-left runs itself, through {@link SwingFont#drawRightToLeft}, and AWT's fonts
 *     order, shape and join them.</li>
 *     <li>The UI thread is the Swing event thread: {@link #uiExecutor()} is {@link SwingUtilities#invokeLater} and
 *     {@link #uiThreadCheck()} is {@link SwingUtilities#isEventDispatchThread}.</li>
 *     <li>File dialogs are the system's, through {@link java.awt.FileDialog}, owned by the active window; a folder
 *     dialog is a {@link javax.swing.JFileChooser} except on macOS. Headless there are none.</li>
 *     <li>Shortcuts use Command where the toolkit's menu shortcut key is Meta, as on macOS, and Control elsewhere.
 *     In a headless JVM the operating system's name decides. Buttons act on release and mnemonics are on.</li>
 * </ul>
 *
 * <p>The first platform made registers the adapter's appearance for {@code BufferedImage}: the image as its own
 * icon, at its pixel size.</p>
 */
public final class SwingPlatform implements Platform {
    private static final Capabilities CAPABILITIES = new Capabilities(true, true, false, true, true);
    private static final Optional<Executor> UI_EXECUTOR = Optional.of(SwingUtilities::invokeLater);
    private static final Optional<BooleanSupplier> UI_THREAD_CHECK =
            Optional.of(SwingUtilities::isEventDispatchThread);

    private final SwingTextMetrics textMetrics;
    private final Clipboard clipboard;
    private final ThemeSource themes;
    private final Conventions conventions;
    private final Optional<FileDialogSource> fileDialogs;
    private final Scheduler scheduler = new SwingScheduler();

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
        this.clipboard = GraphicsEnvironment.isHeadless()
                ? Clipboard.NONE
                : new SystemClipboard(() -> Toolkit.getDefaultToolkit().getSystemClipboard());
        this.conventions = new Conventions(false, SwingPlatform.shortcutModifier(), true);
        this.fileDialogs = GraphicsEnvironment.isHeadless() ? Optional.empty() : Optional.of(new SwingFileDialogs());
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
    public Scheduler scheduler() {
        return this.scheduler;
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

    @Override
    public Optional<FileDialogSource> fileDialogs() {
        return this.fileDialogs;
    }

    /**
     * An AWT clipboard, the system's unless a test gives another. Reads and writes that fail (another program holds
     * the clipboard, or it holds no text) read as empty and are dropped. A transfer's values other than its text are
     * read when first asked for.
     */
    static final class SystemClipboard implements Clipboard {
        private final Supplier<java.awt.datatransfer.Clipboard> clipboard;

        /**
         * Creates the clipboard.
         *
         * @param clipboard gives the AWT clipboard each time it is used
         */
        SystemClipboard(final Supplier<java.awt.datatransfer.Clipboard> clipboard) {
            this.clipboard = clipboard;
        }

        @Override
        public Optional<String> get() {
            try {
                final java.awt.datatransfer.Clipboard system = this.clipboard.get();
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
                this.clipboard.get().setContents(selection, selection);
            } catch (final IllegalStateException ignored) {
                // Another application holds the clipboard; dropping the copy beats crashing the UI
            }
        }

        @Override
        public Optional<Transfer> getTransfer() {
            try {
                final java.awt.datatransfer.Clipboard system = this.clipboard.get();
                final DataFlavor[] flavors = system.getAvailableDataFlavors();
                // The text is read now, as get() reads it; the rest when a paste asks for it
                final Optional<String> text = this.get();
                final Transfer read = SwingTransfers.transfer(flavors, system::getData, false);
                if (read.isEmpty()) {
                    return Optional.empty();
                }
                final Transfer.Builder builder = Transfer.builder();
                text.ifPresent(value -> builder.add(DataFormat.TEXT, value));
                for (final DataFormat<?> format : read.formats()) {
                    if (format != DataFormat.TEXT) {
                        SystemClipboard.copy(builder, read, format);
                    }
                }
                final Transfer transfer = builder.build();
                return transfer.isEmpty() ? Optional.empty() : Optional.of(transfer);
            } catch (final IllegalStateException e) {
                return Optional.empty();
            }
        }

        private static <T> void copy(final Transfer.Builder builder, final Transfer from,
                                     final DataFormat<T> format) {
            builder.supply(format, () -> from.get(format).orElse(null));
        }

        @Override
        public void setTransfer(final Transfer transfer) {
            final SwingTransfers.Selection selection = SwingTransfers.transferable(transfer);
            if (selection.isEmpty()) {
                return;
            }
            try {
                this.clipboard.get().setContents(selection, selection);
            } catch (final IllegalStateException ignored) {
                // Another application holds the clipboard
            }
        }
    }
}
