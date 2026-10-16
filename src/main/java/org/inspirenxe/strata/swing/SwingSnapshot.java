package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.screen.Screen;
import org.inspirenxe.strata.api.theme.Theme;
import org.inspirenxe.strata.core.StrataCore;
import org.inspirenxe.strata.spi.ScreenStack;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Renders one frame of a screen to an image, with no window or display. Useful for checking layouts in tests and
 * on headless machines.
 */
public final class SwingSnapshot {

    private SwingSnapshot() {
    }

    /**
     * @param width image width in pixels
     * @param height image height in pixels
     * @param scale pixels per UI unit, like Minecraft's GUI scale
     */
    public static BufferedImage render(final Screen screen, final Theme theme, final int width, final int height,
                                       final float scale) {
        final SwingPlatform platform = new SwingPlatform(SwingFonts.defaults());
        final ScreenStack screens = StrataCore.createScreenStack(platform, theme);
        screens.resize(width / scale, height / scale);
        screens.push(screen);

        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.scale(scale, scale);
            final SwingCanvas canvas = new SwingCanvas(platform.fonts(), new SwingSprites(SwingSnapshot.class.getClassLoader()));
            canvas.begin(g);
            screens.render(canvas);
        } finally {
            g.dispose();
            screens.clear();
        }
        return image;
    }
}
