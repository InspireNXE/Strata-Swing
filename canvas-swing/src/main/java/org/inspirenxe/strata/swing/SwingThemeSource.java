package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.theme.Theme;
import org.inspirenxe.strata.api.theme.ThemeId;
import org.inspirenxe.strata.spi.ThemeDocument;
import org.inspirenxe.strata.spi.ThemeSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Finds theme files on the class path at {@code assets/<namespace>/themes/<path>.json}, next to where
 * {@link SpriteResolver#DEFAULT} looks for sprites. Every class path entry that has the file contributes one
 * document. An entry earlier on the class path has higher priority, as it does for any other resource.
 *
 * <p>Themes built in code can be registered with {@link #register}. Files are read once per id and cached until
 * {@link #reload()}, which also tells open hosts to resolve their themes again. A file that cannot be read is
 * left out.</p>
 */
public final class SwingThemeSource implements ThemeSource {
    private final ClassLoader loader;
    private final Map<ThemeId, Theme> bases;
    private final Map<ThemeId, List<ThemeDocument>> cache;
    private final AtomicLong version;

    /**
     * Creates a source.
     *
     * @param loader the class loader to find theme files with
     */
    public SwingThemeSource(final ClassLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.bases = new HashMap<>();
        this.cache = new HashMap<>();
        this.version = new AtomicLong();
    }

    /**
     * Registers a theme built in code under an id, replacing any earlier one, and reloads.
     *
     * @param id the id theme refs and theme files use
     * @param theme the theme
     */
    public synchronized void register(final ThemeId id, final Theme theme) {
        this.bases.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(theme, "theme"));
        this.reload();
    }

    /**
     * Drops the cached files and changes {@link #version()}, so open hosts read the files again on their next
     * layout or render. Call it after theme files change, for example while editing a theme.
     */
    public synchronized void reload() {
        this.cache.clear();
        this.version.incrementAndGet();
    }

    @Override
    public synchronized Optional<Theme> base(final ThemeId id) {
        return Optional.ofNullable(this.bases.get(id));
    }

    @Override
    public synchronized List<ThemeDocument> documents(final ThemeId id) {
        return this.cache.computeIfAbsent(id, this::read);
    }

    @Override
    public long version() {
        return this.version.get();
    }

    private List<ThemeDocument> read(final ThemeId id) {
        final String name = "assets/" + id.namespace() + "/themes/" + id.path() + ".json";
        final List<ThemeDocument> documents = new ArrayList<>();
        try {
            final Enumeration<URL> urls = this.loader.getResources(name);
            while (urls.hasMoreElements()) {
                final URL url = urls.nextElement();
                try (InputStream in = url.openStream()) {
                    documents.add(new ThemeDocument(url.toString(), new String(in.readAllBytes(),
                            StandardCharsets.UTF_8)));
                } catch (final IOException ignored) {
                    // An unreadable file is left out, as the ThemeSource contract asks
                }
            }
        } catch (final IOException ignored) {
            // No files can be listed; resolve with none
        }
        // The class path lists the highest priority first; documents want it last
        Collections.reverse(documents);
        return List.copyOf(documents);
    }
}
