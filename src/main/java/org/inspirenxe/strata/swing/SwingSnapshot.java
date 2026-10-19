package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.screen.Screen;
import org.inspirenxe.strata.api.theme.Theme;
import org.inspirenxe.strata.core.StrataCore;
import org.inspirenxe.strata.spi.ScreenStack;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Renders one frame of a screen to an image, with no window or display. Tests and the sandbox's {@code --snapshot}
 * option use it to check layouts on headless machines.
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
     * @param scale pixels per UI unit, the screen gets {@code width / scale} by
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
     * tooltip. Everything else is as in {@link #render(Screen, Theme, int, int, float)}.
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
        final SwingPlatform platform = new SwingPlatform(SwingTextMetrics.defaults());
        final ScreenStack screens = StrataCore.createScreenStack(platform, theme);
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
