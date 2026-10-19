package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.text.TextMetrics;
import org.inspirenxe.strata.spi.Canvas;
import org.inspirenxe.strata.spi.Platform;
import org.inspirenxe.strata.spi.testing.CanvasContract;
import org.inspirenxe.strata.spi.testing.PlatformContract;
import org.inspirenxe.strata.spi.testing.TextMetricsContract;
import org.junit.jupiter.api.Nested;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Runs the SPI contracts against the Swing adapter.
 */
class SwingContractTests {
    private static final SwingTextMetrics METRICS = SwingTextMetrics.defaults();

    @Nested
    class TextMetricsTest extends TextMetricsContract {
        @Override
        protected TextMetrics metrics() {
            return METRICS;
        }
    }

    @Nested
    class PlatformTest extends PlatformContract {
        @Override
        protected Platform platform() {
            return new SwingPlatform(METRICS);
        }

        @Override
        protected boolean hasClipboard() {
            // Tests run headless, where AWT has no clipboard
            return false;
        }
    }

    @Nested
    class CanvasTest extends CanvasContract {
        @Override
        protected Surface surface(final int width, final int height) {
            return new ImageSurface(width, height, new SwingSprites(SwingContractTests.class.getClassLoader()));
        }

        @Override
        protected TextMetrics metrics() {
            return METRICS;
        }
    }

    /**
     * A Swing canvas over an image, one pixel per UI unit, with the same text settings the panel uses.
     */
    static final class ImageSurface implements CanvasContract.Surface {
        private final BufferedImage image;
        private final Graphics2D graphics;
        private final SwingCanvas canvas;

        ImageSurface(final int width, final int height, final SwingSprites sprites) {
            this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            this.graphics = this.image.createGraphics();
            this.graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            this.graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            this.canvas = new SwingCanvas(METRICS, sprites);
            this.canvas.begin(this.graphics);
        }

        @Override
        public Canvas canvas() {
            return this.canvas;
        }

        SwingCanvas swingCanvas() {
            return this.canvas;
        }

        @Override
        public void finish() {
            this.graphics.dispose();
        }

        @Override
        public boolean readable() {
            return true;
        }

        @Override
        public int argb(final int x, final int y) {
            return this.image.getRGB(x, y);
        }
    }
}
