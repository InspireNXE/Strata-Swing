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

import org.inspirenxe.strata.api.text.Text;
import org.inspirenxe.strata.api.transfer.DataFormat;
import org.inspirenxe.strata.api.transfer.DropEffect;
import org.inspirenxe.strata.api.transfer.Transfer;
import org.inspirenxe.strata.api.visual.PixelBuffer;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Turns Strata's transfers into AWT's and back, for the clipboard and for drops.
 *
 * <ul>
 *     <li>{@link DataFormat#TEXT} is {@link DataFlavor#stringFlavor}, and {@link DataFormat#STYLED_TEXT} goes out
 *     as its plain text when there is no plain text.</li>
 *     <li>{@link DataFormat#FILES} is {@link DataFlavor#javaFileListFlavor}.</li>
 *     <li>{@link DataFormat#IMAGE} is {@link DataFlavor#imageFlavor}, an ARGB image holding a copy of the pixels.</li>
 *     <li>Every other {@linkplain DataFormat#mime MIME format} is a text flavor of that MIME type whose value is a
 *     {@code String}, such as {@code text/csv;class=java.lang.String}.</li>
 * </ul>
 */
final class SwingTransfers {

    private SwingTransfers() {
    }

    /**
     * Reads one flavor of what AWT holds.
     */
    @FunctionalInterface
    interface FlavorReader {
        /**
         * Reads a flavor.
         *
         * @param flavor the flavor
         * @return its value
         * @throws UnsupportedFlavorException if it is not offered
         * @throws IOException if it cannot be read now
         */
        Object read(DataFlavor flavor) throws UnsupportedFlavorException, IOException;
    }

    /**
     * Returns a transferable that offers a transfer in every flavor AWT can hold, and that keeps no clipboard
     * ownership. Local formats are left out.
     *
     * @param transfer the transfer
     * @return the transferable, offering nothing for a transfer AWT can hold none of
     */
    static Selection transferable(final Transfer transfer) {
        final Map<DataFlavor, Object> values = new LinkedHashMap<>();
        final Optional<String> text = transfer.get(DataFormat.TEXT)
                .or(() -> transfer.get(DataFormat.STYLED_TEXT).map(Text::plain));
        text.ifPresent(value -> values.put(DataFlavor.stringFlavor, value));
        transfer.get(DataFormat.FILES).ifPresent(files -> values.put(DataFlavor.javaFileListFlavor,
                files.stream().map(Path::toFile).toList()));
        transfer.get(DataFormat.IMAGE).ifPresent(pixels -> values.put(DataFlavor.imageFlavor,
                SwingTransfers.image(pixels)));
        for (final DataFormat<?> format : transfer.formats()) {
            if (format.isLocal() || format == DataFormat.TEXT || format.mimeType().isEmpty()) {
                continue;
            }
            final DataFlavor flavor = SwingTransfers.textFlavor(format.mimeType().get());
            if (flavor != null) {
                transfer.get(format).ifPresent(value -> values.put(flavor, value));
            }
        }
        return new Selection(values);
    }

    /**
     * Builds a transfer from the flavors AWT offers.
     *
     * @param flavors the flavors offered
     * @param reader reads a flavor
     * @param eager true to read every value now, as a drop must, false to read each when it is first asked for
     * @return the transfer, empty if no flavor maps to a format
     */
    static Transfer transfer(final DataFlavor[] flavors, final FlavorReader reader, final boolean eager) {
        final Transfer.Builder builder = Transfer.builder();
        final Set<DataFormat<?>> seen = new HashSet<>();
        for (final DataFlavor flavor : flavors) {
            if (flavor.equals(DataFlavor.stringFlavor)
                    || (SwingTransfers.isText(flavor) && "plain".equals(flavor.getSubType()))) {
                SwingTransfers.add(builder, seen, DataFormat.TEXT, eager,
                        () -> SwingTransfers.read(reader, DataFlavor.stringFlavor, String.class::cast));
            } else if (flavor.equals(DataFlavor.javaFileListFlavor)) {
                SwingTransfers.add(builder, seen, DataFormat.FILES, eager,
                        () -> SwingTransfers.read(reader, flavor, SwingTransfers::paths));
            } else if (flavor.equals(DataFlavor.imageFlavor)) {
                SwingTransfers.add(builder, seen, DataFormat.IMAGE, eager,
                        () -> SwingTransfers.read(reader, flavor, value -> SwingTransfers.pixels((Image) value)));
            } else if (SwingTransfers.isText(flavor)) {
                final DataFormat<String> format;
                try {
                    format = DataFormat.mime(flavor.getPrimaryType() + "/" + flavor.getSubType());
                } catch (final IllegalArgumentException e) {
                    continue;
                }
                SwingTransfers.add(builder, seen, format, eager,
                        () -> SwingTransfers.read(reader, flavor, String.class::cast));
            }
        }
        return builder.build();
    }

    /**
     * Returns the effects a drag's source allows.
     *
     * @param actions AWT's drop actions
     * @return the effects, possibly empty
     */
    static Set<DropEffect> effects(final int actions) {
        final Set<DropEffect> effects = EnumSet.noneOf(DropEffect.class);
        if ((actions & DnDConstants.ACTION_MOVE) != 0) {
            effects.add(DropEffect.MOVE);
        }
        if ((actions & DnDConstants.ACTION_COPY) != 0) {
            effects.add(DropEffect.COPY);
        }
        if ((actions & DnDConstants.ACTION_LINK) != 0) {
            effects.add(DropEffect.LINK);
        }
        return effects;
    }

    /**
     * Returns AWT's drop action for an effect.
     *
     * @param effect the effect
     * @return the action
     */
    static int action(final DropEffect effect) {
        return switch (effect) {
            case COPY -> DnDConstants.ACTION_COPY;
            case MOVE -> DnDConstants.ACTION_MOVE;
            case LINK -> DnDConstants.ACTION_LINK;
        };
    }

    /**
     * Copies pixels into a new ARGB image.
     *
     * @param pixels the pixels
     * @return the image
     */
    static BufferedImage image(final PixelBuffer pixels) {
        final BufferedImage image = new BufferedImage(pixels.width(), pixels.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, pixels.width(), pixels.height(), pixels.array(), 0, pixels.width());
        return image;
    }

    /**
     * Copies an image's pixels into a new buffer.
     *
     * @param image the image
     * @return the pixels, or {@code null} if the image has no size yet or is too large
     */
    static PixelBuffer pixels(final Image image) {
        final int width = image.getWidth(null);
        final int height = image.getHeight(null);
        if (width <= 0 || height <= 0 || (long) width * height > PixelBuffer.MAX_PIXELS) {
            return null;
        }
        final BufferedImage argb;
        if (image instanceof final BufferedImage buffered && buffered.getType() == BufferedImage.TYPE_INT_ARGB) {
            argb = buffered;
        } else {
            argb = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = argb.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
        final PixelBuffer pixels = PixelBuffer.of(width, height);
        argb.getRGB(0, 0, width, height, pixels.array(), 0, width);
        return pixels.changed();
    }

    private static <T> void add(final Transfer.Builder builder, final Set<DataFormat<?>> seen,
                                final DataFormat<T> format, final boolean eager,
                                final Supplier<T> value) {
        if (!seen.add(format)) {
            return;
        }
        if (!eager) {
            builder.supply(format, value);
            return;
        }
        final T read = value.get();
        if (read != null) {
            builder.add(format, read);
        }
    }

    private static <T> T read(final FlavorReader reader, final DataFlavor flavor, final Function<Object, T> convert) {
        try {
            final Object value = reader.read(flavor);
            return value == null ? null : convert.apply(value);
        } catch (final UnsupportedFlavorException | IOException | RuntimeException e) {
            // AWT may refuse to read a drag's data before the drop, and another program may hold the clipboard
            return null;
        }
    }

    private static List<Path> paths(final Object value) {
        final List<Path> paths = new ArrayList<>();
        for (final Object file : (List<?>) value) {
            if (file instanceof final File f) {
                paths.add(f.toPath());
            }
        }
        return paths.isEmpty() ? null : paths;
    }

    private static boolean isText(final DataFlavor flavor) {
        return "text".equals(flavor.getPrimaryType()) && flavor.getRepresentationClass() == String.class;
    }

    private static DataFlavor textFlavor(final String mimeType) {
        try {
            return new DataFlavor(mimeType.toLowerCase(Locale.ROOT) + ";class=java.lang.String");
        } catch (final ClassNotFoundException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * A transferable over fixed values, which gives up clipboard ownership quietly.
     */
    static final class Selection implements Transferable, ClipboardOwner {
        private final Map<DataFlavor, Object> values;

        Selection(final Map<DataFlavor, Object> values) {
            this.values = values;
        }

        /**
         * Returns whether there is anything to offer.
         *
         * @return true if no flavor is offered
         */
        boolean isEmpty() {
            return this.values.isEmpty();
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return this.values.keySet().toArray(new DataFlavor[0]);
        }

        @Override
        public boolean isDataFlavorSupported(final DataFlavor flavor) {
            return this.values.containsKey(flavor);
        }

        @Override
        public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException {
            final Object value = this.values.get(flavor);
            if (value == null) {
                throw new UnsupportedFlavorException(flavor);
            }
            return value;
        }

        @Override
        public void lostOwnership(final java.awt.datatransfer.Clipboard clipboard, final Transferable contents) {
            // Nothing to let go of
        }
    }
}
