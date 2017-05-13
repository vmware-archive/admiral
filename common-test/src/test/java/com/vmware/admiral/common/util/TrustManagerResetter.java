/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.lang.reflect.Field;

import com.vmware.xenon.common.Utils;

public final class TrustManagerResetter {
    private TrustManagerResetter() {
    }

    public static void reset() {
        try {
            Field field = ServerX509TrustManager.class.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            Utils.logWarning("Cannot reset ServerX509TrustManager");
        }
    }
}
