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

package com.vmware.admiral.test.integration.client;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

@DcpDocumentKind(ServiceStats.KIND)
public class ServiceStats extends ServiceDocument {
    public static final String KIND = "com:vmware:xenon:common:ServiceStats";

    public static class ServiceStatLogHistogram {
        /**
         * Each bin tracks a power of 10. Bin[0] tracks all values between 0 and 9, Bin[1] tracks
         * values between 10 and 99, Bin[2] tracks values between 100 and 999, and so forth
         */
        public long[] bins = new long[15];
    }

    public static class ServiceStat {
        public static final String KIND = "com:vmware:xenon:common:ServiceStats:ServiceStat";
        public String name;
        public double latestValue;
        public double accumulatedValue;
        public long version;
        public long lastUpdateMicrosUtc;
        public String kind = KIND;

        /**
         * Source (provider) for this stat
         */
        public URI serviceReference;

        public ServiceStatLogHistogram logHistogram;
    }

    public String kind = KIND;

    public Map<String, ServiceStat> entries = new HashMap<>();
}
