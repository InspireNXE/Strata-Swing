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

import org.inspirenxe.strata.api.event.input.InputEvents;
import org.inspirenxe.strata.api.layout.Anchor;
import org.inspirenxe.strata.api.text.Text;
import org.inspirenxe.strata.api.theme.Themes;
import org.inspirenxe.strata.api.transfer.DataFormat;
import org.inspirenxe.strata.api.transfer.DropEffect;
import org.inspirenxe.strata.api.transfer.Transfer;
import org.inspirenxe.strata.api.visual.PixelBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Point;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clipboard's formats and drops from other applications, without a display: the clipboard is a private AWT
 * clipboard and the drops are handed to the panel as AWT's listener would hand them.
 */
@ExtendWith(OnEventThread.class)
class SwingTransferTest {
    private static final DataFormat<String> CSV = DataFormat.mime("text/csv");

    @Test
    void textAndCsvRoundTrip() {
        final SwingPlatform.SystemClipboard clipboard = new SwingPlatform.SystemClipboard(() -> new Clipboard("test"));
        final Clipboard awt = new Clipboard("test");
        final SwingPlatform.SystemClipboard shared = new SwingPlatform.SystemClipboard(() -> awt);
        shared.setTransfer(Transfer.builder().add(DataFormat.TEXT, "Ana\t12").add(CSV, "Ana,12").build());

        assertEquals(Optional.of("Ana\t12"), shared.get(), "get() reads the text");
        final Transfer read = shared.getTransfer().orElseThrow();
        assertEquals(Optional.of("Ana\t12"), read.get(DataFormat.TEXT));
        assertEquals(Optional.of("Ana,12"), read.get(CSV));
        assertTrue(clipboard.getTransfer().isEmpty(), "an empty clipboard has no transfer");
    }

    @Test
    void filesAndImagesRoundTrip() {
        final Clipboard awt = new Clipboard("test");
        final SwingPlatform.SystemClipboard clipboard = new SwingPlatform.SystemClipboard(() -> awt);
        final PixelBuffer pixels = PixelBuffer.of(2, 1).set(0, 0, 0xFFFF0000).set(1, 0, 0x800000FF).changed();
        final List<Path> files = List.of(Path.of("orders.csv").toAbsolutePath());
        clipboard.setTransfer(Transfer.builder().add(DataFormat.FILES, files).add(DataFormat.IMAGE, pixels).build());

        final Transfer read = clipboard.getTransfer().orElseThrow();
        assertEquals(Optional.of(files), read.get(DataFormat.FILES));
        final PixelBuffer copy = read.get(DataFormat.IMAGE).orElseThrow();
        assertEquals(0xFFFF0000, copy.get(0, 0));
        assertEquals(0x800000FF, copy.get(1, 0));
        assertFalse(read.has(DataFormat.TEXT), "no text was put there");
    }

    @Test
    void styledTextGoesOutAsPlainText() {
        final Clipboard awt = new Clipboard("test");
        final SwingPlatform.SystemClipboard clipboard = new SwingPlatform.SystemClipboard(() -> awt);
        clipboard.setTransfer(Transfer.builder().add(DataFormat.STYLED_TEXT, Text.of("Ben")).build());

        assertEquals(Optional.of("Ben"), clipboard.get());
    }

    @Test
    void textFromAnotherProgramReads() {
        final Clipboard awt = new Clipboard("test");
        final StringSelection selection = new StringSelection("Cleo");
        awt.setContents(selection, selection);

        final Transfer read = new SwingPlatform.SystemClipboard(() -> awt).getTransfer().orElseThrow();
        assertEquals(Optional.of("Cleo"), read.get(DataFormat.TEXT));
    }

    @Test
    void aDropReachesTheComponentThatAcceptsIt() {
        final StrataPanel panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 2f);
        panel.setBounds(0, 0, 400, 200);
        final List<String> dropped = new ArrayList<>();
        final List<DropEffect> effects = new ArrayList<>();
        panel.screens().push(factory -> factory.anchorPanel()
                .setAnchor(Anchor.FILL)
                .onEvent(InputEvents.DRAG_OVER, event -> event.accept())
                .onEvent(InputEvents.DROP, event -> {
                    dropped.add(event.transfer().get(DataFormat.FILES).orElseThrow().getFirst().getFileName()
                            .toString());
                    effects.add(event.effect().orElseThrow());
                }));
        final DataFlavor[] flavors = {DataFlavor.javaFileListFlavor};
        final SwingTransfers.FlavorReader reader = flavor -> {
            if (!flavor.equals(DataFlavor.javaFileListFlavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return List.of(new File("orders.csv"));
        };
        final int actions = DnDConstants.ACTION_COPY_OR_MOVE;

        assertEquals(Optional.of(DropEffect.MOVE), panel.dragEntered(new Point(40, 40), flavors, reader, actions,
                DnDConstants.ACTION_MOVE));
        assertEquals(Optional.of(DropEffect.COPY), panel.dragMoved(new Point(42, 40), actions,
                DnDConstants.ACTION_COPY), "Control asks for a copy");
        final Optional<DropEffect> taken = panel.dropped(new Point(42, 40), flavors, reader, actions,
                DnDConstants.ACTION_COPY);

        assertEquals(Optional.of(DropEffect.COPY), taken);
        assertEquals(List.of("orders.csv"), dropped);
        assertEquals(List.of(DropEffect.COPY), effects);
    }

    @Test
    void aDropNothingTakesIsRefused() {
        final StrataPanel panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 2f);
        panel.setBounds(0, 0, 400, 200);
        panel.screens().push(factory -> factory.anchorPanel().setAnchor(Anchor.FILL));
        final DataFlavor[] flavors = {DataFlavor.stringFlavor};
        final SwingTransfers.FlavorReader reader = flavor -> "Dev";

        assertTrue(panel.dragEntered(new Point(10, 10), flavors, reader, DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY).isEmpty());
        assertTrue(panel.dropped(new Point(10, 10), flavors, reader, DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY).isEmpty());
    }
}
