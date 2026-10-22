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

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/**
 * Runs each test, and its set-up and tear-down methods, on the Swing event thread, the UI thread of a panel's
 * platform, as an application's code would.
 */
final class OnEventThread implements InvocationInterceptor {

    @Override
    public void interceptBeforeEachMethod(final Invocation<Void> invocation,
                                          final ReflectiveInvocationContext<Method> invocationContext,
                                          final ExtensionContext extensionContext) throws Throwable {
        OnEventThread.run(invocation);
    }

    @Override
    public void interceptTestMethod(final Invocation<Void> invocation,
                                    final ReflectiveInvocationContext<Method> invocationContext,
                                    final ExtensionContext extensionContext) throws Throwable {
        OnEventThread.run(invocation);
    }

    @Override
    public void interceptAfterEachMethod(final Invocation<Void> invocation,
                                         final ReflectiveInvocationContext<Method> invocationContext,
                                         final ExtensionContext extensionContext) throws Throwable {
        OnEventThread.run(invocation);
    }

    private static void run(final Invocation<Void> invocation) throws Throwable {
        final Throwable[] failure = new Throwable[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    invocation.proceed();
                } catch (final Throwable t) {
                    failure[0] = t;
                }
            });
        } catch (final InvocationTargetException e) {
            throw e.getCause();
        }
        if (failure[0] != null) {
            throw failure[0];
        }
    }
}
