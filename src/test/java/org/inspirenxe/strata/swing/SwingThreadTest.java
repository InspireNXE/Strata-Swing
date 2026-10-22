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

import org.inspirenxe.strata.api.ComponentFactory;
import org.inspirenxe.strata.api.input.Modifier;
import org.inspirenxe.strata.api.screen.ScreenHandle;
import org.inspirenxe.strata.api.text.TextStyle;
import org.inspirenxe.strata.api.theme.Themes;
import org.inspirenxe.strata.spi.Conventions;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Swing platform's UI thread, conventions, file dialogs and font ascent.
 */
class SwingThreadTest {

    @Test
    void thePanelsUiThreadIsTheEventThread() throws Exception {
        final StrataPanel panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 2f);
        final ScreenHandle[] handle = new ScreenHandle[1];
        SwingUtilities.invokeAndWait(() -> handle[0] = panel.screens().open(factory -> factory.anchorPanel()));
        final ComponentFactory factory = handle[0].factory();

        assertFalse(factory.uiThread().isCurrent(), "the test thread is not the UI thread");
        final boolean[] onEventThread = new boolean[1];
        SwingUtilities.invokeAndWait(() -> onEventThread[0] = factory.uiThread().isCurrent());
        assertTrue(onEventThread[0], "the event thread is");

        final CompletableFuture<Boolean> ran = new CompletableFuture<>();
        factory.uiThread().execute(() -> ran.complete(SwingUtilities.isEventDispatchThread()));
        assertTrue(ran.get(10, TimeUnit.SECONDS), "a task handed to the UI thread runs on the event thread");
    }

    @Test
    void shortcutsFollowTheOperatingSystem() {
        final Conventions conventions = new SwingPlatform(SwingTextMetrics.defaults()).conventions();
        final boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");

        assertEquals(mac ? Modifier.SUPER : Modifier.CONTROL, conventions.shortcutModifier());
        assertTrue(conventions.mnemonics());
        assertFalse(conventions.activateOnPress());
    }

    @Test
    void thereAreNoFileDialogsHeadless() {
        // Tests run headless
        assertTrue(new SwingPlatform(SwingTextMetrics.defaults()).fileDialogs().isEmpty());
    }

    @Test
    void ascentIsTheFontsOwn() {
        final SwingTextMetrics metrics = SwingTextMetrics.defaults();
        final AwtFont font = (AwtFont) metrics.font(TextStyle.DEFAULT);

        assertEquals(font.metrics(TextStyle.DEFAULT).getAscent(), metrics.ascent(TextStyle.DEFAULT), 0.001f);
    }

    @Test
    void anAscentPastTheLineIsCutToIt() {
        final BitmapFont tall = BitmapFont.builder(8f, 12f).build();
        final SwingTextMetrics metrics = new SwingTextMetrics(tall, tall);

        assertEquals(8f, metrics.ascent(TextStyle.DEFAULT));
    }
}
