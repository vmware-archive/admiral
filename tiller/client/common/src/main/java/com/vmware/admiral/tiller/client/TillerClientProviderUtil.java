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

package com.vmware.admiral.tiller.client;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Utility class that helps to obtain a {@link TillerClientProvider} instance.
 */
public class TillerClientProviderUtil {

    /**
     * @return a {@link TillerClientProvider} instance
     * @throws TillerClientException
     *             if no {@link TillerClientProvider}s could be loaded
     */
    public static TillerClientProvider getTillerClientProvider() {
        return getTillerClientProvider(null);
    }

    /**
     * @param preferredProviderClassName
     *            the class name, canonical class name or simple class name of a preferred
     *            {@link TillerClientProvider} implementation
     * @return a {@link TillerClientProvider} instance. If the preferred implementation is available
     *         in the classpath, it will be returned. Otherwise, another implementation will be
     *         returned.
     * @throws TillerClientException
     *             if no {@link TillerClientProvider}s could be loaded
     */
    public static TillerClientProvider getTillerClientProvider(String preferredProviderClassName) {
        ServiceLoader<TillerClientProvider> loader = ServiceLoader.load(TillerClientProvider.class);
        Iterator<TillerClientProvider> iterator = loader.iterator();

        // if there is no preference, just return the first provider
        boolean noPreference = preferredProviderClassName == null
                || preferredProviderClassName.isEmpty();
        if (noPreference && iterator.hasNext()) {
            return iterator.next();
        }

        // otherwise, iterate over the result and look for the specified class name
        TillerClientProvider provider = null;
        while (iterator.hasNext()) {
            TillerClientProvider currentProvider = iterator.next();
            Class<? extends TillerClientProvider> clazz = currentProvider.getClass();
            // the preference might be any of the class name, canonical name or simple name
            if (Stream.of(clazz.getName(), clazz.getCanonicalName(), clazz.getSimpleName())
                    .anyMatch(preferredProviderClassName::equals)) {
                return currentProvider;
            }

            // this guarantees that invoking with no preference and with
            // non-satisfiable preference will return the same result
            if (provider == null) {
                provider = currentProvider;
            }
        }

        if (provider != null) {
            return provider;
        }

        throw new TillerClientException("No TillerClientProvider implementation was loaded");
    }
}
