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

import org.inspirenxe.strata.api.component.Component;
import org.inspirenxe.strata.api.control.field.TextBox;
import org.inspirenxe.strata.api.state.State;
import org.inspirenxe.strata.api.theme.Themes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The panel paints on demand: when its screens need a frame, and at their next timed change, at most at their frame
 * rate.
 */
class StrataPanelFrameTest {
    private StrataPanel panel;
    private CountingRepaints repaints;
    private RepaintManager previous;

    @BeforeEach
    void createPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            this.panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 2f);
            this.panel.setBounds(0, 0, 400, 200);
            this.previous = RepaintManager.currentManager(this.panel);
            this.repaints = new CountingRepaints(this.panel);
            RepaintManager.setCurrentManager(this.repaints);
        });
    }

    @AfterEach
    void restoreRepaints() throws Exception {
        SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(this.previous));
    }

    private void paint() {
        final BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        try {
            this.panel.paintComponent(g);
        } finally {
            g.dispose();
        }
    }

    @Test
    void aStateSetOnAnotherThreadAsksForAFrame() throws Exception {
        final State<Boolean> enabled = State.of(true);
        SwingUtilities.invokeAndWait(() -> {
            this.panel.screens().push(factory -> factory.absolutePanel()
                    .add(factory.button("Save").bind(Component.ENABLED, enabled)));
            this.paint();
        });
        final int before = this.repaints.count.get();

        final Thread worker = new Thread(() -> enabled.set(false));
        worker.start();
        worker.join();
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(this.repaints.count.get() > before || this.panel.isFrameTimerRunning(),
                    "the requester repainted the panel, or waits for the frame rate's interval");
            if (this.panel.isFrameTimerRunning()) {
                final int delay = this.panel.frameTimerDelay();
                assertTrue(delay > 0 && delay <= 34, "at most one interval at 30 frames a second: " + delay);
            }
        });
    }

    @Test
    void aFrameNeededLongAfterTheLastPaintRepaintsAtOnce() throws Exception {
        final State<Boolean> enabled = State.of(true);
        SwingUtilities.invokeAndWait(() -> {
            this.panel.screens().push(factory -> factory.absolutePanel()
                    .add(factory.button("Save").bind(Component.ENABLED, enabled)));
            this.paint();
        });
        Thread.sleep(50L);
        final int before = this.repaints.count.get();
        enabled.set(false);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(this.repaints.count.get() > before, "more than an interval has passed");
            assertFalse(this.panel.isFrameTimerRunning());
        });
    }

    @Test
    void aHigherRateWaitsLess() throws Exception {
        final State<Boolean> enabled = State.of(true);
        SwingUtilities.invokeAndWait(() -> {
            this.panel.screens().setFrameRate(200);
            this.panel.screens().push(factory -> factory.absolutePanel()
                    .add(factory.button("Save").bind(Component.ENABLED, enabled)));
            this.paint();
            enabled.set(false);
        });
        SwingUtilities.invokeAndWait(() -> {
            if (this.panel.isFrameTimerRunning()) {
                assertTrue(this.panel.frameTimerDelay() <= 5, "one interval at 200 a second");
            }
        });
    }

    @Test
    void nothingTimedLeavesNoTimerRunning() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            this.panel.screens().push(factory -> factory.absolutePanel().add(factory.label("Ana")));
            this.paint();
            assertFalse(this.panel.isFrameTimerRunning());
        });
    }

    @Test
    void aFocusedTextBoxRepaintsAtItsCaretsBlink() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final TextBox[] box = new TextBox[1];
            this.panel.screens().push(factory -> {
                box[0] = factory.textBox();
                return factory.absolutePanel().add(box[0]);
            });
            this.paint();
            box[0].requestFocus();
            this.paint();

            assertTrue(this.panel.isFrameTimerRunning(), "the caret blinks");
            final int delay = this.panel.frameTimerDelay();
            assertTrue(delay > 0 && delay <= 500, "the next blink is at most half a second away: " + delay);
        });
    }

    @Test
    void paintingStopsTheTimerOnceNothingIsTimed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final TextBox[] box = new TextBox[1];
            this.panel.screens().push(factory -> {
                box[0] = factory.textBox();
                return factory.absolutePanel().add(box[0]);
            });
            this.paint();
            box[0].requestFocus();
            this.paint();
            this.panel.screens().clear();
            this.paint();

            assertFalse(this.panel.isFrameTimerRunning());
        });
    }

    /**
     * Counts the repaints one component asks for.
     */
    private static final class CountingRepaints extends RepaintManager {
        private final AtomicInteger count = new AtomicInteger();
        private final JComponent target;

        CountingRepaints(final JComponent target) {
            this.target = target;
        }

        @Override
        public void addDirtyRegion(final JComponent component, final int x, final int y, final int w, final int h) {
            if (component == this.target) {
                this.count.incrementAndGet();
            }
            super.addDirtyRegion(component, x, y, w, h);
        }
    }
}
