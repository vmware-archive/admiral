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

package com.vmware.photon.controller.model.tasks.monitoring;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.vmware.xenon.common.UriUtils;

/**
 * Stats related utils.
 */
public class StatsUtil {

    /**
     * Separator for metric key. To be able parse back and forth between metric key and
     * resourceId/metricName, we assume that metric names don't contain the separator "-".
     */
    public static final String SEPARATOR = "_";

    /**
     * Builds a metric key for given resource link and timestamp.
     */
    public static String getMetricKey(String resourceLink,
            Long sourceTimeMicrosUtc) {
        return UriUtils.getLastPathSegment(resourceLink) + SEPARATOR +
                +sourceTimeMicrosUtc;
    }

    /**
     * Returns all the fragments of the metricLink.
     */
    private static MetricKeyComponents getMetricLinkFragments(String metricLink) {
        metricLink = UriUtils.getLastPathSegment(metricLink);

        String[] linkFragments = metricLink.split(SEPARATOR);

        if (linkFragments.length != 2) {
            throw new IllegalArgumentException("Incorrect metric key format "
                    + Arrays.toString(linkFragments)
                    + ". Expected resource-id_timestamp.");
        }

        MetricKeyComponents metricKeyComponents = new MetricKeyComponents();
        metricKeyComponents.resourceId = linkFragments[0];
        metricKeyComponents.timestamp = linkFragments[1];
        return metricKeyComponents;
    }

    /**
     * Returns the resource id for given metric link.
     */
    public static String getResourceId(String metricLink) {
        MetricKeyComponents metricKeyComponents = getMetricLinkFragments(metricLink);
        return metricKeyComponents.resourceId;
    }

    /**
     * Computes the beginning of the rollup interval.
     */
    public static long computeIntervalBeginMicros(long timestampMicros, long bucketDurationMillis) {
        long bucketDurationMicros = TimeUnit.MILLISECONDS.toMicros(bucketDurationMillis);
        timestampMicros -= (timestampMicros % bucketDurationMicros);
        return timestampMicros;
    }

    /**
     * Computes the end of the rollup interval.
     */
    public static long computeIntervalEndMicros(long timestampMicros, long bucketDurationMillis) {
        timestampMicros = computeIntervalBeginMicros(timestampMicros, bucketDurationMillis);
        return (timestampMicros + TimeUnit.MILLISECONDS.toMicros(bucketDurationMillis));
    }

    private static class MetricKeyComponents {
        public String resourceId;
        @SuppressWarnings("unused")
        public String timestamp;
    }
}
