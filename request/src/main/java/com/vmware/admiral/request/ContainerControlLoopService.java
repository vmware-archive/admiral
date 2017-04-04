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

package com.vmware.admiral.request;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.request.ContainerRedeploymentTaskService.ContainerRedeploymentTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Enforces the actual state as close as possible to desired state.
 */
public class ContainerControlLoopService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.CONTAINER_CONTROL_LOOP;

    private static final String CONTROL_LOOP_INFO = "control-loop-info";
    public static final String CONTROL_LOOP_INFO_LINK = UriUtils.buildUriPath(
            FACTORY_LINK, CONTROL_LOOP_INFO);

    private static final long MAINTENANCE_INTERVAL_MICROS = Long
            .getLong("com.vmware.admiral.request.container.maintenance.interval.micros",
                    TimeUnit.MINUTES.toMicros(5));

    public static ServiceDocument buildDefaultStateInstance() {
        ContainerControlLoopState state = new ContainerControlLoopState();
        state.documentSelfLink = CONTROL_LOOP_INFO_LINK;
        return state;
    }

    protected volatile AtomicInteger containerDescriptionsToBeProcessed = new AtomicInteger(0);

    public static class ContainerControlLoopState extends com.vmware.xenon.common.ServiceDocument {
    }

    public ContainerControlLoopService() {
        super(ContainerControlLoopState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.setMaintenanceIntervalMicros(MAINTENANCE_INTERVAL_MICROS);
    }

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ContainerControlLoopState initState = post
                .getBody(ContainerControlLoopState.class);
        if (initState.documentSelfLink == null
                || !initState.documentSelfLink
                        .endsWith(CONTROL_LOOP_INFO)) {
            post.fail(new LocalizableValidationException(
                    "Only one instance of container control loop service can be started",
                    "request.container-control-loop.single-instance"));
            return;
        }

        post.setBody(initState).complete();
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logFine("Skipping maintenance since service is not available: %s ", getUri());
            return;
        }

        if (DeploymentProfileConfig.getInstance().isTest()) {
            logWarning("Skipping maintenance in test mode...");
            post.complete();
            return;
        }

        if (containerDescriptionsToBeProcessed.get() == 0) {
            logFine("Performing maintenance for: %s", getUri());

            performMaintenance();
        } else {
            logFine("Skipping maintenance since there is already running maintenance");
        }

        post.complete();
    }

    @Override
    public void handlePatch(Operation patch) {

        if (!checkForBody(patch)) {
            return;
        }

        ContainerControlLoopState body = patch
                .getBody(ContainerControlLoopState.class);

        if (containerDescriptionsToBeProcessed.get() == 0) {
            logFine("Performing maintenance for: %s", getUri());

            performMaintenance();
        } else {
            logFine("Previous maintenence maintenance not finished for: %s", getUri());
        }

        ContainerControlLoopState currentState = getState(patch);
        PropertyUtils.mergeServiceDocuments(currentState, body);
        patch.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private void performMaintenance() {

        retrieveContainerDescriptions().whenComplete((containerDescriptions, e) -> {
            if (e != null) {
                logSevere("Failed to retrieve container descriptions");
                return;
            }

            if (containerDescriptions.size() == 0) {
                logFine("No container descriptions for processing.");
                return;
            }

            containerDescriptionsToBeProcessed.set(containerDescriptions.size());

            for (ContainerDescription containerDescription : containerDescriptions) {
                DeferredResult<List<ContainerState>> containerStates = retrieveContainerStates(
                        containerDescription);
                containerDescriptionsToBeProcessed.decrementAndGet();

                containerStates
                        .thenApply(cs -> groupContainersByContextId(cs))
                        .whenComplete((groupedContainers, ex) -> {
                            if (ex != null) {
                                logSevere("Failed to retrieve containers");
                                return;
                            }

                            if (groupedContainers.isEmpty()) {
                                logFine("No grouped containers from description: %s",
                                        containerDescription.documentSelfLink);
                                return;
                            }

                            ContainerStateInspector inspectedContainerStates = ContainerStateInspector
                                    .inspect(containerDescription, groupedContainers);
                            ContainerRecommendation recommendation = ContainerRecommendation
                                    .recommend(inspectedContainerStates);

                            switch (recommendation.getRecommendation()) {
                            case REDEPLOY:
                                redeployContainers(recommendation);
                                break;

                            default:
                                break;
                            }
                        });
            }
        });
    }

    private DeferredResult<List<ContainerDescription>> retrieveContainerDescriptions() {
        logFine("Retrieve all container descriptions which have autoredeploy option enabled."
                + "System container is excluded.");

        Builder builder = Builder.create()
                .addKindFieldClause(ContainerDescription.class)
                .addFieldClause(ContainerDescription.FIELD_NAME_SELF_LINK,
                        SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK,
                        Occurance.MUST_NOT_OCCUR)
                .addCompositeFieldClause(ContainerDescription.FIELD_NAME_HEALTH_CONFIG,
                        HealthConfig.FIELD_NAME_AUTOREDEPLOY, Boolean.TRUE.toString(), Occurance.MUST_OCCUR);

        QueryByPages<ContainerDescription> query = new QueryByPages<>(getHost(), builder.build(),
                ContainerDescription.class, null);

        return query.collectDocuments(Collectors.toList());
    }

    private DeferredResult<List<ContainerState>> retrieveContainerStates(
            ContainerDescription cd) {
        logFine("Retrieving containers from container description: %s", cd.documentSelfLink);
        Builder builder = Builder.create()
                .addKindFieldClause(ContainerState.class)
                .addFieldClause(ContainerState.FIELD_NAME_DESCRIPTION_LINK, cd.documentSelfLink);

        QueryByPages<ContainerState> query = new QueryByPages<>(getHost(), builder.build(),
                ContainerState.class, null);

        return query.collectDocuments(Collectors.toList());
    }

    private Map<String, List<ContainerState>> groupContainersByContextId(
            List<ContainerState> containers) {
        logFine("Grouping containers by context_id");

        if (containers == null) {
            throw new LocalizableValidationException("Containers not provided",
                    "request.container-control-loop.containers-not-provided");
        }

        // if the containers are discovered (they don't have context_id) they won't be processed
        Map<String, List<ContainerState>> groupedContainers = containers.stream().filter(
                cs -> cs.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY) != null)
                .collect(Collectors
                        .groupingBy(cs -> cs.customProperties
                                .get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY)));

        return groupedContainers;
    }

    private void redeployContainers(ContainerRecommendation recommendation) {

        ContainerDescription cd = recommendation.getContainerDescription();
        recommendation.getContainersToBeRemoved().entrySet().stream().forEach(c -> {
            String contextId = c.getKey();
            List<String> containerLinks = c.getValue().stream().map(cs -> cs.documentSelfLink)
                    .collect(Collectors.toList());
            int desiredClusterSize = cd._cluster == null ? 1 : cd._cluster;

            if (containerLinks == null || containerLinks.isEmpty()) {
                logFine("Skip container redeployment. No containers for redeploy with context_id %s from description: %s",
                        contextId, cd.documentSelfLink);
            } else {
                createContainerRedeployingTask(cd.documentSelfLink, containerLinks, cd.tenantLinks,
                        contextId, desiredClusterSize);
            }
        });
    }

    private void createContainerRedeployingTask(String containerDescriptionLink,
            List<String> containerLinks, List<String> tenantLinks, String contextId,
            int desiredClusterSize) {
        ContainerRedeploymentTaskState redeployingTaskState = new ContainerRedeploymentTaskState();
        redeployingTaskState.containerDescriptionLink = containerDescriptionLink;
        redeployingTaskState.containerStateLinks = containerLinks;
        redeployingTaskState.contextId = contextId;
        redeployingTaskState.desiredClusterSize = desiredClusterSize;
        redeployingTaskState.tenantLinks = tenantLinks;
        redeployingTaskState.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        sendRequest(Operation
                .createPost(this, ContainerRedeploymentTaskService.FACTORY_LINK)
                .setBody(redeployingTaskState)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        o.fail(new LocalizableValidationException(
                                "Creation of redeployment task failed",
                                "request.container-control-loop-state.create-redeployment-task-fail"));
                        return;
                    }
                }));
    }
}