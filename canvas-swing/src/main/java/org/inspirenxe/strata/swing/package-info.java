/**
 * The Java2D/Swing adapter.
 *
 * <p>Most code needs only {@link org.inspirenxe.strata.swing.StrataPanel}, which shows a stack of screens in a
 * Swing window, or {@link org.inspirenxe.strata.swing.SwingSnapshot}, which renders a screen to an image without a
 * display. To host Strata in other Java2D code, use the SPI pieces directly:
 * {@link org.inspirenxe.strata.swing.SwingPlatform} and {@link org.inspirenxe.strata.swing.SwingCanvas}, with
 * {@link org.inspirenxe.strata.swing.SwingInput} to translate AWT key and button codes.</p>
 *
 * <p>Sprites load from the class path, at {@code assets/<namespace>/sprites/<path>.png} unless a
 * {@link org.inspirenxe.strata.swing.SpriteResolver} names another place.</p>
 *
 * <p>Like Swing itself, every class here must be used on the Swing event thread.</p>
 */
package org.inspirenxe.strata.swing;
