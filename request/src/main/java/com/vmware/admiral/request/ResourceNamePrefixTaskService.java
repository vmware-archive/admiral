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

package com.vmware.admiral.request;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ResourceNamePrefixService.NamePrefixRequest;
import com.vmware.admiral.service.common.ResourceNamePrefixService.NamePrefixResponse;
import com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task implementing the request for resource name prefixes.
 */
public class ResourceNamePrefixTaskService
        extends
        AbstractTaskStatefulService<ResourceNamePrefixTaskService.ResourceNamePrefixTaskState,
        DefaultSubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_NAME_PREFIXES_TASKS;
    public static final String DISPLAY_NAME = "Resource Name Selection";

    public static class ResourceNamePrefixTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<DefaultSubStage> {

        /** (Required) Number of resources to provision. */
        public long resourceCount;

        /** (Required) The base name format to which the prefix will be applied */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String baseResourceNameFormat;

        /** Set by the Task with the result of applying prefixes based on group selection. */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceNames;
    }

    public ResourceNamePrefixTaskService() {
        super(ResourceNamePrefixTaskState.class, DefaultSubStage.class, DISPLAY_NAME);
    }

    @Override
    protected void handleStartedStagePatch(ResourceNamePrefixTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            queryResourceNamePrefixes(state, false);
            break;
        default:
            break;
        }
    }

    @Override
    protected void validateStateOnStart(ResourceNamePrefixTaskState state) {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.", "request.resource-count.zero");
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ResourceNamePrefixTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceNames = state.resourceNames;
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceNames;
    }

    private void queryResourceNamePrefixes(ResourceNamePrefixTaskState state, boolean globalSearch) {
        // match on group property:
        QueryTask q = QueryUtil.buildQuery(ResourceNamePrefixState.class, false);
        q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;
        q.tenantLinks = state.tenantLinks;

        List<String> documentLinks = new ArrayList<>();
        new ServiceDocumentQuery<>(getHost(), ResourceNamePrefixState.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        failTask("Cannot retrieve prefixes", r.getException());
                    } else if (r.hasResult()) {
                        documentLinks.add(r.getDocumentSelfLink());
                    } else {
                        selectResourceNamePrefix(state, documentLinks.iterator(), globalSearch);
                    }
                });
    }

    private void selectResourceNamePrefix(ResourceNamePrefixTaskState state,
            Iterator<String> iterator, boolean globalSearch) {
        if (!iterator.hasNext()) {
            if (state.tenantLinks != null && !state.tenantLinks.isEmpty() && !globalSearch) {
                // search for global placements (without group)
                queryResourceNamePrefixes(state, true);
            } else {
                failTask("No available resource name prefixes", null);
            }
            return;
        }

        String resourceNamePrefixLink = iterator.next();
        requestResourceNamePrefix(state, resourceNamePrefixLink, iterator, globalSearch);
    }

    private void requestResourceNamePrefix(ResourceNamePrefixTaskState state,
            String resourceNamePrefixLink, Iterator<String> iterator, boolean globalSearch) {

        NamePrefixRequest namePrefixRequest = new NamePrefixRequest();
        namePrefixRequest.resourceCount = state.resourceCount;

        sendRequest(Operation
                .createPatch(this, resourceNamePrefixLink)
                .setBody(namePrefixRequest)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Failure requesting resource name prefixes: %s. Retrying with the next one...",
                                        e.getMessage());
                                selectResourceNamePrefix(state, iterator, globalSearch);
                                return;
                            }
                            NamePrefixResponse response = o.getBody(NamePrefixResponse.class);
                            Set<String> resourceNames = new HashSet<>(response.resourceNamePrefixes
                                    .size());
                            for (String prefix : response.resourceNamePrefixes) {
                                try {
                                    resourceNames.add(String.format(state.baseResourceNameFormat,
                                            prefix));
                                } catch (IllegalFormatException fe) {
                                    failTask("Failure formatting baseResourceNameFormat", fe);
                                    return;
                                }
                            }
                            complete(DefaultSubStage.COMPLETED, s -> {
                                s.resourceNames = resourceNames;
                            });
                        }));
    }
}
