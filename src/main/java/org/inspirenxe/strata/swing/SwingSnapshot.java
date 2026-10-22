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

import org.inspirenxe.strata.api.screen.Screen;
import org.inspirenxe.strata.api.theme.Theme;
import org.inspirenxe.strata.api.theme.ThemeRef;
import org.inspirenxe.strata.core.StrataCore;
import org.inspirenxe.strata.spi.ScreenStack;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Renders one frame of a screen to an image, with no window or display. Tests and the gallery's {@code --snapshot}
 * option use it to check layouts on headless machines.
 *
 * <p>The screen runs on the calling thread, which need not be the Swing event thread: each snapshot's stack takes the
 * thread that drives it as its UI thread, so a snapshot can be taken from a test or a command line tool's main
 * thread.</p>
 */
public final class SwingSnapshot {

    private SwingSnapshot() {
    }

    /**
     * Opens a screen on a fresh screen stack, draws one frame, and closes it again. Text uses
     * {@link SwingTextMetrics#defaults()}, and sprites load from this class's class loader. Any pixel the screen
     * does not draw stays transparent. The screen's {@code closed} callback runs before this method returns.
     *
     * @param screen the screen to render
     * @param theme the theme to render it with
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param scale pixels per UI unit; the screen gets {@code width / scale} by
     *     {@code height / scale} units
     * @return a new ARGB image of the frame
     * @throws org.inspirenxe.strata.api.theme.ThemeException if the theme lacks a style key the screen's tree
     *     requires
     */
    public static BufferedImage render(final Screen screen, final Theme theme, final int width, final int height,
                                       final float scale) {
        return render(screen, theme, width, height, scale, screens -> { });
    }

    /**
     * Opens a screen on a fresh screen stack, feeds it input, draws one frame, and closes it again. Use it to
     * picture a screen in a state that takes input to reach, such as with a drop-down open or the pointer over a
     * tooltip. Tooltips show without the usual delay. Everything else is as in
     * {@link #render(Screen, Theme, int, int, float)}.
     *
     * @param screen the screen to render
     * @param theme the theme to render it with
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param scale pixels per UI unit
     * @param input sends input to the stack, in UI units, after the screen opens and before the frame is drawn
     * @return a new ARGB image of the frame
     * @throws org.inspirenxe.strata.api.theme.ThemeException if the theme lacks a style key the screen's tree
     *     requires
     */
    public static BufferedImage render(final Screen screen, final Theme theme, final int width, final int height,
                                       final float scale, final Consumer<ScreenStack> input) {
        return render(screen, theme, width, height, scale, new SwingSprites(SwingSnapshot.class.getClassLoader()),
            input);
    }

    /**
     * Opens a screen on a fresh screen stack, feeds it input, draws one frame with the given sprites, and closes it
     * again. Everything else is as in {@link #render(Screen, Theme, int, int, float, Consumer)}.
     *
     * @param screen the screen to render
     * @param theme the theme to render it with
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param scale pixels per UI unit
     * @param sprites where sprite images come from
     * @param input sends input to the stack, in UI units, after the screen opens and before the frame is drawn
     * @return a new ARGB image of the frame
     * @throws org.inspirenxe.strata.api.theme.ThemeException if the theme lacks a style key the screen's tree
     *     requires
     */
    public static BufferedImage render(final Screen screen, final Theme theme, final int width, final int height,
                                       final float scale, final SwingSprites sprites,
                                       final Consumer<ScreenStack> input) {
        return render(new SwingPlatform(SwingTextMetrics.defaults()), screen, theme, width, height, scale, sprites,
                input);
    }

    /**
     * Opens a screen on a fresh screen stack of a given platform, feeds it input, draws one frame, and closes it
     * again. Text is measured and drawn with the platform's text metrics. Everything else is as in
     * {@link #render(Screen, Theme, int, int, float, SwingSprites, Consumer)}.
     *
     * @param platform the platform, for its fonts, theme source and the rest
     * @param screen the screen to render
     * @param theme the theme to render it with
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param scale pixels per UI unit
     * @param sprites where sprite images come from
     * @param input sends input to the stack, in UI units, after the screen opens and before the frame is drawn
     * @return a new ARGB image of the frame
     * @throws org.inspirenxe.strata.api.theme.ThemeException if the theme lacks a style key the screen's tree
     *     requires
     */
    public static BufferedImage render(final SwingPlatform platform, final Screen screen, final Theme theme,
                                       final int width, final int height, final float scale,
                                       final SwingSprites sprites, final Consumer<ScreenStack> input) {
        return draw(platform, StrataCore.createScreenStack(offscreen(platform), theme), screen, width, height, scale,
                sprites, input);
    }

    /**
     * Opens a screen on a fresh screen stack whose theme is resolved from the platform's theme source, feeds it
     * input, draws one frame, and closes it again, so a snapshot shows what theme files make of a theme. Everything
     * else is as in {@link #render(SwingPlatform, Screen, Theme, int, int, float, SwingSprites, Consumer)}.
     *
     * @param platform the platform, for its fonts, theme source and the rest
     * @param screen the screen to render
     * @param theme the theme to resolve
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param scale pixels per UI unit
     * @param sprites where sprite images come from
     * @param input sends input to the stack, in UI units, after the screen opens and before the frame is drawn
     * @return a new ARGB image of the frame
     * @throws org.inspirenxe.strata.api.theme.ThemeException if the theme cannot be resolved or lacks a style key
     *     the screen's tree requires
     */
    public static BufferedImage render(final SwingPlatform platform, final Screen screen, final ThemeRef theme,
                                       final int width, final int height, final float scale,
                                       final SwingSprites sprites, final Consumer<ScreenStack> input) {
        return draw(platform, StrataCore.createScreenStack(offscreen(platform), theme), screen, width, height, scale,
                sprites, input);
    }

    /**
     * Returns the platform a snapshot's stack runs with: the given one, on whichever thread drives the stack.
     */
    private static HostPlatform offscreen(final SwingPlatform platform) {
        return new HostPlatform(platform, platform.textInput(), false);
    }

    private static BufferedImage draw(final SwingPlatform platform, final ScreenStack screens, final Screen screen,
                                      final int width, final int height, final float scale,
                                      final SwingSprites sprites, final Consumer<ScreenStack> input) {
        // One frame is drawn, so a tooltip the input reaches shows without waiting
        screens.setTooltipDelay(0L);
        screens.resize(width / scale, height / scale);
        screens.push(screen);
        input.accept(screens);

        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.scale(scale, scale);
            final SwingCanvas canvas = new SwingCanvas(platform.textMetrics(), sprites);
            canvas.begin(g);
            screens.render(canvas);
        } finally {
            g.dispose();
            screens.clear();
        }
        return image;
    }
}
