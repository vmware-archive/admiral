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

package com.vmware.photon.controller.model.adapterapi;

import java.util.List;
import java.util.Map;

import com.vmware.xenon.common.ServiceStats.ServiceStat;

/**
 * Defines the response body for getting health status of a Compute instance.
 */
public class ComputeStatsResponse {

    /**
     * List of stats
     */
    public List<ComputeStats> statsList;

    /**
     * Task stage to patch back
     */
    public Object taskStage;

    public static class ComputeStats {
        /**
         *  link of the compute resource the stats belongs to
         */
        public String computeLink;

        /**
         * Stats values are of type ServiceStat
         */
        public Map<String, List<ServiceStat>> statValues;
    }
}
