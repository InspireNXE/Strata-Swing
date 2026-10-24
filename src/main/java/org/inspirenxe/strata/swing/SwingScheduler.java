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

import org.inspirenxe.strata.api.event.Subscription;
import org.inspirenxe.strata.spi.Scheduler;

import javax.swing.Timer;
import java.util.Objects;

/**
 * Runs repeating tasks with a {@link Timer} each, on the Swing event thread. A timer that falls behind coalesces the
 * runs it missed into one, so a busy event thread drops runs rather than queueing them.
 */
final class SwingScheduler implements Scheduler {

    @Override
    public Subscription every(final long millis, final Runnable task) {
        Objects.requireNonNull(task, "task");
        if (millis <= 0L) {
            throw new IllegalArgumentException("A task's interval must be above 0 ms: " + millis);
        }
        final int delay = (int) Math.min(Integer.MAX_VALUE, millis);
        final TimerTask scheduled = new TimerTask(task);
        final Timer timer = new Timer(delay, event -> scheduled.run());
        timer.setInitialDelay(delay);
        timer.setCoalesce(true);
        scheduled.timer = timer;
        timer.start();
        return scheduled;
    }

    private static final class TimerTask implements Subscription {
        private final Runnable task;
        private Timer timer;
        // Read on the event thread, written by whoever closes
        private volatile boolean closed;

        TimerTask(final Runnable task) {
            this.task = task;
        }

        void run() {
            // A firing already queued on the event thread when the task closed is dropped here
            if (!this.closed) {
                this.task.run();
            }
        }

        @Override
        public void unsubscribe() {
            this.closed = true;
            this.timer.stop();
        }
    }
}
