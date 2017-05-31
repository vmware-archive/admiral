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

package com.vmware.admiral.auth.project;

import java.util.List;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectStateWithMembers;
import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.ContainerService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Factory service implementing {@link FactoryService} used to create instances of
 * {@link ContainerService}.
 */
public class ProjectFactoryService extends FactoryService {
    public static final String SELF_LINK = ManagementUriParts.PROJECTS;

    public ProjectFactoryService() {
        super(ProjectState.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new ProjectService();
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.GET && UriUtils.hasODataExpandParamValue(op.getUri())) {
            op.nestCompletion(this::expandGetResults);
        }

        super.handleRequest(op);
    }

    private void expandGetResults(Operation op, Throwable ex) {
        if (ex != null) {
            op.fail(ex);
        }
        ServiceDocumentQueryResult body = op.getBody(ServiceDocumentQueryResult.class);
        if (body.documents != null) {
            List<DeferredResult<ProjectStateWithMembers>> deferredExpands = body.documents.values()
                    .stream()
                    .map((jsonProject) -> {
                        ProjectState projectState = Utils.fromJson(jsonProject, ProjectState.class);
                        return ProjectUtil.expandProjectState(getHost(), projectState, getUri());
                    }).collect(Collectors.toList());
            DeferredResult.allOf(deferredExpands).thenAccept((expandedStates) -> {
                expandedStates.forEach((expandedState) -> {
                    body.documents.put(expandedState.documentSelfLink, expandedState);
                });
            }).thenAccept((ignore) -> op.setBodyNoCloning(body))
                    .whenCompleteNotify(op);
        } else {
            op.complete();
        }
    }

}