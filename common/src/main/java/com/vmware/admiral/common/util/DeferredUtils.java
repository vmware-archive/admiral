/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.logging.Level;

import com.vmware.xenon.common.Utils;

public final class DeferredUtils {

    private DeferredUtils() {
    }

    /**
     * Utility method to log an exception and rethrow it wrapped as CompletionException.
     * First unwrap CompletionException if necessary.
     *
     * @param t exception
     * @param m message producer returning the message to log
     * @param c class which is logging the error, used to get logger and class name
     * @return same exception if the input is CompletionException, or a new one wrapping it
     */
    public static CompletionException logErrorAndThrow(Throwable t, Function<Throwable, String> m,
            Class c) {

        logException(t, Level.SEVERE, m, c);

        return wrap(t);
    }

    /**
     * Utility method to log an exception without rethrowing it. Unwrap CompletionException if
     * necessary.
     *
     * @param t exception
     * @param l log level to use
     * @param m message producer returning the message to log
     * @param c class which is logging the error, used to get logger and class name
     */
    public static void logException(Throwable t, Level l, Function<Throwable, String> m, Class c) {
        Throwable cause = t instanceof CompletionException ? t.getCause() : t;

        try {
            String msg = m.apply(cause);
            Utils.log(c, c.getSimpleName(), l, () -> msg);
        } catch (Throwable e) {
            Utils.logWarning("DeferredUtils: Cannot log an error, error: %s", e.getMessage());
        }
    }

    /**
     * Returns same instance if <code>t</code> is instance of CompletionException or wraps it in
     * CompletionException.
     */
    public static CompletionException wrap(Throwable t) {
        return t instanceof CompletionException
                ? (CompletionException) t
                : new CompletionException(t);
    }

}
