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

package com.vmware.admiral.common.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utilities for concurrent package
 */
public class ConcurrentUtil {
    /**
     * Atomically increment the counter if it's below the given max value
     *
     * @param counter
     * @param max
     * @return true if incremented, false if current value is not less than the max value
     */
    public static boolean incrementIfLessThan(AtomicInteger counter, int max) {
        while (true) {
            int current = counter.intValue();
            if (current < max) {
                if (counter.compareAndSet(current, current + 1)) {
                    return true;
                }

                // otherwise loop and try again

            } else {
                return false;
            }
        }
    }

}
