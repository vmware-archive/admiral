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

import com.vmware.xenon.common.ServiceHost;

public class TestServerX509TrustManager extends ServerX509TrustManager {

    public TestServerX509TrustManager(ServiceHost host, Long updateInterval) {
        super(host);
        maintenanceIntervalInitial = updateInterval;
        //reload faster the first 1 minute
        reloadCounterThreshold = (int) (TimeUnit.MINUTES.toMicros(1) / updateInterval);
    }
}
