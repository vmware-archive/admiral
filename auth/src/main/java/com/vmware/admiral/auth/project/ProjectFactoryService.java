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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.project.ProjectService.ExpandedProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
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

    public static final String QUERY_PARAM_PUBLIC = "public";

    public static class PublicProjectDto {
        public String name;

        public String documentSelfLink;

        public Map<String, String> customProperties;
    }

    public ProjectFactoryService() {
        super(ProjectState.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new ProjectService();
    }

    @Override
    public void authorizeRequest(Operation op) {
        if (isPublicProjectsAccess(op)) {
            op.complete();
            return;
        }
        super.authorizeRequest(op);
    }

    @Override
    public void handleRequest(Operation op) {
        if (isPublicProjectsAccess(op)) {
            // Set system authorization and add expand query if not present.
            if (!UriUtils.hasODataExpandParamValue(op.getUri())) {
                op.setUri(UriUtils.extendUriWithQuery(op.getUri(),
                        UriUtils.URI_PARAM_ODATA_EXPAND_NO_DOLLAR_SIGN, Boolean.TRUE.toString()));
            }
            setAuthorizationContext(op, getSystemAuthorizationContext());
            op.nestCompletion(this::filterNonPublicProjects);
        } else if (op.getAction() == Action.GET && UriUtils.hasODataExpandParamValue(op.getUri())) {
            op.nestCompletion(this::expandGetResults);
        }

        super.handleRequest(op);
    }

    /*
     * Accepts ?public, ?public= or ?public=true, but nothing else.
     */
    public static boolean isPublicProjectsAccess(Operation op) {
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());
        return (op.getAction() == Action.GET) && queryParams.containsKey(QUERY_PARAM_PUBLIC) &&
                (queryParams.get(QUERY_PARAM_PUBLIC).isEmpty()
                        || Boolean.parseBoolean(queryParams.get(QUERY_PARAM_PUBLIC)));
    }

    private void expandGetResults(Operation op, Throwable ex) {
        if (ex != null) {
            op.fail(ex);
        }
        ServiceDocumentQueryResult body = op.getBody(ServiceDocumentQueryResult.class);
        if (body.documents != null) {
            try {
                List<DeferredResult<ExpandedProjectState>> deferredExpands = body.documents.values()
                        .stream()
                        .map((jsonProject) -> {
                            logFine(() -> ("Expanding project : " + Utils.toJson(jsonProject)));
                            ProjectState projectState = Utils
                                    .fromJson(jsonProject, ProjectState.class);

                            return ProjectUtil
                                    .basicExpandProjectState(this, projectState, getUri());
                        }).collect(Collectors.toList());

                DeferredResult
                        .allOf(deferredExpands)
                        .thenAccept((expandedStates) ->
                                expandedStates.forEach((expState) -> {
                                    body.documents.put(expState.documentSelfLink, expState);
                                }))
                        .thenAccept((ignore) -> op.setBodyNoCloning(body))
                        .whenCompleteNotify(op);
            } catch (Throwable t) {
                op.fail(new IllegalStateException("Invalid project state", t));
            }
        } else {
            op.complete();
        }
    }

    private void filterNonPublicProjects(Operation op, Throwable ex) {
        if (ex != null) {
            op.fail(ex);
        }
        ServiceDocumentQueryResult body = op.getBody(ServiceDocumentQueryResult.class);
        if (body.documents != null) {
            List<PublicProjectDto> publicProjects = body.documents.values().stream()
                    .map(doc -> Utils.fromJson(doc, ProjectState.class))
                    // TODO - filtering wouldn't be not needed if the odata query had explicitly
                    // public = true
                    .filter(doc -> doc.isPublic != null && doc.isPublic)
                    .map(doc -> {
                        PublicProjectDto publicProjectDto = new PublicProjectDto();
                        publicProjectDto.name = doc.name;
                        publicProjectDto.documentSelfLink = doc.documentSelfLink;
                        publicProjectDto.customProperties = doc.customProperties;
                        return publicProjectDto;
                    }).collect(Collectors.toList());
            ServiceDocumentQueryResult newBody = new ServiceDocumentQueryResult();
            newBody.documentLinks = new ArrayList<>();
            newBody.documents = new HashMap<>();
            newBody.documentCount = (long) publicProjects.size();
            for (PublicProjectDto publicProjectDto : publicProjects) {
                newBody.documentLinks.add(publicProjectDto.documentSelfLink);
                newBody.documents.put(publicProjectDto.documentSelfLink, publicProjectDto);
            }
            op.setBodyNoCloning(newBody);
        }
        op.complete();
    }

}