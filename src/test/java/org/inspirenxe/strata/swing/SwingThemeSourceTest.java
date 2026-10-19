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

import org.inspirenxe.strata.api.theme.ThemeId;
import org.inspirenxe.strata.api.theme.Themes;
import org.inspirenxe.strata.spi.ThemeDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingThemeSourceTest {
    private static final ThemeId ID = ThemeId.of("mod", "shop");

    private static void write(final Path root, final String content) throws IOException {
        final Path file = root.resolve("assets/mod/themes/shop.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void earlierClassPathEntriesComeLast(@TempDir final Path first, @TempDir final Path second) throws IOException {
        write(first, "first");
        write(second, "second");
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {first.toUri().toURL(), second.toUri().toURL()}, null)) {
            final List<ThemeDocument> documents = new SwingThemeSource(loader).documents(ID);
            assertEquals(List.of("second", "first"), documents.stream().map(ThemeDocument::content).toList());
        }
    }

    @Test
    void reloadReadsTheFilesAgain(@TempDir final Path root) throws IOException {
        write(root, "old");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
            final SwingThemeSource source = new SwingThemeSource(loader);
            assertEquals("old", source.documents(ID).getFirst().content());
            write(root, "new");
            assertEquals("old", source.documents(ID).getFirst().content());
            final long version = source.version();
            source.reload();
            assertNotEquals(version, source.version());
            assertEquals("new", source.documents(ID).getFirst().content());
        }
    }

    @Test
    void registeredThemesAreBases(@TempDir final Path root) throws IOException {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
            final SwingThemeSource source = new SwingThemeSource(loader);
            assertTrue(source.base(ID).isEmpty());
            assertTrue(source.documents(ID).isEmpty());
            source.register(ID, Themes.flat());
            assertTrue(source.base(ID).isPresent());
        }
    }
}
