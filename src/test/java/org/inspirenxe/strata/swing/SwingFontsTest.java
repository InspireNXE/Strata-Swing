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

import org.inspirenxe.strata.api.control.display.Label;
import org.inspirenxe.strata.api.event.Subscription;
import org.inspirenxe.strata.api.geometry.Rect;
import org.inspirenxe.strata.api.text.FontId;
import org.inspirenxe.strata.api.text.TextSpan;
import org.inspirenxe.strata.api.text.TextStyle;
import org.inspirenxe.strata.api.text.TextStylePatch;
import org.inspirenxe.strata.api.theme.Paint;
import org.inspirenxe.strata.api.theme.Themes;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registered families, text sizes and the platform's timers.
 */
class SwingFontsTest {
    private static final FontId TITLE = FontId.of("app", "title");
    private static final int BACKGROUND = 0xFF1E1F22;

    private final SwingTextMetrics metrics = SwingTextMetrics.defaults();

    private float width(final TextStyle style) {
        return this.metrics.width(new TextSpan("Strata", style));
    }

    @Test
    void aSizeScalesTheNormalFont() {
        assertEquals(9f, this.metrics.defaultSize());
        assertEquals(this.width(TextStyle.DEFAULT), this.width(TextStyle.DEFAULT.withSize(9f)), 0.001f);
        assertEquals(this.width(TextStyle.DEFAULT) * 2f, this.width(TextStyle.DEFAULT.withSize(18f)), 0.01f);
        assertEquals(this.metrics.lineHeight(TextStyle.DEFAULT) * 2f,
                this.metrics.lineHeight(TextStyle.DEFAULT.withSize(18f)), 0.01f);
    }

    @Test
    void aRegisteredFamilyIsChosenByIdAndScaledToTheDefaultSize() {
        final AwtFont serif = new AwtFont(new Font(Font.SERIF, Font.PLAIN, 1).deriveFont(1f));
        this.metrics.register(TITLE, serif);
        final TextStyle title = TextStyle.DEFAULT.withFont(TITLE).withMonospace(true);
        assertSame(serif, this.metrics.font(title), "a named family wins over monospace");
        assertEquals(serif.width(new TextSpan("Strata", title)) * 9f, this.width(title), 0.05f);
        assertNotEquals(this.metrics.font(TextStyle.DEFAULT.withMonospace(true)),
                this.metrics.font(TextStyle.DEFAULT), "monospace still picks its font without a family");
        assertSame(this.metrics.font(TextStyle.DEFAULT),
                this.metrics.font(TextStyle.DEFAULT.withFont(FontId.of("app", "missing"))), "a missing family");
    }

    @Test
    void registeringChangesTheVersion() {
        final long start = this.metrics.version();
        final Font serif = new Font(Font.SERIF, Font.PLAIN, 9);
        assertEquals(Optional.empty(), this.metrics.register(TITLE, serif));
        assertNotEquals(start, this.metrics.version());
        final long registered = this.metrics.version();
        assertTrue(this.metrics.unregister(TITLE).isPresent());
        assertNotEquals(registered, this.metrics.version());
        assertEquals(Optional.empty(), this.metrics.registered(TITLE));
    }

    @Test
    void textOfTwiceTheSizeDrawsTwiceAsTall() {
        final Label[] labels = new Label[2];
        final BufferedImage image = SwingSnapshot.render(factory -> {
            labels[0] = factory.label("Hello");
            labels[1] = factory.label("Hello");
            return factory.absolutePanel().setBackground(Paint.solid(BACKGROUND))
                    .add(labels[0], 10, 10)
                    .add(factory.stackPanel().setTextStyle(TextStylePatch.of().size(18f)).add(labels[1]), 10, 60);
        }, Themes.flat(), 360, 240, 2f);
        final int small = inkHeight(image, labels[0].bounds());
        final int large = inkHeight(image, labels[1].bounds());
        assertTrue(small > 0, "the small label drew");
        assertEquals(small * 2, large, 3, "glyphs twice as tall: " + small + " and " + large);
    }

    @Test
    void thePlatformsTimersRunTasksOnTheEventThreadUntilClosed() throws InterruptedException {
        final SwingPlatform platform = new SwingPlatform(this.metrics);
        final CountDownLatch runs = new CountDownLatch(2);
        final AtomicBoolean offThread = new AtomicBoolean();
        final Subscription subscription = platform.scheduler().every(10L, () -> {
            offThread.compareAndSet(false, !SwingUtilities.isEventDispatchThread());
            runs.countDown();
        });
        assertTrue(runs.await(5, TimeUnit.SECONDS), "the task ran twice");
        subscription.close();
        subscription.close();
        assertFalse(offThread.get(), "every run was on the event thread");
    }

    private static int inkHeight(final BufferedImage image, final Rect bounds) {
        int rows = 0;
        final int x0 = Math.round(bounds.x() * 2f);
        final int x1 = Math.min(image.getWidth(), Math.round((bounds.x() + bounds.width()) * 2f));
        final int y0 = Math.round(bounds.y() * 2f);
        final int y1 = Math.min(image.getHeight(), Math.round((bounds.y() + bounds.height()) * 2f));
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (image.getRGB(x, y) != BACKGROUND) {
                    rows++;
                    break;
                }
            }
        }
        return rows;
    }
}
