package org.inspirenxe.strata.swing;

import org.inspirenxe.strata.api.layout.Anchor;
import org.inspirenxe.strata.api.theme.Paint;
import org.inspirenxe.strata.api.theme.Themes;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Renders whole screens headlessly and checks pixels, end to end through the host, layout and the Swing canvas.
 */
class SwingSnapshotTest {

    @Test
    void rendersAScreenAtTheGivenScale() {
        final BufferedImage image = SwingSnapshot.render(factory -> {
            final var box = factory.anchorPanel().setBackground(Paint.solid(0xFF112233));
            box.setSize(10, 10).setAnchor(Anchor.CENTER);
            return factory.anchorPanel().setBackground(Paint.solid(0xFF445566)).add(box).setAnchor(Anchor.FILL);
        }, Themes.flat(), 100, 60, 2f);

        // 50 by 30 UI units; the 10 by 10 box sits centered at UI (20, 10), which is pixels (40, 20) to (60, 40)
        assertEquals(0xFF445566, image.getRGB(5, 5), "the root fills the screen");
        assertEquals(0xFF112233, image.getRGB(50, 30), "the centered box");
        assertEquals(0xFF112233, image.getRGB(40, 20), "the box's top-left pixel");
        assertEquals(0xFF445566, image.getRGB(39, 20), "just left of the box");
        assertEquals(0xFF445566, image.getRGB(60, 40), "just past the box's bottom-right corner");
    }

    @Test
    void screenClosesAfterTheSnapshot() {
        final boolean[] closed = {false};
        SwingSnapshot.render(new org.inspirenxe.strata.api.screen.Screen() {
            @Override
            public org.inspirenxe.strata.api.component.Component<?> build(final org.inspirenxe.strata.api.ComponentFactory factory) {
                return factory.label("x");
            }

            @Override
            public void closed() {
                closed[0] = true;
            }
        }, Themes.flat(), 10, 10, 1f);

        assertEquals(true, closed[0]);
    }
}
