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

package com.vmware.photon.controller.model.monitoring;

import java.util.Map;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

public class ResourceMetricsService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/resource-metrics";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ResourceMetricsService.class);
    }

    public ResourceMetricsService() {
        super(ResourceMetrics.class);
        super.toggleOption(ServiceOption.IMMUTABLE, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    public static class ResourceMetrics extends ServiceDocument {
        public static final String FIELD_NAME_ENTRIES = "entries";
        public static final String FIELD_NAME_TIMESTAMP = "timestampMicrosUtc";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
        public static final String PROPERTY_RESOURCE_LINK = "resourceLink";

        @Documentation(description = "Map of datapoints. The key represents the metric name and the value"
                + "represents the the metric value")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @PropertyOptions(indexing = PropertyIndexingOption.EXPAND)
        public Map<String, Double> entries;
        @Documentation(description = "timestamp associated with this metric entry")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Long timestampMicrosUtc;

        @PropertyOptions(indexing = {
                PropertyIndexingOption.CASE_INSENSITIVE,
                PropertyIndexingOption.EXPAND,
                PropertyIndexingOption.FIXED_ITEM_NAME })
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_15)
        public Map<String, String> customProperties;
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    private ResourceMetrics processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourceMetrics state = op.getBody(ResourceMetrics.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
