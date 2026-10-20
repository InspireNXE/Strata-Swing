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
 * {@link SpriteResolver#DEFAULT} looks for sprites, or wherever a {@link ThemePathResolver} says. Every class path entry that has the file contributes one
 * document. An entry earlier on the class path has higher priority, as it does for any other resource.
 *
 * <p>Themes built in code can be registered with {@link #register}. Files are read once per id and cached until
 * {@link #reload()}, which also tells open hosts to resolve their themes again. A file that cannot be read is
 * left out.</p>
 */
public final class SwingThemeSource implements ThemeSource {
    private final ClassLoader loader;
    private final ThemePathResolver resolver;
    private final Map<ThemeId, Theme> bases;
    private final Map<ThemeId, List<ThemeDocument>> cache;
    private final AtomicLong version;

    /**
     * Creates a source.
     *
     * @param loader the class loader to find theme files with
     */
    public SwingThemeSource(final ClassLoader loader) {
        this(loader, ThemePathResolver.DEFAULT);
    }

    /**
     * Creates a source that finds theme files where a resolver says.
     *
     * @param loader the class loader to find theme files with
     * @param resolver where each theme's files live on the class path
     */
    public SwingThemeSource(final ClassLoader loader, final ThemePathResolver resolver) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
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
        final String name = this.resolver.resourcePath(id);
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
