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

package com.vmware.admiral.request.kubernetes;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.adapter.common.ApplicationRequest;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.request.kubernetes.CompositeKubernetesRemovalTaskService.CompositeKubernetesRemovalTaskState;
import com.vmware.admiral.request.kubernetes.CompositeKubernetesRemovalTaskService.CompositeKubernetesRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

public class CompositeKubernetesRemovalTaskService
        extends
        AbstractTaskStatefulService<CompositeKubernetesRemovalTaskState, SubStage> {
    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPOSITION_REMOVAL_KUBERNETES_TASK;

    public static final String DISPLAY_NAME = "Kubernetes Composite Removal";

    public static class CompositeKubernetesRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<CompositeKubernetesRemovalTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            INSTANCES_REMOVING,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(INSTANCES_REMOVING));
        }

        @Documentation(
                description = "(Required) The composites on which the given operation will be applied.")
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        /**
         * Whether to actually go and destroy the kubernetes entity using the adapter or just remove
         * the kubernetes state.
         */
        public boolean cleanupOnly;
    }

    public CompositeKubernetesRemovalTaskService() {
        super(CompositeKubernetesRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(CompositeKubernetesRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            proceedTo(SubStage.INSTANCES_REMOVING);
            break;
        case INSTANCES_REMOVING:
            performResourceRemovalOperations(state, null);
            break;
        case COMPLETED:
            complete();
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    private void performResourceRemovalOperations(CompositeKubernetesRemovalTaskState state,
            ServiceTaskCallback taskCallback) {

        if (state.cleanupOnly) {
            cleanupStates(state);
            return;
        }

        if (taskCallback == null) {
            createCounterSubTaskCallback(
                    state,
                    state.resourceLinks.size(),
                    false,
                    true,
                    SubStage.COMPLETED,
                    (serviceTask) -> performResourceRemovalOperations(state, serviceTask));
            return;
        }

        final AtomicBoolean error = new AtomicBoolean();

        try {
            logInfo("Starting removal of %d resources", state.resourceLinks.size());
            for (String link : state.resourceLinks) {
                sendApplicationRemovalRequest(state, link, taskCallback, (o, e) -> {
                    if (e != null) {
                        if (error.compareAndSet(false, true)) {
                            failTask("AdapterRequest failed for composite component: " + link, e);
                        } else {
                            logWarning("AdapterRequest failed Error: %s", Utils.toString(e));
                        }
                    } else {
                        logInfo("Kubernetes application removal started for: %s", link);
                    }
                });
            }
        } catch (Throwable e) {
            failTask("Unexpected exception while requesting removal.", e);
        }

    }

    private void sendApplicationRemovalRequest(final CompositeKubernetesRemovalTaskState state,
            String compositeComponentLink, ServiceTaskCallback taskCallback,
            final CompletionHandler completionHandler) {

        ApplicationRequest request = new ApplicationRequest();
        request.resourceReference = UriUtils.buildUri(getHost(), compositeComponentLink);
        request.customProperties = state.customProperties;
        request.serviceTaskCallback = taskCallback;
        request.operationTypeId = ApplicationOperationType.DELETE.id;

        sendRequest(Operation
                .createPatch(this, ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION)
                .setBody(request)
                .setContextId(getSelfId())
                .setCompletion(completionHandler)
        );

    }

    private void cleanupStates(CompositeKubernetesRemovalTaskState state) {

        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            proceedTo(SubStage.COMPLETED);
            return;
        }

        QueryTask kubernetesQuery = new QueryTask();
        kubernetesQuery.querySpec = new QueryTask.QuerySpecification();
        kubernetesQuery.taskInfo.isDirect = false;

        logInfo("Kubernetes classes count: %d", CompositeComponentRegistry
                .getKubernetesClasses().size());

        logInfo("Host links count: %d", state.resourceLinks.size());

        for (Class<? extends ResourceState> stateClass : CompositeComponentRegistry
                .getKubernetesClasses()) {
            kubernetesQuery.querySpec.query.addBooleanClause(new Query()
                    .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
                    .setTermMatchValue(Utils.buildKind(stateClass))
                    .setOccurance(Occurance.SHOULD_OCCUR));
        }

        QueryUtil.addListValueClause(kubernetesQuery,
                BaseKubernetesState.FIELD_NAME_PARENT_LINK, state.resourceLinks);

        Set<String> kubernetesLinks = new HashSet<>();

        new ServiceDocumentQuery<ResourceState>(getHost(), ResourceState.class).query(
                kubernetesQuery, (r) -> {
                    if (r.hasException()) {
                        failTask("Failure retrieving query results", r.getException());
                    } else if (r.hasResult()) {
                        kubernetesLinks.add(r.getDocumentSelfLink());
                    } else {
                        if (kubernetesLinks.isEmpty()) {
                            proceedTo(SubStage.COMPLETED);
                            return;
                        }

                        removeKubernetesStates(kubernetesLinks);
                    }
                });
    }

    private void removeKubernetesStates(Set<String> kubernetesLinks) {

        AtomicInteger counter = new AtomicInteger(kubernetesLinks.size());
        AtomicBoolean hasError = new AtomicBoolean(false);

        for (String link : kubernetesLinks) {
            sendRequest(Operation.createDelete(this, link)
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            if (hasError.compareAndSet(false, true)) {
                                failTask("Failure removing kubernetes state: ", ex);
                            } else {
                                logWarning("Failure removing kubernetes state: %s",
                                        Utils.toString(ex));
                            }
                        } else {
                            if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                proceedTo(SubStage.COMPLETED);
                            }
                        }
                    }));
        }

    }
}
