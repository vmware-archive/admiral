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

package com.vmware.admiral.compute.cluster;

import static java.util.EnumSet.of;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.SubscriptionManager;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.PlacementZoneConstants.PlacementZoneType;
import com.vmware.admiral.compute.PlacementZoneUtil;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

public class ClusterService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CLUSTERS;
    public static final String HOSTS_URI_PATH_SEGMENT = "hosts";

    private static final Pattern PATTERN_CLUSTER_LINK = Pattern
            .compile(String.format("^%s\\/?$", SELF_LINK));
    private static final Pattern PATTERN_SINGLE_CLUSTER_OPERATION_LINK = Pattern
            .compile(String.format("^%s\\/[^\\/]+\\/?$", SELF_LINK));
    private static final Pattern PATTERN_CLUSTER_HOSTS_OPERATION_LINK = Pattern
            .compile(String.format("^%s\\/[^\\/]+\\/%s\\/?$", SELF_LINK, HOSTS_URI_PATH_SEGMENT));
    private static final Pattern PATTERN_CLUSTER_SINGLE_HOST_OPERATION_LINK = Pattern
            .compile(String.format("^%s\\/[^\\/]+\\/%s\\/[^\\/]+\\/?$", SELF_LINK,
                    HOSTS_URI_PATH_SEGMENT));

    private static final Pattern PATTERN_PROJECT_TENANT_LINK = Pattern
            .compile(String.format("^%s\\/[^\\/]+", ManagementUriParts.PROJECTS));
    private static final Pattern PATTERN_TENANT_LINK = Pattern
            .compile(String.format("^\\%s\\/[^\\/]+\\%s\\/[^\\/]+",
                    MultiTenantDocument.TENANTS_PREFIX, MultiTenantDocument.GROUP_IDENTIFIER));

    public static final String HOST_NOT_IN_THIS_CLUSTER_EXCEPTION_TEMPLATE = "No host with id %s found in cluster with id %s";

    public static final String CLUSTER_ID_PATH_SEGMENT = "clusterId";
    public static final String CLUSTER_HOST_ID_PATH_SEGMENT = "hostId";
    public static final String CLUSTER_PATH_SEGMENT_TEMPLATE = SELF_LINK + "/{clusterId}" + "/"
            + HOSTS_URI_PATH_SEGMENT
            + "/{hostId}";
    public static final String CLUSTER_DETAILS_CUSTOM_PROP = "__clusterDetails";
    public static final String CLUSTER_NAME_CUSTOM_PROP = "__clusterName";
    public static final String CLUSTER_CREATION_TIME_MICROS_CUSTOM_PROP = "__clusterCreationTimeMicros";

    public static final String HOSTS_FILTER_QUERY_PARAM = "$hostsFilter";
    public static final String CUSTOM_OPTIONS_QUERY_PARAM = "customOptions";

    public ClusterService() {
        super(ClusterDto.class);
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    public enum ClusterType {
        DOCKER, VCH
    }

    public enum ClusterStatus {
        ON, OFF, DISABLED, WARNING
    }

    @SuppressWarnings("serial")
    public class CertificateNotTrustedException extends Exception {
        private SslTrustCertificateState certificate;

        CertificateNotTrustedException(SslTrustCertificateState c) {
            super();
            this.certificate = c;
        }

        public SslTrustCertificateState getCertificate() {
            return certificate;
        }
    }

    public static class ClusterDto extends ServiceDocument {

        public ClusterDto() {
            nodeLinks = new LinkedList<>();
        }

        /** The name of a given cluster. */
        public String name;

        /** The type of hosts the cluster contains. */
        public ClusterType type;

        /** The status of the cluster. */
        public ClusterStatus status;

        /** (Optional) the address of the VCH cluster. */
        public String address;

        /** The moment of creation of a given cluster. */
        public Long clusterCreationTimeMicros;

        /** The details of a given cluster. */
        public String details;

        /** The number of containers in the cluster. */
        public int containerCount;

        public long totalMemory;

        public long memoryUsage;

        /** Document links of the {@link ComputeState}s that are part of this cluster */
        public List<String> nodeLinks;

        public Map<String, ComputeState> nodes;

        // TODO do we need that and how do we compute that for docker clusters? (VCH reports this,
        // but docker host doesn't)
        public double totalCpu;

        public double cpuUsage;
    }

    public static class ContainerHostRemovalTaskState extends TaskService.TaskServiceState {

        public static final String RESOURCE_TYPE_CONTAINER_HOST = "CONTAINER_HOST";
        public static final String OPERATION_REMOVE_RESOURCE = "REMOVE_RESOURCE";

        public ContainerHostRemovalTaskState(List<String> resourceLinks) {
            resourceType = RESOURCE_TYPE_CONTAINER_HOST;
            operation = OPERATION_REMOVE_RESOURCE;
            this.resourceLinks = resourceLinks;
        }

        public String resourceType;
        public String operation;
        public List<String> resourceLinks;
    }

    @Override
    public void handlePost(Operation post) {
        if (isOperationOverAllClustes(post)) {
            createCluster(post);
            return;
        }
        if (isOperationOverAllHostsInSingleCluster(post)) {
            createHostInCluster(post);
            return;
        }
        post.fail(Operation.STATUS_CODE_NOT_FOUND);
    }

    @Override
    public void handlePatch(Operation patch) {
        try {
            validateClusterPatch(patch);
        } catch (Throwable ex) {
            logWarning("Failed to verify cluster PATCH: %s", Utils.toString(ex));
            patch.fail(ex);
            return;
        }

        String clusterId = UriUtils.parseUriPathSegments(patch.getUri(),
                CLUSTER_PATH_SEGMENT_TEMPLATE).get(CLUSTER_ID_PATH_SEGMENT);

        String projectLink = OperationUtil.extractProjectFromHeader(patch);

        String pathPZId = UriUtils.buildUriPath(
                ElasticPlacementZoneConfigurationService.SELF_LINK,
                ResourcePoolService.FACTORY_LINK, clusterId);
        ResourcePoolState resourcePool = new ResourcePoolState();
        resourcePool.documentSelfLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK,
                clusterId);
        ClusterDto clusterDto = patch.getBody(ClusterDto.class);
        resourcePool.name = clusterDto.name;
        resourcePool.customProperties = new HashMap<>();
        resourcePool.customProperties.put(
                ClusterService.CLUSTER_DETAILS_CUSTOM_PROP,
                clusterDto.details);

        ElasticPlacementZoneConfigurationState placementZone = new ElasticPlacementZoneConfigurationState();
        placementZone.resourcePoolState = resourcePool;
        sendWithDeferredResult(
                Operation
                        .createPatch(UriUtils.buildUri(getHost(), pathPZId))
                        .setReferer(getHost().getUri())
                        .setBody(placementZone),
                ElasticPlacementZoneConfigurationState.class)
                        .thenCompose(epzConfigState -> getInfoFromHostsWihtinOnePlacementZone(
                                projectLink, epzConfigState))
                        .thenAccept(clusterDtoList -> {
                            if (!clusterDtoList.isEmpty()) {
                                patch.setBody(clusterDtoList.get(0));
                            }
                        })
                        .whenCompleteNotify(patch);
    }

    @Override
    public void handleDelete(Operation delete) {
        if (isOperationOverSingleCluster(delete)) {
            deleteCluster(delete);
            return;
        }

        if (isOperationOverSingleHostInSingleCluster(delete)) {
            deleteHostInCluster(delete);
            return;
        }
        delete.fail(Operation.STATUS_CODE_NOT_FOUND);
    }

    @Override
    public void handleGet(Operation get) {
        if (isOperationOverAllClustes(get)) {
            getAllClusters(get);
            return;
        }
        if (isOperationOverSingleCluster(get)) {
            getSingleCluster(get);
            return;
        }
        if (isOperationOverAllHostsInSingleCluster(get)) {
            getAllHostsInSingleCluster(get);
            return;
        }
        if (isOperationOverSingleHostInSingleCluster(get)) {
            getSingleHostInSingleCluster(get);
            return;
        }
        get.fail(Operation.STATUS_CODE_NOT_FOUND);
    }

    private boolean isOperationOverAllClustes(Operation op) {
        return PATTERN_CLUSTER_LINK.matcher(op.getUri().getPath()).matches();
    }

    private boolean isOperationOverSingleCluster(Operation op) {
        return PATTERN_SINGLE_CLUSTER_OPERATION_LINK.matcher(op.getUri().getPath()).matches();
    }

    private boolean isOperationOverAllHostsInSingleCluster(Operation op) {
        return PATTERN_CLUSTER_HOSTS_OPERATION_LINK.matcher(op.getUri().getPath()).matches();
    }

    private boolean isOperationOverSingleHostInSingleCluster(Operation op) {
        return PATTERN_CLUSTER_SINGLE_HOST_OPERATION_LINK.matcher(op.getUri().getPath()).matches();
    }

    private void getAllClusters(Operation get) {
        boolean expand = UriUtils.hasODataExpandParamValue(get.getUri());
        URI placementZoneUri = UriUtils.buildUri(getHost(),
                ElasticPlacementZoneConfigurationService.SELF_LINK, get.getUri().getQuery());

        placementZoneUri = UriUtils.extendUriWithQuery(placementZoneUri,
                UriUtils.URI_PARAM_ODATA_EXPAND_NO_DOLLAR_SIGN, "");

        String projectLink = OperationUtil.extractProjectFromHeader(get);

        Operation getPlacementZone = Operation
                .createGet(placementZoneUri)
                .setReferer(getUri());

        if (projectLink != null && !projectLink.isEmpty()) {
            getPlacementZone.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, projectLink);
            OperationUtil.transformProjectHeaderToFilterQuery(getPlacementZone);
        }

        sendWithDeferredResult(getPlacementZone, ServiceDocumentQueryResult.class)
                .thenCompose(queryResult -> getInfoFromHostsWihtinPlacementZone(projectLink,
                        queryResult, get))
                .thenAccept(clusterDtoList -> {
                    Map<String, Object> ClusterDtoMap = clusterDtoList.stream()
                            .collect(Collectors.toMap(
                                    clusterDto -> clusterDto.documentSelfLink,
                                    Function.identity()));

                    ServiceDocumentQueryResult queryResult = new ServiceDocumentQueryResult();
                    queryResult.documentLinks = new LinkedList<>(
                            ClusterDtoMap.keySet());
                    queryResult.documentCount = Long.valueOf(ClusterDtoMap.size());
                    if (expand) {
                        queryResult.documents = ClusterDtoMap;
                    }
                    get.setBody(queryResult);
                })
                .whenCompleteNotify(get);
    }

    private void getSingleCluster(Operation get) {
        String clusterId = UriUtils.parseUriPathSegments(get.getUri(),
                CLUSTER_PATH_SEGMENT_TEMPLATE).get(CLUSTER_ID_PATH_SEGMENT);
        String pathPZId = UriUtils.buildUriPath(
                ElasticPlacementZoneConfigurationService.SELF_LINK,
                ResourcePoolService.FACTORY_LINK, clusterId);
        String projectLink = OperationUtil.extractProjectFromHeader(get);

        URI elasticPlacementZoneConfigurationUri = UriUtils.buildUri(getHost(),
                pathPZId, get.getUri().getQuery());
        sendWithDeferredResult(Operation
                .createGet(
                        UriUtils.buildExpandLinksQueryUri(elasticPlacementZoneConfigurationUri))
                .setReferer(getUri()),
                ElasticPlacementZoneConfigurationState.class)
                        .thenCompose(epzConfigState -> getInfoFromHostsWihtinOnePlacementZone(
                                projectLink, epzConfigState))
                        .thenAccept(clusterDtoList -> {
                            if (!clusterDtoList.isEmpty()) {
                                get.setBody(clusterDtoList.get(0));
                            }
                        })
                        .whenCompleteNotify(get);

    }

    private void getAllHostsInSingleCluster(Operation get) {
        ClusterUtils.getHostsWithinPlacementZone(get, getHost());
    }

    private void getSingleHostInSingleCluster(Operation get) {
        Map<String, String> uriParams = UriUtils.parseUriPathSegments(get.getUri(),
                CLUSTER_PATH_SEGMENT_TEMPLATE);
        String clusterId = uriParams.get(CLUSTER_ID_PATH_SEGMENT);
        String hostId = uriParams.get(CLUSTER_HOST_ID_PATH_SEGMENT);

        String hostDocumentSelfLink = UriUtils.buildUriPath(
                ComputeService.FACTORY_LINK, hostId);

        sendWithDeferredResult(
                Operation.createGet(UriUtils.buildUri(getHost(), hostDocumentSelfLink))
                        .setReferer(getHost().getUri()),
                ComputeState.class)
                        .thenAccept(cs -> {
                            if (clusterId.equals(Service.getId(cs.resourcePoolLink))) {
                                get.setBody(cs);
                            } else {
                                get.fail(new ServiceNotFoundException(String.format(
                                        HOST_NOT_IN_THIS_CLUSTER_EXCEPTION_TEMPLATE, hostId,
                                        clusterId)));
                            }
                        })
                        .whenCompleteNotify(get);

    }

    private DeferredResult<List<ClusterDto>> getInfoFromHostsWihtinPlacementZone(String projectLink,
            ServiceDocumentQueryResult queryResult, Operation get) {
        Map<String, ElasticPlacementZoneConfigurationState> ePZstates = QueryUtil
                .extractQueryResult(
                        queryResult, ElasticPlacementZoneConfigurationState.class);
        List<DeferredResult<ClusterDto>> clusterDtoList = ePZstates.keySet().stream()
                .map(key -> ClusterUtils.getHostsWithinPlacementZone(ePZstates.get(key)
                                .resourcePoolState.documentSelfLink, projectLink, get, getHost())
                        .thenApply(computeStates -> {
                            return ClusterUtils.placementZoneAndItsHostsToClusterDto(
                                    ePZstates.get(key).resourcePoolState, computeStates);
                        }))
                .collect(Collectors.toList());
        return DeferredResult.allOf(clusterDtoList);
    }

    private DeferredResult<List<ClusterDto>> getInfoFromHostsWihtinOnePlacementZone(
            String projectLink, ElasticPlacementZoneConfigurationState queryResult) {
        Map<String, ElasticPlacementZoneConfigurationState> ePZstates = new HashMap<>();
        ePZstates.put(queryResult.documentSelfLink, queryResult);
        List<DeferredResult<ClusterDto>> clusterDtoList = ePZstates.keySet().stream()
                .map(key -> ClusterUtils.getHostsWithinPlacementZone(
                        ePZstates.get(key).resourcePoolState.documentSelfLink, projectLink,
                        getHost())
                        .thenApply(computeStates -> {
                            return ClusterUtils.placementZoneAndItsHostsToClusterDto(
                                    ePZstates.get(key).resourcePoolState, computeStates);
                        }))
                .collect(Collectors.toList());
        return DeferredResult.allOf(clusterDtoList);
    }

    private DeferredResult<Operation> deleteHostsWihtinOnePlacementZone(ServiceHost host,
            String resourcePoolLink, String projectLink) {

        DeferredResult<Operation> deleteNodesDR = ClusterUtils.getHostsWithinPlacementZone(
                resourcePoolLink, projectLink, getHost())
                .thenCompose(computeStates -> {

                    if (computeStates.isEmpty()) {
                        return DeferredResult.completed(null);
                    }
                    List<String> hostLinksList = computeStates.stream()
                            .map(computeState -> {
                                return computeState.documentSelfLink;
                            }).collect(Collectors.toList());
                    DeferredResult<Operation> deleteNodesRequestOperation = sendWithDeferredResult(
                            Operation.createPost(
                                    UriUtils.buildUri(host, ManagementUriParts.REQUESTS))
                                    .setReferer(host.getUri())
                                    .forceRemote()
                                    .setBody(new ContainerHostRemovalTaskState(
                                            hostLinksList)));
                    return deleteNodesRequestOperation;
                });
        return deleteNodesDR;
    }

    private void createCluster(Operation post) {

        try {
            validateCreateClusterPost(post);
        } catch (Throwable ex) {
            logWarning("Failed to verify cluster creation POST: %s", Utils.toString(ex));
            post.fail(ex);
            return;
        }

        // this will contain the IDs of the auto-generated resources
        HashSet<String> generatedResourcesIds = new HashSet<>(2);
        ContainerHostSpec hostSpec = post.getBody(ContainerHostSpec.class);

        generatePlacementZoneAndPlacement(hostSpec, generatedResourcesIds)
                .thenCompose((zoneAndPlacement) -> {
                    return addContainerHost(hostSpec)
                            .thenApply((hostState) -> new Pair<>(zoneAndPlacement.left, hostState));
                })
                .thenAccept((zoneAndHost) -> {
                    LinkedList<ComputeState> a = new LinkedList<ComputeState>();
                    a.add(zoneAndHost.right);
                    post.setBody(
                            ClusterUtils.placementZoneAndItsHostsToClusterDto(zoneAndHost.left, a));
                    post.complete();
                }).exceptionally((ex) -> {
                    if (ex.getCause() instanceof CertificateNotTrustedException) {
                        post.setBody(
                                ((CertificateNotTrustedException) ex.getCause()).certificate);
                        post.complete();
                    } else {
                        logWarning("Create cluster failed: %s", Utils.toString(ex));
                        post.fail(ex.getCause());
                    }
                    ContainerHostUtil.cleanupAutogeneratedResources(this, generatedResourcesIds);
                    return null;
                });
    }

    private void createHostInCluster(Operation post) {
        String clusterId = UriUtils.parseUriPathSegments(post.getUri(),
                CLUSTER_PATH_SEGMENT_TEMPLATE).get(CLUSTER_ID_PATH_SEGMENT);
        String resourcePoolDocumentSelfLink = UriUtils.buildUriPath(
                ResourcePoolService.FACTORY_LINK, clusterId);

        if (!post.hasBody()) {
            throw new LocalizableValidationException(
                    "ContainerHostSpec body is required", "compute.host.spec.is.required");
        }
        ContainerHostSpec hostSpec = post.getBody(ContainerHostSpec.class);

        ContainerHostSpec hs = new ContainerHostSpec();
        hs.hostState = new ComputeState();
        hs.hostState.address = hostSpec.hostState.address;
        hs.hostState.tenantLinks = hostSpec.getHostTenantLinks();
        //        hs.hostState.id = hostSpec.hostState.address;
        hs.hostState.resourcePoolLink = resourcePoolDocumentSelfLink;
        hs.acceptCertificate = hostSpec.acceptCertificate;
        hs.hostState.customProperties = new HashMap<>();
        hs.hostState.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());
        hs.hostState.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.DOCKER.name());
        hs.hostState.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                PropertyUtils.getPropertyString(hostSpec.hostState.customProperties,
                        ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME)
                        .orElse(null));

        sendWithDeferredResult(Operation
                .createPut(UriUtils.buildUri(getHost(),
                        ContainerHostService.SELF_LINK))
                .setReferer(getUri())
                .setBody(hs))
                        .thenAccept(cs -> {
                            post.setBody(cs.getBodyRaw());
                        }).exceptionally(ex -> {
                            if (ex.getCause() instanceof CertificateNotTrustedException) {
                                post.setBody(
                                        ((CertificateNotTrustedException) ex
                                                .getCause()).certificate);
                                post.complete();
                            } else {
                                logWarning("Create host in cluster %s failed: %s", clusterId,
                                        Utils.toString(ex));
                                post.fail(ex.getCause());
                            }
                            return null;
                        }).whenCompleteNotify(post);

    }

    private void validateCreateClusterPost(Operation post) {
        if (!post.hasBody()) {
            throw new LocalizableValidationException(
                    "ContainerHostSpec body is required", "compute.host.spec.is.required");
        }

        ContainerHostSpec hostSpec = post.getBody(ContainerHostSpec.class);
        List<String> hostTenantLinks = hostSpec.getHostTenantLinks();

        if (hostTenantLinks == null || hostTenantLinks.isEmpty()
                || (hostTenantLinks.stream().noneMatch(PATTERN_PROJECT_TENANT_LINK.asPredicate())
                        && hostTenantLinks.stream().noneMatch(PATTERN_TENANT_LINK.asPredicate()))) {
            throw new LocalizableValidationException(
                    "Project context is required", "auth.project.context.required");
        }
    }

    private DeferredResult<ComputeState> addContainerHost(ContainerHostSpec hostSpec) {
        return getHost().sendWithDeferredResult(
                Operation.createPut(getHost(), ContainerHostService.SELF_LINK)
                        .setReferer(getUri())
                        .setBody(hostSpec))
                .thenCompose((op) -> {

                    if (op.getStatusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                        // host successfully added
                        String computeLink = op.getResponseHeader(Operation.LOCATION_HEADER);
                        return getHost().sendWithDeferredResult(
                                Operation.createGet(getHost(), computeLink).setReferer(getUri()),
                                ComputeState.class);

                    } else {
                        return DeferredResult.failed(new CertificateNotTrustedException(
                                op.getBody(SslTrustCertificateState.class)));
                    }
                });
    }

    /**
     * If needed, automatically generates placement zone and placement for the host specified.
     * Stores the document links of the created documents in the provided set
     */
    private DeferredResult<Pair<ResourcePoolState, GroupResourcePlacementState>> generatePlacementZoneAndPlacement(
            ContainerHostSpec hostSpec, Set<String> generatedResourcesIds) {
        ComputeState hostState = hostSpec.hostState;
        String clusterDetails = null;
        String clusterName = null;
        if (hostState.customProperties != null) {
            clusterDetails = hostState.customProperties.remove(CLUSTER_DETAILS_CUSTOM_PROP);
            clusterName = hostState.customProperties.remove(CLUSTER_NAME_CUSTOM_PROP);
        }
        Long clusterCreationTime = Instant.now().toEpochMilli();

        // Honor predefined placement zone if any
        if (ClusterUtils.hasPlacementZone(hostState)) {
            return DeferredResult.completed(null);
        }

        // else, automatically generate placement zone
        return PlacementZoneUtil
                .generatePlacementZone(getHost(), hostState, clusterDetails, clusterName,
                        clusterCreationTime)
                .thenCompose((generatedZone) -> {
                    // update placement zone in the compute state and generate placement
                    hostState.resourcePoolLink = generatedZone.documentSelfLink;
                    generatedResourcesIds.add(generatedZone.documentSelfLink);
                    return PlacementZoneUtil.generatePlacement(getHost(), generatedZone)
                            .thenApply((generatedPlacement) -> {
                                generatedResourcesIds.add(generatedPlacement.documentSelfLink);
                                return new Pair<>(generatedZone, generatedPlacement);
                            });
                });
    }

    private void validateClusterPatch(Operation patch) {
        if (!patch.hasBody()) {
            throw new LocalizableValidationException(
                    "Cluster body is required", "compute.host.spec.is.required");
        }
        ClusterDto patchClusterDto = patch.getBody(ClusterDto.class);
        if (patchClusterDto == null) {
            throw new LocalizableValidationException(
                    "Cluster name is required", "compute.host.spec.is.required");
        }
    }

    private void deleteCluster(Operation delete) {
        String clusterId = UriUtils.parseUriPathSegments(delete.getUri(),
                CLUSTER_PATH_SEGMENT_TEMPLATE).get(CLUSTER_ID_PATH_SEGMENT);
        String pZILink = UriUtils.buildUriPath(
                ElasticPlacementZoneConfigurationService.SELF_LINK,
                ResourcePoolService.FACTORY_LINK, clusterId);
        String resourcePoolLink = UriUtils.buildUriPath(
                ResourcePoolService.FACTORY_LINK, clusterId);
        String projectLink = OperationUtil.extractProjectFromHeader(delete);
        deleteHostsWihtinOnePlacementZone(getHost(), resourcePoolLink, projectLink)
                .thenAccept(operation -> {
                    if (operation == null || DeploymentProfileConfig.getInstance().isTest()) {
                        //TODO if MockRequestBrokerService behaves as Task service we can remove the
                        // check for test context
                        ClusterUtils.deletePlacementZoneAndPlacement(pZILink, resourcePoolLink,
                                delete, getHost());

                    } else {
                        String containerHostRemovalTaskState = operation
                                .getBody(ContainerHostRemovalTaskState.class).documentSelfLink;

                        SubscriptionManager<ContainerHostRemovalTaskState> subscriptionManager = new SubscriptionManager<>(
                                getHost(), UUID.randomUUID().toString(),
                                containerHostRemovalTaskState,
                                ContainerHostRemovalTaskState.class);
                        subscriptionManager.start(notification -> {
                            ContainerHostRemovalTaskState eats = notification.getResult();
                            EnumSet<TaskStage> terminalStages = of(TaskStage.FINISHED,
                                    TaskStage.FAILED, TaskStage.CANCELLED);
                            if (terminalStages.contains(eats.taskInfo.stage)) {
                                subscriptionManager.close();
                                ClusterUtils.deletePlacementZoneAndPlacement(pZILink,
                                        resourcePoolLink, delete, getHost());
                            }
                        }, true, null);
                    }
                });
    }

    private void deleteHostInCluster(Operation delete) {

        Map<String, String> uriParams = UriUtils.parseUriPathSegments(delete.getUri(),
                CLUSTER_PATH_SEGMENT_TEMPLATE);
        String clusterId = uriParams.get(CLUSTER_ID_PATH_SEGMENT);
        String hostId = uriParams.get(CLUSTER_HOST_ID_PATH_SEGMENT);

        String hostDocumentSelfLink = UriUtils.buildUriPath(
                ComputeService.FACTORY_LINK, hostId);

        List<String> hostLinksList = new LinkedList<>();
        hostLinksList.add(hostDocumentSelfLink);

        String pathPZId = UriUtils.buildUriPath(
                ElasticPlacementZoneConfigurationService.SELF_LINK,
                ResourcePoolService.FACTORY_LINK, clusterId);

        URI elasticPlacementZoneConfigurationUri = UriUtils.buildUri(getHost(),
                pathPZId);
        sendWithDeferredResult(Operation
                .createGet(
                        UriUtils.buildExpandLinksQueryUri(elasticPlacementZoneConfigurationUri))
                .setReferer(getUri()),
                ElasticPlacementZoneConfigurationState.class)
                        .thenCompose(epzcs -> {
                            if (PlacementZoneUtil.getPlacementZoneType(
                                    epzcs.resourcePoolState) == PlacementZoneType.DOCKER) {
                                return sendWithDeferredResult(
                                        Operation.createPost(
                                                UriUtils.buildUri(getHost(),
                                                        ManagementUriParts.REQUESTS))
                                                .setReferer(getHost().getUri())
                                                .forceRemote()
                                                .setBody(new ContainerHostRemovalTaskState(
                                                        hostLinksList)));
                            }
                            return DeferredResult.completed(null);
                        }).thenAccept(operation -> {
                            if (operation == null
                                    || DeploymentProfileConfig.getInstance().isTest()) {
                                //TODO if MockRequestBrokerService behaves as Task service we can remove the
                                // check for test context
                                delete.complete();
                            } else {
                                String containerHostRemovalTaskState = operation
                                        .getBody(
                                                ContainerHostRemovalTaskState.class).documentSelfLink;

                                SubscriptionManager<ContainerHostRemovalTaskState> subscriptionManager = new SubscriptionManager<>(
                                        getHost(), UUID.randomUUID().toString(),
                                        containerHostRemovalTaskState,
                                        ContainerHostRemovalTaskState.class);
                                subscriptionManager.start(notification -> {
                                    ContainerHostRemovalTaskState eats = notification.getResult();
                                    EnumSet<TaskStage> terminalStages = of(TaskStage.FINISHED,
                                            TaskStage.FAILED, TaskStage.CANCELLED);
                                    if (terminalStages.contains(eats.taskInfo.stage)) {
                                        subscriptionManager.close();
                                        delete.complete();
                                    }
                                }, true, null);

                            }
                        });
    }

}
