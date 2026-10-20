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

import org.inspirenxe.strata.api.appearance.Appearance;
import org.inspirenxe.strata.core.StrataCore;

import java.awt.image.BufferedImage;

/**
 * The appearances the Swing adapter gives AWT types: a {@link BufferedImage} shows as itself, at its pixel size.
 */
final class SwingAppearances {
    private static boolean installed;

    private SwingAppearances() {
    }

    /**
     * Registers the defaults the first time it is called. Every {@link SwingPlatform} calls it.
     */
    static synchronized void install() {
        if (!installed) {
            installed = true;
            StrataCore.registerDefaultAppearance(BufferedImage.class,
                    Appearance.<BufferedImage>builder().icon(SwingVisuals::image).build());
        }
    }
}
