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

import static com.vmware.admiral.common.util.QueryUtil.createKindClause;
import static com.vmware.admiral.compute.ContainerHostUtil.filterKubernetesHostLinks;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.ContainerHostRemovalTaskService.ContainerHostRemovalTaskState.SubStage;
import com.vmware.admiral.request.ContainerNetworkRemovalTaskService.ContainerNetworkRemovalTaskState;
import com.vmware.admiral.request.ContainerRemovalTaskService.ContainerRemovalTaskState;
import com.vmware.admiral.request.ContainerVolumeRemovalTaskService.ContainerVolumeRemovalTaskState;
import com.vmware.admiral.request.kubernetes.CompositeKubernetesRemovalTaskService;
import com.vmware.admiral.request.kubernetes.CompositeKubernetesRemovalTaskService.CompositeKubernetesRemovalTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.CounterSubTaskService.CounterSubTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Task implementing removal of container hosts (including removing all the ContainerStates that
 * depend on the hosts)
 */
public class ContainerHostRemovalTaskService extends
        AbstractTaskStatefulService<ContainerHostRemovalTaskService.ContainerHostRemovalTaskState,
                ContainerHostRemovalTaskService.ContainerHostRemovalTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Host Removal";

    public static class ContainerHostRemovalTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerHostRemovalTaskState.SubStage> {

        /**
         * (Required) The resources on which the given operation will be applied
         */
        @PropertyOptions(usage = { REQUIRED, SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        /**
         * The credentials that potentially we can delete after deleting the container hosts.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_1_2_2)
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL },
                indexing = STORE_ONLY)
        public List<String> trustCertificatesForDelete;

        /**
         * (Internal) Set by Task for the query to retrieve all the containers for the given hosts.
         */
        @PropertyOptions(usage = { SERVICE_USE, SINGLE_ASSIGNMENT, AUTO_MERGE_IF_NOT_NULL },
                indexing = STORE_ONLY)
        public String containerQueryTaskLink;

        public boolean skipComputeHostRemoval;

        public static enum SubStage {
            CREATED,
            SUSPENDING_HOSTS,
            SUSPENDED_HOSTS,
            REMOVING_CONTAINERS,
            REMOVED_CONTAINERS,
            REMOVING_NETWORKS,
            REMOVED_NETWORKS,
            REMOVING_VOLUMES,
            REMOVED_VOLUMES,
            REMOVING_KUBERNETES_RESOURCES,
            REMOVED_KUBERNETES_RESOURCES,
            REMOVING_PORT_PROFILES,
            REMOVED_PORT_PROFILES,
            REMOVING_CERTIFICATES,
            REMOVED_CERTIFICATES,
            REMOVING_HOSTS,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(REMOVING_HOSTS, SUSPENDING_HOSTS, REMOVING_CONTAINERS,
                            REMOVING_NETWORKS, REMOVING_VOLUMES, REMOVING_PORT_PROFILES));
        }
    }

    public ContainerHostRemovalTaskService() {
        super(ContainerHostRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ContainerHostRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            disableContainerHosts(state, null);
            break;
        case SUSPENDING_HOSTS:
            break;
        case SUSPENDED_HOSTS:
            queryContainers(state);
            break;
        case REMOVING_CONTAINERS:
            break;
        case REMOVED_CONTAINERS:
            queryNetworks(state);
            break;
        case REMOVING_NETWORKS:
            break;
        case REMOVED_NETWORKS:
            queryVolumes(state);
            break;
        case REMOVING_VOLUMES:
            break;
        case REMOVED_VOLUMES:
            filterKubernetesHosts(state);
            break;
        case REMOVING_KUBERNETES_RESOURCES:
            break;
        case REMOVED_KUBERNETES_RESOURCES:
            queryPortProfiles(state);
            break;
        case REMOVING_PORT_PROFILES:
            break;
        case REMOVED_PORT_PROFILES:
            removeTrustCerts(state);
            break;
        case REMOVING_CERTIFICATES:
            break;
        case REMOVED_CERTIFICATES:
            ConfigurationUtil
                    .getConfigProperty(this, ConfigurationUtil.ALLOW_HOST_EVENTS_SUBSCRIPTIONS, (String allow) -> {
                        if (Boolean.valueOf(allow)) {
                            unsubscribeHostForEvents(state).thenAccept((ignore) -> {
                                removeHosts(state, null);
                            });
                        } else {
                            logInfo("Skipped host events unsubscription.");
                            removeHosts(state, null);
                        }
                    });
            break;
        case REMOVING_HOSTS:
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

    private void disableContainerHosts(ContainerHostRemovalTaskState state, String subTaskLink) {
        if (subTaskLink == null) {
            CounterSubTaskState subTaskInitState = new CounterSubTaskState();
            subTaskInitState.completionsRemaining = state.resourceLinks.size();
            subTaskInitState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();
            subTaskInitState.serviceTaskCallback = ServiceTaskCallback.create(
                    getSelfLink(),
                    TaskStage.STARTED, SubStage.SUSPENDED_HOSTS,
                    TaskStage.STARTED, SubStage.ERROR);

            CounterSubTaskService.createSubTask(this, subTaskInitState,
                    (link) -> disableContainerHosts(state, link));
            return;
        }
        ComputeState computeState = new ComputeState();
        computeState.powerState = PowerState.SUSPEND;

        try {
            AtomicBoolean error = new AtomicBoolean();
            for (String resourceLink : state.resourceLinks) {
                sendRequest(Operation
                        .createPatch(this, resourceLink)
                        .setBody(computeState)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                if (error.compareAndSet(false, true)) {
                                    logWarning("Failed suspending ComputeState: %s", resourceLink);
                                    completeSubTasksCounter(subTaskLink, e);
                                }
                                return;
                            }
                            completeSubTasksCounter(subTaskLink, null);
                        }));
            }
            proceedTo(SubStage.SUSPENDING_HOSTS);
        } catch (Throwable e) {
            failTask("Unexpected exception while suspending container host", e);
        }
    }

    private void queryContainers(ContainerHostRemovalTaskState state) {
        QueryTask containerQuery = QueryUtil.buildQuery(ContainerState.class, true);

        QueryUtil.addListValueClause(containerQuery,
                ContainerState.FIELD_NAME_PARENT_LINK, state.resourceLinks);

        Set<String> containerLinks = new HashSet<>();
        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class).query(
                containerQuery, (r) -> {
                    if (r.hasException()) {
                        failTask("Failure retrieving query results", r.getException());
                        return;
                    } else if (r.hasResult()) {
                        containerLinks.add(r.getDocumentSelfLink());
                    } else {
                        if (containerLinks.isEmpty()) {
                            queryNetworks(state);
                            return;
                        }

                        removeContainers(state, containerLinks);
                    }
                });
    }

    private void removeContainers(ContainerHostRemovalTaskState state,
            Set<String> containerSelfLinks) {

        // run a sub task for removing the containers
        ContainerRemovalTaskState containerRemovalTask = new ContainerRemovalTaskState();
        containerRemovalTask.resourceLinks = containerSelfLinks;
        containerRemovalTask.removeOnly = true;
        containerRemovalTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.REMOVED_CONTAINERS,
                TaskStage.STARTED, SubStage.ERROR);
        containerRemovalTask.requestTrackerLink = state.requestTrackerLink;

        Operation startPost = Operation
                .createPost(this, ContainerRemovalTaskFactoryService.SELF_LINK)
                .setBody(containerRemovalTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container removal task", e);
                        return;
                    }

                    proceedTo(SubStage.REMOVING_CONTAINERS);
                });
        sendRequest(startPost);
    }

    private void queryNetworks(ContainerHostRemovalTaskState state) {
        QueryTask networkQuery = QueryUtil.buildQuery(ContainerNetworkState.class, false);

        String parentLinksItemField = QueryTask.QuerySpecification
                .buildCollectionItemName(ContainerNetworkState.FIELD_NAME_PARENT_LINKS);
        QueryUtil.addListValueClause(networkQuery, parentLinksItemField, state.resourceLinks);
        QueryUtil.addExpandOption(networkQuery);

        Set<String> networkLinks = new HashSet<>();
        new ServiceDocumentQuery<ContainerNetworkState>(getHost(), ContainerNetworkState.class)
                .query(networkQuery, (r) -> {
                    if (r.hasException()) {
                        failTask("Failure retrieving query results", r.getException());
                        return;
                    } else if (r.hasResult()) {
                        ContainerNetworkState networkState = r.getResult();

                        List<String> parentLinks = new ArrayList<>(networkState.parentLinks);
                        parentLinks.removeAll(state.resourceLinks);

                        if (parentLinks.isEmpty()) {
                            networkLinks.add(networkState.documentSelfLink);
                        } else {
                            networkState.parentLinks = parentLinks;
                            updateNetworkParentLinks(networkState);
                        }
                    } else {
                        if (networkLinks.isEmpty()) {
                            queryVolumes(state);
                            return;
                        }

                        removeNetworks(state, networkLinks);
                    }
                });
    }

    private void updateNetworkParentLinks(ContainerNetworkState networkState) {
        ContainerNetworkState patchNetworkState = new ContainerNetworkState();
        patchNetworkState.parentLinks = networkState.parentLinks;

        if ((networkState.originatingHostLink != null) && !networkState.parentLinks.isEmpty()
                && (!networkState.parentLinks.contains(networkState.originatingHostLink))) {
            // set another parent like the "owner" of the network
            patchNetworkState.originatingHostLink = networkState.parentLinks.get(0);
        }

        sendRequest(Operation
                .createPatch(this, networkState.documentSelfLink)
                .setBody(patchNetworkState)
                .setCompletion((o, ex) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        logFine("Network %s not found to be updated its parent links.",
                                networkState.documentSelfLink);
                    } else if (ex != null) {
                        logWarning("Failed to update network %s parent links: %s",
                                networkState.documentSelfLink, Utils.toString(ex));
                    } else {
                        logInfo("Updated network parent links: %s", networkState.documentSelfLink);
                    }
                }));
    }

    private void removeNetworks(ContainerHostRemovalTaskState state,
            Set<String> networkSelfLinks) {

        // run a sub task for removing the networks
        ContainerNetworkRemovalTaskState networkRemovalTask = new ContainerNetworkRemovalTaskState();
        networkRemovalTask.resourceLinks = networkSelfLinks;
        networkRemovalTask.removeOnly = true;
        networkRemovalTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.REMOVED_NETWORKS,
                TaskStage.STARTED, SubStage.ERROR);
        networkRemovalTask.requestTrackerLink = state.requestTrackerLink;

        Operation startPost = Operation
                .createPost(this, ContainerNetworkRemovalTaskService.FACTORY_LINK)
                .setBody(networkRemovalTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container network removal task", e);
                        return;
                    }

                    proceedTo(SubStage.REMOVING_NETWORKS);
                });
        sendRequest(startPost);
    }

    private void queryVolumes(ContainerHostRemovalTaskState state) {
        QueryTask volumeQuery = QueryUtil.buildQuery(ContainerVolumeState.class, false);

        QueryUtil.addListValueClause(volumeQuery,
                ContainerVolumeState.FIELD_NAME_ORIGINATING_HOST_LINK, state.resourceLinks);

        Set<String> volumeLinks = new HashSet<>();
        new ServiceDocumentQuery<ContainerVolumeState>(getHost(), ContainerVolumeState.class).query(
                volumeQuery, (r) -> {
                    if (r.hasException()) {
                        failTask("Failure retrieving query results", r.getException());
                        return;
                    } else if (r.hasResult()) {
                        volumeLinks.add(r.getDocumentSelfLink());
                    } else {
                        if (volumeLinks.isEmpty()) {
                            filterKubernetesHosts(state);
                            return;
                        }

                        removeVolumes(state, volumeLinks);
                    }
                });
    }

    private void removeVolumes(ContainerHostRemovalTaskState state, Set<String> volumeSelfLinks) {
        // run a sub task for removing the volumes
        ContainerVolumeRemovalTaskState volumeRemovalTask = new ContainerVolumeRemovalTaskState();
        volumeRemovalTask.resourceLinks = volumeSelfLinks;
        volumeRemovalTask.removeOnly = true;
        volumeRemovalTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.REMOVED_VOLUMES,
                TaskStage.STARTED, SubStage.ERROR);
        volumeRemovalTask.requestTrackerLink = state.requestTrackerLink;

        Operation startPost = Operation
                .createPost(this, ContainerVolumeRemovalTaskService.FACTORY_LINK)
                .setBody(volumeRemovalTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container volume removal task", e);
                        return;
                    }

                    proceedTo(SubStage.REMOVING_VOLUMES);
                });
        sendRequest(startPost);
    }

    private void filterKubernetesHosts(ContainerHostRemovalTaskState state) {
        filterKubernetesHostLinks(this, state.resourceLinks,
                (kubernetesHostLinks, errors) -> {
                    if (errors != null) {
                        failTask("Couldn't filter kubernetes host links: %s",
                                new IllegalStateException(Utils.toString(errors)));
                        return;
                    }
                    removeKubernetesResources(state, kubernetesHostLinks);
                });

    }

    private void removeKubernetesResources(ContainerHostRemovalTaskState state, Set<String>
            kubernetesHostLinks) {
        if (kubernetesHostLinks == null || kubernetesHostLinks.isEmpty()) {
            queryPortProfiles(state);
            return;
        }

        CompositeKubernetesRemovalTaskState kubernetesRemovalTask = new CompositeKubernetesRemovalTaskState();
        kubernetesRemovalTask.resourceLinks = kubernetesHostLinks;
        kubernetesRemovalTask.cleanupOnly = true;
        kubernetesRemovalTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.REMOVED_KUBERNETES_RESOURCES,
                TaskStage.STARTED, SubStage.ERROR);
        kubernetesRemovalTask.requestTrackerLink = state.requestTrackerLink;

        Operation startPost = Operation
                .createPost(this, CompositeKubernetesRemovalTaskService.FACTORY_LINK)
                .setBody(kubernetesRemovalTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating kubernetes composite removal task", e);
                        return;
                    }

                    proceedTo(SubStage.REMOVING_KUBERNETES_RESOURCES);
                });
        sendRequest(startPost);
    }

    private void queryPortProfiles(ContainerHostRemovalTaskState state) {
        QueryTask q = QueryUtil
                .buildQuery(HostPortProfileService.HostPortProfileState.class, false);
        QueryUtil.addListValueClause(q, HostPortProfileService.HostPortProfileState.FIELD_HOST_LINK,
                state.resourceLinks);
        ServiceDocumentQuery<HostPortProfileService.HostPortProfileState> query = new ServiceDocumentQuery<>(
                getHost(), HostPortProfileService.HostPortProfileState.class);
        QueryUtil.addBroadcastOption(q);
        ArrayList<String> hostPortProfileLinks = new ArrayList<>();
        query.query(q, (r) -> {
            if (r.hasException()) {
                failTask("Failure retrieving query results", r.getException());
                return;
            } else if (r.hasResult()) {
                hostPortProfileLinks.add(r.getDocumentSelfLink());
            } else {
                // if there are no host port profiles, go to the next stage
                if (hostPortProfileLinks.isEmpty()) {
                    proceedTo(SubStage.REMOVED_PORT_PROFILES);
                    return;
                }

                removePortProfiles(state, hostPortProfileLinks, null);
                proceedTo(SubStage.REMOVING_PORT_PROFILES);
            }
        });
    }

    private void removePortProfiles(ContainerHostRemovalTaskState state,
            ArrayList<String> hostPortProfileLinks, String subTaskLink) {
        if (subTaskLink == null) {
            // create counter subtask to remove every host port profile. Go to REMOVED_PORT_PROFILES when complete
            createCounterSubTask(state, hostPortProfileLinks.size(), SubStage.REMOVED_PORT_PROFILES,
                    (link) -> removePortProfiles(state, hostPortProfileLinks, link));
            return;
        }

        // delete host port profiles
        for (String profileLink : hostPortProfileLinks) {
            sendRequest(Operation.createDelete(this, profileLink)
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            failTask("Failed to delete host port profile: %s", ex);
                            return;
                        }
                        completeSubTasksCounter(subTaskLink, null);
                    }));
        }
    }

    private void removeHosts(ContainerHostRemovalTaskState state, String subTaskLink) {
        if (subTaskLink == null && !state.skipComputeHostRemoval) {
            createCounterSubTask(state, state.resourceLinks.size(),
                    (link) -> removeHosts(state, link));
            return;
        }

        try {
            logInfo("Starting delete of %d container hosts", state.resourceLinks.size());

            // Notify the data collection service first
            URI uri = UriUtils.buildUri(getHost(),
                    ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK);
            ContainerHostDataCollectionService.ContainerHostDataCollectionState dataCollectionState
                    = new ContainerHostDataCollectionService.ContainerHostDataCollectionState();
            dataCollectionState.computeContainerHostLinks = state.resourceLinks;
            dataCollectionState.remove = true;
            sendRequest(Operation.createPatch(uri)
                    .setBody(dataCollectionState)
                    .setCompletion((o1, ex1) -> {
                        if (ex1 != null) {
                            logWarning("Failed to update host data collection: %s",
                                    ex1.getMessage());
                        }

                        if (state.skipComputeHostRemoval) {
                            proceedTo(SubStage.COMPLETED);
                            return;
                        }

                        for (String resourceLink : state.resourceLinks) {
                            sendRequest(Operation
                                    .createDelete(this, resourceLink)
                                    .setBody(new ServiceDocument())
                                    .setCompletion((o, e) -> {
                                        if (e != null) {
                                            logWarning("Failed deleting ComputeState: %s",
                                                    resourceLink);
                                            completeSubTasksCounter(subTaskLink, e);
                                            return;
                                        }
                                        completeSubTasksCounter(subTaskLink, null);
                                    }));
                        }
                    }));
            proceedTo(SubStage.REMOVING_HOSTS);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting container host instances", e);
        }
    }

    private DeferredResult<List<String>> collectTrustCert(ContainerHostRemovalTaskState state) {
        List<DeferredResult<String>> a = state.resourceLinks.stream().map(resourceLink ->
                sendWithDeferredResult(Operation
                        .createGet(this, resourceLink), ComputeState.class)
                        .thenApply(o ->

                                PropertyUtils
                                        .getPropertyString(o.customProperties,
                                                ComputeConstants.HOST_TRUST_CERTS_PROP_NAME)
                                        .orElse(null))

        ).collect(Collectors.toList());

        return DeferredResult.allOf(a);
    }

    private void removeTrustCerts(ContainerHostRemovalTaskState state) {
        QueryTask queryTask = new QueryTask();
        queryTask.querySpec = new QueryTask.QuerySpecification();
        queryTask.taskInfo.isDirect = true;

        Query q = Query.Builder.create()
                .addFieldClause(QuerySpecification
                                .buildCompositeFieldName(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                        ComputeConstants.HOST_TRUST_CERTS_PROP_NAME),
                        UriUtils.URI_WILDCARD_CHAR, MatchType.WILDCARD,
                        Occurance.MUST_NOT_OCCUR).build();
        q.addBooleanClause(createKindClause(ComputeState.class));

        queryTask.querySpec.query.addBooleanClause(q);

        sendWithDeferredResult(Operation
                .createPost(UriUtils.buildUri(getHost(),
                        ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(queryTask)
                .setReferer(getHost().getUri()), QueryTask.class)
                .thenAccept(qrt -> {
                    if (qrt.results.documentCount == 0) {
                        doRemoveTrustCerts(state);

                    } else {
                        proceedTo(SubStage.REMOVED_CERTIFICATES);
                    }
                }).exceptionally(e -> {
                    logWarning("Failed to remove unused trust certificate.", e);
                    proceedTo(SubStage.REMOVED_CERTIFICATES);
                    return null;
                });
    }

    private void doRemoveTrustCerts(ContainerHostRemovalTaskState state) {

        collectTrustCert(state)
                .thenAccept(trustCertSelfLinks -> {
                    List l = trustCertSelfLinks.stream().map(trustCertSelfLink -> {

                        if (trustCertSelfLink == null || !trustCertSelfLink.startsWith
                                (SslTrustCertificateService.FACTORY_LINK)) {
                            return DeferredResult.completed(null);
                        }

                        Query.Builder queryBuilder = Query.Builder.create()
                                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                        ComputeConstants.HOST_TRUST_CERTS_PROP_NAME,
                                        trustCertSelfLink);

                        Query query = queryBuilder.build();
                        QueryTask queryTask = QueryUtil.buildQuery(ComputeState.class, true, query);
                        QueryUtil.addExpandOption(queryTask);

                        return sendWithDeferredResult(Operation
                                .createPost(UriUtils.buildUri(getHost(),
                                        ServiceUriPaths.CORE_QUERY_TASKS))
                                .setBody(queryTask)
                                .setReferer(getHost().getUri()), QueryTask.class)
                                .thenCompose(qrt -> {
                                    if (qrt.results.documentCount == 0 || state.resourceLinks
                                            .containsAll(qrt.results.documentLinks)) {
                                        return sendWithDeferredResult(Operation
                                                .createDelete(UriUtils.buildUri(getHost(),
                                                        trustCertSelfLink))
                                                .setReferer(getHost().getUri()), QueryTask.class);
                                    }
                                    return DeferredResult.completed(qrt);
                                }).exceptionally(e -> {
                                    logWarning("Failed to remove unused trust certificate.", e);
                                    proceedTo(SubStage.REMOVED_CERTIFICATES);
                                    return null;
                                });
                    }).collect(Collectors.toList());

                    DeferredResult.allOf(l)
                            .thenAccept(oo -> {
                                proceedTo(SubStage.REMOVED_CERTIFICATES);
                            });
                });

    }

    private DeferredResult<List<Operation>> unsubscribeHostForEvents(ContainerHostRemovalTaskState state) {

        List<DeferredResult<Operation>> deferredResults = new ArrayList<>(state.resourceLinks.size());
        for (String resourceLink : state.resourceLinks) {
            AdapterRequest request = new AdapterRequest();
            request.operationTypeId = ContainerHostOperationType.EVENTS_UNSUBSCRIBE.id;
            request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
            request.resourceReference = UriUtils.buildUri(getHost(), resourceLink);
            URI adapterManagementReference = UriUtils.buildUri(getHost(), ManagementUriParts.ADAPTER_DOCKER_HOST);

            deferredResults.add(getHost()
                    .sendWithDeferredResult(Operation
                            .createPatch(adapterManagementReference)
                            .setReferer(this.getUri())
                            .setBodyNoCloning(request))
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            logWarning("Failed to unsubscribe for host events.");
                        }
                    }));
        }

        return DeferredResult.allOf(deferredResults);
    }
}
