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

package com.vmware.admiral.auth.util;

import java.util.Arrays;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

public class ProjectUtil {
    public static final String PROJECT_IN_USE_MESSAGE = "Project is associated with %s placement%s";
    public static final String PROJECT_IN_USE_MESSAGE_CODE = "host.resource-group.in.use";

    public static QueryTask createQueryTaskForProjectAssociatedWithPlacement(ResourceState project, Query query) {
        QueryTask queryTask = null;
        if (query != null) {
            queryTask = QueryTask.Builder.createDirectTask().setQuery(query).build();
        } else if (project != null && project.documentSelfLink != null) {
            queryTask = QueryUtil.buildQuery(GroupResourcePlacementState.class, true, QueryUtil.addTenantAndGroupClause(Arrays.asList(project.documentSelfLink)));
        }

        if (queryTask != null) {
            QueryUtil.addCountOption(queryTask);
        }

        return queryTask;
    }
}