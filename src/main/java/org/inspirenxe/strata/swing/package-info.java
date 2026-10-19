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
