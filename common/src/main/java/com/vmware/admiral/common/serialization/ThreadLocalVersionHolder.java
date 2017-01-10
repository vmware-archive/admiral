/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.serialization;

import static com.vmware.admiral.common.serialization.ReleaseConstants.VERSION_PREFIX;
import static com.vmware.xenon.common.Operation.ACCEPT_HEADER;

import com.vmware.xenon.common.Operation;

/**
 * ThreadLocal strategy to keep the version.
 */
public class ThreadLocalVersionHolder {

    private static final ThreadLocal<String> versionHolder = new ThreadLocal<String>();

    /**
     * Clears the version associated with the current thread.
     */
    public static void clearVersion() {
        versionHolder.remove();
    }

    /**
     * Returns the version associated with the current thread.
     */
    public static String getVersion() {
        return versionHolder.get();
    }

    /**
     * Associates the passed version with the current thread.
     */
    public static void setVersion(String version) {
        if (version != null) {
            versionHolder.set(version);
        }
    }

    /**
     * Associates the version included in the passed operation with the current thread.
     */
    public static void setVersion(Operation op) {
        String acceptHeader = op.getRequestHeader(ACCEPT_HEADER);
        String version = extractApiVersion(acceptHeader);
        setVersion(version);
    }

    private static String extractApiVersion(String acceptHeader) {
        if (acceptHeader != null) {
            String[] tokens = acceptHeader.split(";");
            for (String token : tokens) {
                token = token.trim();
                if (token.startsWith(VERSION_PREFIX)) {
                    return token.substring(VERSION_PREFIX.length(), token.length());
                }
            }
        }
        return null;
    }

}