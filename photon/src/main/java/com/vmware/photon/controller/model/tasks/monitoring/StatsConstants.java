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

public class StatsConstants {

    public static final String DAILY_SUFFIX = "(Daily)";
    public static final String HOUR_SUFFIX = "(Hourly)";
    public static final String MIN_SUFFIX = "(Minutes)";

    // number of buckets to keep data for an hour at one minute intervals
    public static final int NUM_BUCKETS_MINUTE_DATA = 60;
    // size of the bucket in milliseconds for maintaining data at a minute granularity
    public static final int BUCKET_SIZE_MINUTES_IN_MILLIS = 1000 * 60;

    // number of buckets to keep data for a day at one hour intervals
    public static final int NUM_BUCKETS_HOURLY_DATA = 24;
    // number of buckets to keep data for 4 weeks at 1 day interval
    public static final int NUM_BUCKETS_DAILY_DATA = 4 * 7;

    // size of the bucket in milliseconds for maintaining data at the granularity of an hour
    public static final int BUCKET_SIZE_HOURS_IN_MILLIS = BUCKET_SIZE_MINUTES_IN_MILLIS * 60;
    // size of the bucket in milliseconds for maintaining data at the granularity of a day
    public static final int BUCKET_SIZE_DAYS_IN_MILLIS = BUCKET_SIZE_HOURS_IN_MILLIS * 24;
}
