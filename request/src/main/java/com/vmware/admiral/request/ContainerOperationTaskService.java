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

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.compute.container.SystemContainerDescriptions.isSystemContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.admiral.request.ContainerOperationTaskService.ContainerOperationTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Task implementing Container post-provisioning (Day2) operation.
 */
public class ContainerOperationTaskService extends
        AbstractTaskStatefulService<ContainerOperationTaskService.ContainerOperationTaskState,
        ContainerOperationTaskService.ContainerOperationTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Container Operation";

    public static class ContainerOperationTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerOperationTaskState.SubStage> {
        private static final String FIELD_NAME_OPERATION = "operation";
        private static final String FIELD_RESOURCE_LINKS = "resourceLinks";

        public static enum SubStage {
            CREATED,
            COMPLETED,
            ERROR;
        }

        /** (Required) The name of the resource operation to be performed. */
        public String operation;

        /** (Required) The resources on which the given operation will be applied */
        public List<String> resourceLinks;
    }

    public ContainerOperationTaskService() {
        super(ContainerOperationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(ContainerOperationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            queryContainerResources(state);
            break;
        case COMPLETED:
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ContainerOperationTaskState patchBody, ContainerOperationTaskState currentState) {
        return false;
    }

    @Override
    protected void validateStateOnStart(ContainerOperationTaskState state)
            throws IllegalArgumentException {
        assertNotEmpty(state.operation, "operation");
        assertNotEmpty(state.resourceLinks, "resourceLinks");
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        TaskStatusState statusTask = super.fromTask(state);
        ContainerOperationTaskState currentState = (ContainerOperationTaskState) state;
        statusTask.name = ContainerOperationType.extractDisplayName(currentState.operation);
        return statusTask;
    }

    private void queryContainerResources(ContainerOperationTaskState state) {
        QueryTask computeQuery = createResourcesQuery(ContainerState.class, state.resourceLinks);
        ServiceDocumentQuery<ContainerState> query = new ServiceDocumentQuery<>(getHost(),
                ContainerState.class);
        final List<ContainerState> documents = new ArrayList<>(state.resourceLinks.size());
        query.query(computeQuery, (r) -> {
            if (r.hasException()) {
                failTask("Failure retrieving query results", r.getException());
            } else if (r.hasResult()) {
                if (isSystemContainer(r.getResult())) {
                    failTask(null, new IllegalArgumentException(
                            "Day2 operations are not supported for system container"));
                }
                documents.add(r.getResult());
            } else {
                if (documents.isEmpty()) {
                    if (ContainerOperationType.DELETE.id.equals(state.operation)) {
                        logWarning("No resources found to be removed with links: %s",
                                state.resourceLinks);
                        sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
                    } else {
                        failTask("No available resources", null);
                    }
                } else {
                    performResourceOperations(state, documents, null);
                }
            }
        });
    }

    private QueryTask createResourcesQuery(Class<? extends ServiceDocument> type,
            List<String> resourceLinks) {
        QueryTask query = QueryUtil.buildQuery(type, false);
        query.documentExpirationTimeMicros = ServiceUtils.getDefaultTaskExpirationTimeInMicros();
        query.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT);

        QueryUtil.addListValueClause(query, ServiceDocument.FIELD_NAME_SELF_LINK, resourceLinks);

        return query;
    }

    private void performResourceOperations(ContainerOperationTaskState state,
            Collection<ContainerState> resources, ServiceTaskCallback taskCallback) {
        if (taskCallback == null) {
            createCounterSubTaskCallback(state, resources.size(), true,
                    (serviceTask) -> performResourceOperations(state, resources, serviceTask));
            return;
        }

        try {
            logInfo("Starting %s of %d container resources", state.operation, resources.size());
            for (ContainerState container : resources) {
                createAdapterRequest(state, container, taskCallback);
            }
        } catch (Throwable e) {
            failTask("Unexpected exception while requesting operation: " + state.operation, e);
        }
    }

    private void createAdapterRequest(ContainerOperationTaskState state,
            ContainerState containerState, ServiceTaskCallback taskCallback) {
        AdapterRequest adapterRequest = new AdapterRequest();
        String selfLink = containerState.documentSelfLink;
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), selfLink);
        adapterRequest.serviceTaskCallback = taskCallback;
        adapterRequest.operationTypeId = state.operation;
        adapterRequest.customProperties = state.customProperties;
        sendRequest(Operation.createPatch(containerState.adapterManagementReference)
                .setBody(adapterRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("AdapterRequest failed for container: " + selfLink, e);
                        return;
                    } else {
                        patchContainerStats(state, containerState);
                    }
                }));
    }

    private void patchContainerStats(ContainerOperationTaskState state, ContainerState containerState) {
        if (state.operation.equals(ContainerOperationType.STOP.toString())) {
            ContainerStats patch = new ContainerStats();
            patch.containerStopped = true;
            sendRequest(Operation.createPatch(this, containerState.documentSelfLink)
                    .setBody(patch));
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                ContainerOperationTaskState.FIELD_NAME_OPERATION,
                ContainerOperationTaskState.FIELD_RESOURCE_LINKS);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                ContainerOperationTaskState.FIELD_NAME_OPERATION,
                ContainerOperationTaskState.FIELD_RESOURCE_LINKS);

        setDocumentTemplateUsageOptions(template, EnumSet.of(PropertyUsageOption.SERVICE_USE));

        return template;
    }
}
