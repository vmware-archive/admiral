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

package com.vmware.admiral.request.compute.allocation.filter;

import static com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState;

import java.util.List;

import com.vmware.admiral.request.utils.RequestUtils;

/**
 * This class contains necessary information used by affinity filters when selecting a host for a
 * given resource
 */
public class FilterContext {
    public String contextId;
    public String serviceLink;
    public List<String> resourcePoolLinks;
    public long resourceCount;
    public boolean isClustering;

    public static FilterContext from(ComputePlacementSelectionTaskState state) {
        FilterContext filterContext = new FilterContext();

        filterContext.contextId = state.contextId;
        filterContext.resourcePoolLinks = state.resourcePoolLinks;
        filterContext.resourceCount = state.resourceCount;
        filterContext.isClustering = state
                .getCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP) != null;
        if (state.serviceTaskCallback != null) {
            filterContext.serviceLink = state.serviceTaskCallback.serviceSelfLink;
        }

        return filterContext;
    }
}
