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

import org.inspirenxe.strata.api.file.FileDialog;
import org.inspirenxe.strata.spi.FileDialogSource;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

/**
 * File dialogs over {@link java.awt.FileDialog}, the system's own, owned by the active window. A folder dialog is
 * the system's on macOS and a {@link JFileChooser} elsewhere, since AWT's dialog picks only files there.
 *
 * <p>Each dialog is shown from a later event on the event thread, so the call returns at once; the dialog's nested
 * loop keeps the window painting while it is open, and the future completes when it closes. Titles are read
 * through the translator then. Filters go in as a filename filter, which Windows ignores, so there the file name
 * starts as the filters' patterns, such as {@code *.csv;*.xlsx}, unless a name was suggested or a file given as
 * the start.</p>
 */
final class SwingFileDialogs implements FileDialogSource {
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    @Override
    public CompletableFuture<Optional<Path>> open(final FileDialog dialog) {
        return this.show(() -> SwingFileDialogs.files(dialog, java.awt.FileDialog.LOAD, false))
                .thenApply(files -> files.stream().findFirst());
    }

    @Override
    public CompletableFuture<List<Path>> openMany(final FileDialog dialog) {
        return this.show(() -> SwingFileDialogs.files(dialog, java.awt.FileDialog.LOAD, true));
    }

    @Override
    public CompletableFuture<Optional<Path>> save(final FileDialog dialog) {
        return this.show(() -> SwingFileDialogs.files(dialog, java.awt.FileDialog.SAVE, false))
                .thenApply(files -> files.stream().findFirst());
    }

    @Override
    public CompletableFuture<Optional<Path>> folder(final FileDialog dialog) {
        return this.show(() -> OS.startsWith("mac") ? SwingFileDialogs.macFolder(dialog)
                : SwingFileDialogs.chooserFolder(dialog)).thenApply(files -> files.stream().findFirst());
    }

    /**
     * Shows a dialog from a later event on the event thread and completes with what it returns.
     */
    private CompletableFuture<List<Path>> show(final Supplier<List<Path>> dialog) {
        final CompletableFuture<List<Path>> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                result.complete(dialog.get());
            } catch (final RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    private static List<Path> files(final FileDialog dialog, final int mode, final boolean multiple) {
        final java.awt.FileDialog awt = SwingFileDialogs.awtDialog(dialog, mode);
        awt.setMultipleMode(multiple);
        if (mode == java.awt.FileDialog.SAVE && !dialog.suggestedName().isEmpty()) {
            awt.setFile(dialog.suggestedName());
        }
        if (!dialog.filters().isEmpty()) {
            awt.setFilenameFilter((directory, name) -> SwingFileDialogs.accepts(dialog, name));
            if (OS.startsWith("windows") && awt.getFile() == null) {
                final List<String> patterns = new ArrayList<>();
                dialog.filters().forEach(filter -> filter.extensions().forEach(extension -> patterns.add("*." + extension)));
                awt.setFile(String.join(";", patterns));
            }
        }
        awt.setVisible(true);
        try {
            if (awt.getFile() == null) {
                return List.of();
            }
            if (multiple) {
                return Arrays.stream(awt.getFiles()).map(File::toPath).toList();
            }
            return List.of(new File(awt.getDirectory(), awt.getFile()).toPath());
        } finally {
            awt.dispose();
        }
    }

    private static List<Path> macFolder(final FileDialog dialog) {
        final String property = "apple.awt.fileDialogForDirectories";
        final String before = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            return SwingFileDialogs.files(new FileDialog(dialog.title(), dialog.start(), "", List.of()),
                    java.awt.FileDialog.LOAD, false);
        } finally {
            if (before == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, before);
            }
        }
    }

    private static List<Path> chooserFolder(final FileDialog dialog) {
        final JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(dialog.title().plain());
        if (dialog.start() != null) {
            chooser.setCurrentDirectory(dialog.start().toFile());
        }
        if (chooser.showOpenDialog(SwingFileDialogs.owner()) != JFileChooser.APPROVE_OPTION
                || chooser.getSelectedFile() == null) {
            return List.of();
        }
        return List.of(chooser.getSelectedFile().toPath());
    }

    /**
     * Makes AWT's dialog with the title and start, owned by the active frame or dialog.
     */
    private static java.awt.FileDialog awtDialog(final FileDialog dialog, final int mode) {
        final Window owner = SwingFileDialogs.owner();
        final String title = dialog.title().plain();
        final java.awt.FileDialog awt = owner instanceof final Dialog parent
                ? new java.awt.FileDialog(parent, title, mode)
                : new java.awt.FileDialog(owner instanceof final Frame frame ? frame : null, title, mode);
        final Path start = dialog.start();
        if (start != null) {
            if (Files.isDirectory(start)) {
                awt.setDirectory(start.toString());
            } else {
                final Path parent = start.toAbsolutePath().getParent();
                if (parent != null) {
                    awt.setDirectory(parent.toString());
                }
                if (start.getFileName() != null) {
                    awt.setFile(start.getFileName().toString());
                }
            }
        }
        return awt;
    }

    private static Window owner() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }

    private static boolean accepts(final FileDialog dialog, final String name) {
        try {
            final Path path = Path.of(name);
            return dialog.filters().stream().anyMatch(filter -> filter.accepts(path));
        } catch (final InvalidPathException e) {
            return false;
        }
    }
}
