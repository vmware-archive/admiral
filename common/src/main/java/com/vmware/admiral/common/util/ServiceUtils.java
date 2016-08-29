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

import java.util.concurrent.TimeUnit;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;

public class ServiceUtils {

    public static final long EXPIRATION_MICROS = Long.getLong(
            "com.vmware.admiral.common.util.ServiceUtils.expiration.micros",
            TimeUnit.HOURS.toMicros(5));

    /*
     * The default expiration time for all Task Services.
     */
    public static long getDefaultTaskExpirationTimeInMicros() {
        return getExpirationTimeFromNowInMicros(EXPIRATION_MICROS);
    }

    /*
     * Adds the supplied argument to the value returned from Utils.getNotMicrosUtc
     */
    public static long getExpirationTimeFromNowInMicros(long microsFromNow) {
        return Utils.getNowMicrosUtc() + microsFromNow;
    }

    public static void sendSelfDelete(Service s) {
        s.sendRequest(Operation.createDelete(s.getUri()).setBody(new ServiceDocument()));
    }
}