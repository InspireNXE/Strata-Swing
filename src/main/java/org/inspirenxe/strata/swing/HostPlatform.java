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

import org.inspirenxe.strata.api.event.cause.CauseStack;
import org.inspirenxe.strata.api.text.TextMetrics;
import org.inspirenxe.strata.spi.Capabilities;
import org.inspirenxe.strata.spi.Clipboard;
import org.inspirenxe.strata.spi.Clock;
import org.inspirenxe.strata.spi.Conventions;
import org.inspirenxe.strata.spi.FileDialogSource;
import org.inspirenxe.strata.spi.Platform;
import org.inspirenxe.strata.spi.Scheduler;
import org.inspirenxe.strata.spi.TextInputSink;
import org.inspirenxe.strata.spi.ThemeSource;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * The platform one panel's or one snapshot's screen stack runs with: a {@link SwingPlatform} with a text input sink
 * of its own, and either the event thread as its UI thread or none, so that core takes the first thread that drives
 * the stack. A panel's repeating tasks run from the Swing platform's timers on the event thread; a snapshot draws one
 * frame on a thread of its own, so its tasks never run. Every other member comes from the Swing platform. Each one is equal only to itself, so core keeps a UI
 * thread for each.
 */
final class HostPlatform implements Platform {
    private final SwingPlatform platform;
    private final TextInputSink textInput;
    private final boolean eventThread;

    /**
     * Creates the platform.
     *
     * @param platform the Swing platform everything else comes from
     * @param textInput the text input sink
     * @param eventThread true to run on the Swing event thread, false to let core record the thread
     */
    HostPlatform(final SwingPlatform platform, final TextInputSink textInput, final boolean eventThread) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.eventThread = eventThread;
    }

    /**
     * Returns the Swing platform this one forwards to.
     *
     * @return the Swing platform
     */
    SwingPlatform swing() {
        return this.platform;
    }

    @Override
    public TextMetrics textMetrics() {
        return this.platform.textMetrics();
    }

    @Override
    public Scheduler scheduler() {
        return this.eventThread ? this.platform.scheduler() : (millis, task) -> {
            if (millis <= 0L) {
                throw new IllegalArgumentException("A task's interval must be above 0 ms: " + millis);
            }
            return () -> { };
        };
    }

    @Override
    public Clipboard clipboard() {
        return this.platform.clipboard();
    }

    @Override
    public Clock clock() {
        return this.platform.clock();
    }

    @Override
    public TextInputSink textInput() {
        return this.textInput;
    }

    @Override
    public Capabilities capabilities() {
        return this.platform.capabilities();
    }

    @Override
    public Conventions conventions() {
        return this.platform.conventions();
    }

    @Override
    public ThemeSource themes() {
        return this.platform.themes();
    }

    @Override
    public Optional<CauseStack> causes() {
        return this.platform.causes();
    }

    @Override
    public Optional<Object> user() {
        return this.platform.user();
    }

    @Override
    public Optional<Executor> uiExecutor() {
        return this.eventThread ? this.platform.uiExecutor() : Optional.empty();
    }

    @Override
    public Optional<BooleanSupplier> uiThreadCheck() {
        return this.eventThread ? this.platform.uiThreadCheck() : Optional.empty();
    }

    @Override
    public Optional<FileDialogSource> fileDialogs() {
        return this.platform.fileDialogs();
    }
}
