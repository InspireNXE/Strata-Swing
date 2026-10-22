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
import org.inspirenxe.strata.api.component.Component;
import org.inspirenxe.strata.api.geometry.Size;
import org.inspirenxe.strata.api.screen.Screen;
import org.inspirenxe.strata.api.screen.ScreenHandle;
import org.inspirenxe.strata.api.text.Text;
import org.inspirenxe.strata.api.theme.Themes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The panel's preferred size, and what it does for the window it runs.
 */
@ExtendWith(OnEventThread.class)
class StrataPanelWindowTest {
    private StrataPanel panel;

    @BeforeEach
    void createPanel() {
        this.panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 2f);
    }

    @Test
    void anEmptyPanelAsksFor960By600() {
        assertEquals(new Dimension(960, 600), this.panel.getPreferredSize());
    }

    @Test
    void theContentDecidesThePreferredSize() {
        this.panel.screens().push(factory -> factory.button("A wide caption for the button"));
        final Size size = this.panel.screens().preferredSize(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

        final Dimension preferred = this.panel.getPreferredSize();

        assertEquals((int) Math.ceil(size.width() * 2f), preferred.width);
        assertEquals((int) Math.ceil(size.height() * 2f), preferred.height);
        assertTrue(preferred.width > 0 && preferred.height > 0);
    }

    @Test
    void aSetPreferredSizeWins() {
        this.panel.screens().push(factory -> factory.button("Go"));
        this.panel.setPreferredSize(new Dimension(300, 200));

        assertEquals(new Dimension(300, 200), this.panel.getPreferredSize());
    }

    @Test
    void closingTheWindowAsksTheScreens() {
        final boolean[] agree = {false};
        this.panel.screens().push(new Screen() {
            @Override
            public Component<?> build(final ComponentFactory factory) {
                return factory.anchorPanel();
            }

            @Override
            public boolean closeRequested(final ScreenHandle handle) {
                return agree[0];
            }
        });

        assertFalse(this.panel.closeWindow(), "the screen refused");
        assertFalse(this.panel.screens().isEmpty());
        agree[0] = true;
        assertTrue(this.panel.closeWindow(), "the screen agreed");
        assertTrue(this.panel.screens().isEmpty());
    }

    @Test
    void theTitleFollowsTheTopScreen() {
        assertEquals("", this.panel.shownTitle());
        final ScreenHandle handle = this.panel.screens().open(new Screen() {
            @Override
            public Component<?> build(final ComponentFactory factory) {
                return factory.anchorPanel();
            }

            @Override
            public Text title() {
                return Text.of("Orders");
            }
        });
        assertEquals("Orders", this.panel.shownTitle());

        handle.setTitle(Text.of("Orders for Ana"));
        assertEquals("Orders for Ana", this.panel.shownTitle());
    }
}
