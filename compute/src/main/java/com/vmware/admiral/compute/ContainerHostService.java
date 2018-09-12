/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute;

import static com.vmware.admiral.common.util.ServiceUtils.addServiceRequestRoute;
import static com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription.getDockerHostUri;

import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.netty.channel.ConnectTimeoutException;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.KubernetesHostConstants;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.SslCertificateResolver;
import com.vmware.admiral.compute.PlacementZoneConstants.PlacementZoneType;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateFactoryService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;


/**
 * Help service to add/update a container host and validate container host address.
 */
public class ContainerHostService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.CONTAINER_HOSTS;
    public static final String INCORRECT_PLACEMENT_ZONE_TYPE_MESSAGE_FORMAT = "Incorrect placement "
            + "zone type. Expected '%s' but was '%s'";
    public static final String CONTAINER_HOST_ALREADY_EXISTS_MESSAGE = "Container host already exists";
    public static final String CONTAINER_HOST_IS_NOT_VCH_MESSAGE = "Host type is not VCH";
    public static final String PLACEMENT_ZONE_NOT_EMPTY_MESSAGE = "Placement zone is not empty";
    public static final String PLACEMENT_ZONE_CONTAINS_SCHEDULERS_MESSAGE = "Placement zone is not empty "
            + "or does not contain only Docker hosts";

    public static final String CONTAINER_HOST_ALREADY_EXISTS_MESSAGE_CODE = "compute.host.already.exists";
    public static final String CONTAINER_HOST_IS_NOT_VCH_MESSAGE_CODE = "compute.host.type.not.vch";
    public static final String INCORRECT_PLACEMENT_ZONE_TYPE_MESSAGE_CODE = "compute.placement-zone.type.incorrect";
    public static final String PLACEMENT_ZONE_NOT_EMPTY_MESSAGE_CODE = "compute.placement-zone.not.empty";
    public static final String PLACEMENT_ZONE_CONTAINS_SCHEDULERS_MESSAGE_CODE = "compute.placement-zone.contains.schedulers";

    public static final String DOCKER_COMPUTE_DESC_ID = "docker-host-compute-desc-id";
    public static final String DOCKER_COMPUTE_DESC_LINK = UriUtils.buildUriPath(
            ComputeDescriptionService.FACTORY_LINK, DOCKER_COMPUTE_DESC_ID);

    public static final String VIC_COMPUTE_DESC_ID = "vic-host-compute-desc-id";
    public static final String VIC_COMPUTE_DESC_LINK = UriUtils.buildUriPath(
            ComputeDescriptionService.FACTORY_LINK, VIC_COMPUTE_DESC_ID);

    public static final String CONTAINER_HOST_TYPE_PROP_NAME = "__containerHostType";
    public static final String HOST_DOCKER_ADAPTER_TYPE_PROP_NAME = "__adapterDockerType";
    public static final String HOST_PUBLIC_ADDRESS_PROP_NAME = "__publicAddress";
    public static final String NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME = "__Containers";
    public static final String NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME = "__systemContainers";
    public static final String RETRIES_COUNT_PROP_NAME = "__retriesCount";

    public static final String DOCKER_HOST_PORT_PROP_NAME = "__dockerHostPort";
    public static final String DOCKER_HOST_PATH_PROP_NAME = "__dockerHostPath";
    public static final String DOCKER_HOST_SCHEME_PROP_NAME = "__dockerHostScheme";
    public static final String DOCKER_HOST_ADDRESS_PROP_NAME = "__dockerHostAddress";

    public static final String CUSTOM_PROPERTY_HOST_ALIAS = "__hostAlias";

    public static final String SSL_TRUST_CERT_PROP_NAME = "__sslTrustCertificate";
    public static final String SSL_TRUST_ALIAS_PROP_NAME = "__sslTrustAlias";

    public static final String DOCKER_HOST_AVAILABLE_STORAGE_PROP_NAME = "__StorageAvailable";
    public static final String DOCKER_HOST_TOTAL_STORAGE_PROP_NAME = "__StorageTotal";
    public static final String DOCKER_HOST_TOTAL_MEMORY_PROP_NAME = "__MemTotal";
    public static final String DOCKER_HOST_NUM_CORES_PROP_NAME = "__NCPU";

    public static final String CUSTOM_PROPERTY_DEPLOYMENT_POLICY = "__deploymentPolicyLink";

    public static final String DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME = "__CpuUsage";
    public static final String DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME = "__MemAvailable";

    public static final String KUBERNETES_HOST_NODE_LIST_PROP_NAME = "__nodes";

    public static final String DOCKER_HOST_CLUSTER_STORE_PROP_NAME = "__ClusterStore";

    public static final String DOCKER_HOST_PLUGINS_PROP_NAME = "__Plugins";

    public static final String DOCKER_HOST_PLUGINS_VOLUME_PROP_NAME = "Volume";
    public static final String DOCKER_HOST_PLUGINS_NETWORK_PROP_NAME = "Network";

    public static final String DEFAULT_VMDK_DATASTORE_PROP_NAME = "defaultVmdkDatastore";

    public enum ContainerHostType {
        DOCKER,
        VCH,
        KUBERNETES;

        public static ContainerHostType getDefaultHostType() {
            return DOCKER;
        }
    }

    public enum DockerAdapterType {
        API
    }

    public static class ContainerHostSpec extends HostSpec {
        /** The state for the container host to be created or validated. */
        public ComputeState hostState;

        /** The given container host exists and has to be updated. */
        public Boolean isUpdateOperation;

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSecureScheme() {
            if (hostState != null && hostState.customProperties != null) {
                return !UriUtils.HTTP_SCHEME.equalsIgnoreCase(
                        hostState.customProperties.get(DOCKER_HOST_SCHEME_PROP_NAME));
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<String> getHostTenantLinks() {
            return hostState == null ? null : hostState.tenantLinks;
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        checkForDefaultHostDescription(DOCKER_COMPUTE_DESC_LINK, DOCKER_COMPUTE_DESC_ID);
        checkForDefaultHostDescription(VIC_COMPUTE_DESC_LINK, VIC_COMPUTE_DESC_ID);
        checkForDefaultHostDescription(KubernetesHostConstants.KUBERNETES_COMPUTE_DESC_LINK,
                KubernetesHostConstants.KUBERNETES_COMPUTE_DESC_ID);
        super.handleStart(startPost);
    }

    @Override
    public void handlePut(Operation op) {
        if (!op.hasBody()) {
            op.fail(new LocalizableValidationException("ContainerHostSpec body is required",
                    "compute.host.spec.is.required"));
            return;
        }

        ContainerHostSpec hostSpec = op.getBody(ContainerHostSpec.class);
        validate(hostSpec);

        hostSpec.uri = getDockerHostUri(hostSpec.hostState);

        String query = op.getUri().getQuery();
        boolean validateHostTypeAndConnection = query != null
                && query.contains(ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);

        if (validateHostTypeAndConnection) {
            validateHostTypeAndConnection(hostSpec, op);
        } else if (hostSpec.isUpdateOperation != null
                && hostSpec.isUpdateOperation.booleanValue()) {
            updateHost(hostSpec, op);
        } else {
            QueryTask q = QueryUtil.buildPropertyQuery(ComputeState.class,
                    QuerySpecification.buildCompositeFieldName(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeConstants.DOCKER_URI_PROP_NAME),
                    hostSpec.uri.toString());

            List<String> tenantLinks = hostSpec.hostState.tenantLinks;
            if (tenantLinks != null) {
                q.querySpec.query
                        .addBooleanClause(QueryUtil.addTenantGroupAndUserClause(tenantLinks));
            }

            AtomicBoolean found = new AtomicBoolean(false);
            new ServiceDocumentQuery<>(getHost(), ComputeState.class)
                    .query(q,
                            (r) -> {
                                if (r.hasException()) {
                                    op.fail(r.getException());
                                } else if (r.hasResult()) {
                                    found.set(true);
                                    op.fail(new LocalizableValidationException(
                                            CONTAINER_HOST_ALREADY_EXISTS_MESSAGE,
                                            CONTAINER_HOST_ALREADY_EXISTS_MESSAGE_CODE));
                                } else if (!found.get()) {
                                    createHost(hostSpec, op);
                                }
                            });
        }
    }

    /**
     * Fetches server certificate and stores its fingerprint as custom property. It is then used as
     * a hash key to get the client certificate when handshaking. If cannot establish connection
     * sets host power state to unknown. It will be set to the correct one once the data collection
     * passes.
     */
    private void fetchSslTrustAliasProperty(ContainerHostSpec hostSpec, Runnable callback) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            logInfo("No ssl trust validation is performed in test mode...");
            callback.run();
            return;
        }

        if (!hostSpec.isSecureScheme()) {
            logInfo("Using non secure channel, skipping SSL validation for %s", hostSpec.uri);
            callback.run();
            return;
        }

        SslCertificateResolver.execute(hostSpec.uri, (resolver, ex) -> {
            if (ex != null) {
                // if we cannot connect host to get its certificate move the state to unknown,
                // later data collection will set the proper state when host is available
                hostSpec.hostState.powerState = ComputeService.PowerState.UNKNOWN;
                logWarning("Cannot connect to %s to get remote certificate for sslTrustAlias",
                        hostSpec.uri);
                callback.run();
                return;
            }

            X509Certificate[] certificateChain = resolver.getCertificateChain();
            String s = CertificateUtil.generatePureFingerPrint(certificateChain);
            if (hostSpec.hostState.customProperties == null) {
                hostSpec.hostState.customProperties = new HashMap<>();
            }
            hostSpec.hostState.customProperties.put(SSL_TRUST_ALIAS_PROP_NAME, s);
            callback.run();
        });
    }

    private void validate(ContainerHostSpec hostSpec) {
        final ComputeState cs = hostSpec.hostState;
        AssertUtil.assertNotNull(cs, "computeState");
        AssertUtil.assertNotEmpty(cs.address, "address");
        AssertUtil.assertNotEmpty(cs.customProperties, "customProperties");
        String adapterDockerType = cs.customProperties.get(HOST_DOCKER_ADAPTER_TYPE_PROP_NAME);
        AssertUtil.assertNotEmpty(adapterDockerType, HOST_DOCKER_ADAPTER_TYPE_PROP_NAME);
        DockerAdapterType adapterType = DockerAdapterType.valueOf(adapterDockerType);
        AssertUtil.assertNotNull(adapterType, "adapterType");

        cs.address = cs.address.trim();

        String kubernetesNamespace = cs.customProperties.get(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME);
        if (kubernetesNamespace == null || kubernetesNamespace.isEmpty()) {
            cs.customProperties.put(
                    KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME,
                    KubernetesHostConstants.KUBERNETES_HOST_DEFAULT_NAMESPACE);
        } else {
            kubernetesNamespace = kubernetesNamespace.trim();
            AssertUtil.assertTrue(!kubernetesNamespace.contains("/") &&
                    !kubernetesNamespace.contains("\\"), "Namespace cannot contain"
                            + " slashes.");
            AssertUtil.assertTrue(kubernetesNamespace.matches(
                    KubernetesHostConstants.KUBERNETES_NAMESPACE_REGEX),
                    "Invalid namespace.");
            cs.customProperties.put(
                    KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME,
                    kubernetesNamespace);
        }
    }

    protected String getDescriptionForType(ContainerHostType type) {
        switch (type) {
        case DOCKER:
            return DOCKER_COMPUTE_DESC_LINK;
        case VCH:
            return VIC_COMPUTE_DESC_LINK;
        case KUBERNETES:
            return KubernetesHostConstants.KUBERNETES_COMPUTE_DESC_LINK;
        default:
            throw new LocalizableValidationException(String.format(
                    ContainerHostUtil.CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_FORMAT, type),
                    ContainerHostUtil.CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_CODE, type);
        }
    }

    private URI getAdapterManagementReferenceForType(ContainerHostType type) {
        switch (type) {
        case DOCKER:
            return UriUtils.buildUri(getHost(), ManagementUriParts.ADAPTER_DOCKER_HOST);
        case VCH:
            return UriUtils.buildUri(getHost(), ManagementUriParts.ADAPTER_DOCKER_HOST);
        case KUBERNETES:
            return UriUtils.buildUri(getHost(), ManagementUriParts.ADAPTER_KUBERNETES_HOST);
        default:
            throw new LocalizableValidationException(String.format(
                    ContainerHostUtil.CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_FORMAT, type),
                    ContainerHostUtil.CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_CODE, type);
        }
    }

    private void validateHostTypeAndConnection(ContainerHostSpec hostSpec, Operation op) {
        ContainerHostType hostType;
        try {
            hostType = ContainerHostUtil.getDeclaredContainerHostType(hostSpec.hostState);
        } catch (LocalizableValidationException ex) {
            logWarning("Error getting host type: %s", ex.getMessage());
            op.fail(ex);
            return;
        }

        // Apply the appropriate validation for each host type
        switch (hostType) {
        case DOCKER:
            validateConnection(hostSpec, op);
            break;

        case VCH:
            validateVicHost(hostSpec, op);
            break;

        case KUBERNETES:
            validateConnection(hostSpec, op);
            break;

        default:
            String error = String.format(
                    ContainerHostUtil.CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_FORMAT,
                    hostType.toString());
            op.fail(new LocalizableValidationException(error,
                    ContainerHostUtil.CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_CODE, hostType));
            break;
        }
    }

    private void validateVicHost(ContainerHostSpec hostSpec, Operation op) {
        String computeAddress = hostSpec.hostState.address;
        EndpointCertificateUtil.validateSslTrust(this, hostSpec, op, () -> {
            fetchSslTrustAliasProperty(hostSpec, () -> {
                getHostInfo(hostSpec, op, hostSpec.sslTrust,
                        (computeState) -> {
                            if (ContainerHostUtil.isVicHost(computeState)) {
                                logInfo("VIC host verification passed for %s", computeAddress);
                                completeOperationSuccess(op);
                            } else {
                                logInfo("VIC host verification failed for %s", computeAddress);
                                op.fail(new LocalizableValidationException(
                                        CONTAINER_HOST_IS_NOT_VCH_MESSAGE,
                                        CONTAINER_HOST_IS_NOT_VCH_MESSAGE_CODE));
                            }
                        });
            });
        });

    }

    protected void storeHost(ContainerHostSpec hostSpec, Operation op) {
        ContainerHostType hostType;
        try {
            hostType = ContainerHostUtil
                    .getDeclaredContainerHostType(hostSpec.hostState);
        } catch (LocalizableValidationException ex) {
            logWarning("Error getting host type: %s", ex.getMessage());
            op.fail(ex);
            return;
        }

        switch (hostType) {
        case DOCKER:
            verifyPlacementZoneType(hostSpec, op, PlacementZoneType.DOCKER, () -> {
                verifyNoSchedulersInPlacementZone(hostSpec, op, () -> {
                    storeDockerHost(hostSpec, op);
                });
            });
            break;

        case VCH:
            verifyPlacementZoneType(hostSpec, op, PlacementZoneType.SCHEDULER, () -> {
                verifyPlacementZoneIsEmpty(hostSpec, op, () -> {
                    storeVchHost(hostSpec, op);
                });
            });
            break;
        case KUBERNETES:
            verifyPlacementZoneType(hostSpec, op, PlacementZoneType.SCHEDULER, () -> {
                verifyPlacementZoneIsEmpty(hostSpec, op,
                        () -> storeKubernetesHost(hostSpec, op));
            });
            break;

        default:
            String error = String.format(
                    ContainerHostUtil.CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_FORMAT,
                    hostType.toString());
            op.fail(new LocalizableValidationException(error,
                    ContainerHostUtil.CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_CODE, hostType));
            break;
        }
    }

    private void storeVchHost(ContainerHostSpec hostSpec, Operation op) {
        ComputeState hostState = hostSpec.hostState;
        try {
            // Schedulers can be added to placements zones only explicitly, it is not possible to
            // use tags
            AssertUtil.assertEmpty(hostState.tagLinks, "tagLinks");
        } catch (LocalizableValidationException ex) {
            op.fail(ex);
            return;
        }

        // keep track of auto-generated resources (placement and placement zone)
        HashSet<String> generatedResourcesIds = new HashSet<>(2);

        // nest completion to clean up auto-generated resource on operation failure
        op.nestCompletion((o, e) -> {
            if (e != null) {
                ContainerHostUtil.cleanupAutogeneratedResources(this, generatedResourcesIds);
                // propagate failure
                op.fail(e);
            } else {
                // propagate completion
                op.complete();
            }
        });

        // If no placement zone is specified, automatically generate one
        if (hostState.resourcePoolLink == null) {
            // mark automatic deletion of the placement zone on host removal
            if (hostState.customProperties == null) {
                hostState.customProperties = new HashMap<>();
            }
            hostState.customProperties.put(
                    ComputeConstants.AUTOGENERATED_PLACEMENT_ZONE_PROP_NAME,
                    Boolean.toString(true));

            PlacementZoneUtil.generatePlacementZone(getHost(), hostState)
                    .thenCompose((generatedZone) -> {
                        generatedResourcesIds.add(generatedZone.documentSelfLink);
                        hostState.resourcePoolLink = generatedZone.documentSelfLink;
                        return PlacementZoneUtil.generatePlacement(getHost(), generatedZone);
                    })
                    .thenAccept((generatedPlacement) -> {
                        generatedResourcesIds.add(generatedPlacement.documentSelfLink);
                        storeVchHost(hostSpec, op);
                    }).exceptionally((ex) -> {
                        op.fail(ex);
                        return null;
                    });

            return;
        }

        if (hostSpec.acceptHostAddress) {
            doStoreHost(hostSpec, op);
        } else {
            // VIC verification relies on data gathered by a docker info command
            getHostInfo(hostSpec, op, hostSpec.sslTrust, (computeState) -> {
                if (ContainerHostUtil.isVicHost(computeState)) {
                    String version = ContainerHostUtil.getHostServerVersion(computeState);
                    ContainerHostUtil.verifyVchVersionIsSupported(getHost(), version)
                            .whenComplete((ignore, ex) -> {
                                if (ex != null) {
                                    Throwable cause = ex instanceof CompletionException
                                            ? ex.getCause() : ex;
                                    logWarning("Unsupported VCH version: %s",
                                            Utils.toString(cause));
                                    op.fail(cause);
                                } else {
                                    doStoreHost(hostSpec, op);
                                }
                            });
                } else {
                    op.fail(new LocalizableValidationException(
                            CONTAINER_HOST_IS_NOT_VCH_MESSAGE,
                            CONTAINER_HOST_IS_NOT_VCH_MESSAGE_CODE));
                }
            });
        }
    }

    private void storeDockerHost(ContainerHostSpec hostSpec, Operation op) {
        if (hostSpec.acceptHostAddress) {
            doStoreHost(hostSpec, op);
        } else {
            // Docker hosts are validated by a ping call.
            pingHost(hostSpec, op, hostSpec.sslTrust, () -> {
                doStoreHost(hostSpec, op);
            });
        }
    }

    private void storeKubernetesHost(ContainerHostSpec hostSpec, Operation op) {
        if (hostSpec.acceptHostAddress) {
            doStoreHost(hostSpec, op);
        } else {
            // Kubernetes hosts are validated by a ping call.
            pingHost(hostSpec, op, hostSpec.sslTrust, () -> {
                doStoreHost(hostSpec, op);
            });
        }
    }

    private void doStoreHost(ContainerHostSpec hostSpec, Operation op) {
        ComputeState cs = hostSpec.hostState;

        ContainerHostType hostType = ContainerHostUtil
                .getDeclaredContainerHostType(hostSpec.hostState);

        if (cs.descriptionLink == null) {
            cs.descriptionLink = getDescriptionForType(hostType);
        }

        // If Compute state has endpointLink, it's not a manually added host, so don't override it's
        // adapterManagementReference
        if (cs.endpointLink == null) {
            cs.adapterManagementReference = getAdapterManagementReferenceForType(hostType);
        }

        Operation store = null;
        if (cs.id == null) {
            cs.id = UUID.randomUUID().toString();
        }
        // This should be the case only when using the addHost manually, e.g. unmanaged external
        // host
        if (cs.documentSelfLink == null
                || !cs.documentSelfLink.startsWith(ComputeService.FACTORY_LINK)) {
            store = Operation.createPost(getHost(), ComputeService.FACTORY_LINK)
                    .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);

            if (cs.powerState == null) {
                cs.powerState = ComputeService.PowerState.ON;
            }
        } else {
            store = Operation.createPut(getHost(), cs.documentSelfLink);
        }
        if (cs.creationTimeMicros == null) {
            cs.creationTimeMicros = Utils.getNowMicrosUtc();
        }
        if (cs.customProperties == null) {
            cs.customProperties = new HashMap<>();
        }
        cs.customProperties.put(ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true");
        cs.customProperties.put(ComputeConstants.COMPUTE_HOST_PROP_NAME, "true");
        cs.customProperties.put(ComputeConstants.DOCKER_URI_PROP_NAME, hostSpec.uri.toString());
        cs.customProperties.put(CONTAINER_HOST_TYPE_PROP_NAME, hostType.toString());
        if (hostSpec.sslTrust != null) {
            cs.customProperties.put(ComputeConstants.HOST_TRUST_CERTS_PROP_NAME,
                    hostSpec.sslTrust.documentSelfLink);
        }

        sendRequest(store
                .setBody(cs)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        op.fail(e);
                        return;
                    }
                    ComputeState storedHost = o.getBody(ComputeState.class);
                    String documentSelfLink = storedHost.documentSelfLink;
                    if (!documentSelfLink.startsWith(ComputeService.FACTORY_LINK)) {
                        documentSelfLink = UriUtils.buildUriPath(
                                ComputeService.FACTORY_LINK, documentSelfLink);
                    }

                    op.addResponseHeader(Operation.LOCATION_HEADER, documentSelfLink);
                    createHostPortProfile(storedHost, op);

                    updateContainerHostInfo(documentSelfLink);
                    triggerEpzEnumeration();
                }));
    }

    private void verifyPlacementZoneType(ContainerHostSpec hostSpec, Operation op,
            PlacementZoneType expectedType, Runnable successCallaback) {

        if (hostSpec.hostState.resourcePoolLink == null) {
            successCallaback.run();
            return;
        }

        Operation.createGet(getHost(), hostSpec.hostState.resourcePoolLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        op.fail(e);
                        return;
                    }

                    ResourcePoolState placementZone = o.getBody(ResourcePoolState.class);
                    try {
                        PlacementZoneType zoneType = PlacementZoneUtil
                                .getPlacementZoneType(placementZone);
                        if (zoneType == expectedType) {
                            successCallaback.run();
                        } else {
                            String error = String.format(
                                    INCORRECT_PLACEMENT_ZONE_TYPE_MESSAGE_FORMAT,
                                    expectedType, zoneType);
                            op.fail(new LocalizableValidationException(error,
                                    INCORRECT_PLACEMENT_ZONE_TYPE_MESSAGE_CODE,
                                    expectedType, zoneType));
                        }
                    } catch (LocalizableValidationException ex) {
                        op.fail(ex);
                    }
                }).sendWith(this);
    }

    private void verifyPlacementZoneIsEmpty(ContainerHostSpec hostSpec, Operation op,
            Runnable successCallback) {
        String placementZoneLink = hostSpec.hostState.resourcePoolLink;
        if (placementZoneLink == null || placementZoneLink.isEmpty()) {
            // no placement zone to verify
            successCallback.run();
            return;
        }

        AtomicBoolean emptyZone = new AtomicBoolean(true);
        QueryTask queryTask = QueryUtil.buildPropertyQuery(ComputeState.class,
                ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, placementZoneLink);
        QueryUtil.addCountOption(queryTask);
        new ServiceDocumentQuery<>(getHost(), ComputeState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        op.fail(r.getException());
                    } else if (r.getCount() > 0) {
                        emptyZone.set(false);
                        op.fail(new LocalizableValidationException(PLACEMENT_ZONE_NOT_EMPTY_MESSAGE,
                                PLACEMENT_ZONE_NOT_EMPTY_MESSAGE_CODE));
                    } else {
                        if (emptyZone.get()) {
                            successCallback.run();
                        }
                    }
                });
    }

    private void verifyNoSchedulersInPlacementZone(ContainerHostSpec hostSpec, Operation op,
            Runnable successCallback) {
        String placementZoneLink = hostSpec.hostState.resourcePoolLink;
        if (placementZoneLink == null || placementZoneLink.isEmpty()) {
            // no placement zone => no schedulers
            successCallback.run();
            return;
        }

        AtomicBoolean schedulerFound = new AtomicBoolean(false);
        QueryTask queryTask = QueryUtil.buildPropertyQuery(ComputeState.class,
                ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, placementZoneLink);
        QueryUtil.addExpandOption(queryTask);
        new ServiceDocumentQuery<>(getHost(), ComputeState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        op.fail(r.getException());
                    } else if (r.hasResult()) {
                        if (ContainerHostUtil.isTreatedLikeSchedulerHost(r.getResult())) {
                            schedulerFound.set(true);
                            op.fail(new LocalizableValidationException(
                                    PLACEMENT_ZONE_CONTAINS_SCHEDULERS_MESSAGE,
                                    PLACEMENT_ZONE_CONTAINS_SCHEDULERS_MESSAGE_CODE));
                        }
                    } else {
                        if (!schedulerFound.get()) {
                            successCallback.run();
                        }
                    }
                });
    }

    private void checkForDefaultHostDescription(String descriptionLink, String descriptionId) {
        new ServiceDocumentQuery<>(getHost(), ComputeDescription.class)
                .queryDocument(descriptionLink, (r) -> {
                    if (r.hasException()) {
                        r.throwRunTimeException();
                    } else if (r.hasResult()) {
                        logFine("Default docker compute description exists.");
                    } else {
                        ComputeDescription desc = new ComputeDescription();
                        desc.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
                        desc.supportedChildren = new ArrayList<>(
                                Collections.singletonList(ComputeType.DOCKER_CONTAINER.name()));
                        desc.documentSelfLink = descriptionId;
                        desc.id = descriptionId;
                        sendRequest(Operation
                                .createPost(this, ComputeDescriptionService.FACTORY_LINK)
                                .setBody(desc)
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        logWarning("Default host description can't be created."
                                                + " Exception: %s",
                                                e instanceof CancellationException
                                                        ? e.getMessage() : Utils.toString(e));
                                        return;
                                    }
                                    logInfo("Default host description created with self link: %s",
                                            descriptionLink);
                                }));
                    }
                });
    }

    private AdapterRequest prepareAdapterRequest(ContainerHostOperationType operationType,
            ComputeState cs, SslTrustCertificateState sslTrust) {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = operationType.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = UriUtils.buildUri(getHost(), ComputeService.FACTORY_LINK);
        request.customProperties = cs.customProperties == null ? new HashMap<>()
                : new HashMap<>(cs.customProperties);
        request.customProperties.putIfAbsent(ComputeConstants.HOST_URI_PROP_NAME,
                cs.address);

        if (sslTrust != null) {
            request.customProperties.put(SSL_TRUST_CERT_PROP_NAME, sslTrust.certificate);
            request.customProperties.put(SSL_TRUST_ALIAS_PROP_NAME,
                    SslTrustCertificateFactoryService.generateFingerprint(sslTrust));
        }

        return request;
    }

    private void sendAdapterRequest(AdapterRequest request, ComputeState cs, Operation op,
            Runnable callbackFunction) {
        Consumer<Void> callback = (v) -> {
            callbackFunction.run();
        };

        sendAdapterRequest(request, cs, op, callback, Void.class);
    }

    private <T> void sendAdapterRequest(AdapterRequest request, ComputeState cs, Operation op,
            Consumer<T> callbackFunction, Class<T> callbackResultClass) {

        URI adapterManagementReference = getAdapterManagementReferenceForType(
                ContainerHostUtil.getDeclaredContainerHostType(cs));

        Operation patchOp = Operation
                .createPatch(adapterManagementReference)
                .setBody(request)
                .setContextId(Service.getId(getSelfLink()))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        String innerMessage = toReadableErrorMessage(ex, op);
                        String message = String.format("Error connecting to %s: %s",
                                cs.address, innerMessage);
                        LocalizableValidationException validationEx = new LocalizableValidationException(
                                ex.getCause(),
                                message, "compute.add.host.connection.error", cs.address,
                                innerMessage);
                        ServiceErrorResponse rsp = Utils.toValidationErrorResponse(validationEx,
                                op);

                        logWarning("Error sending adapter request with type %s : %s. Cause: %s",
                                request.operationTypeId, rsp.message, Utils.toString(ex));
                        postEventlogError(cs, rsp.message);
                        op.setStatusCode(o.getStatusCode());
                        op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
                        op.fail(validationEx, rsp);
                        return;
                    }

                    if (callbackFunction != null) {
                        if (callbackResultClass != null) {
                            callbackFunction.accept(o.getBody(callbackResultClass));
                        } else {
                            callbackFunction.accept(null);
                        }
                    }
                });
        String languageHeader = op.getRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER);
        if (languageHeader != null) {
            patchOp.addRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER, languageHeader);
        }
        sendRequest(patchOp);
    }

    private void pingHost(ContainerHostSpec hostSpec, Operation op,
            SslTrustCertificateState sslTrust, Runnable callbackFunction) {
        ComputeState cs = hostSpec.hostState;
        AdapterRequest request = prepareAdapterRequest(ContainerHostOperationType.PING, cs,
                sslTrust);
        sendAdapterRequest(request, cs, op, callbackFunction);
    }

    private void getHostInfo(ContainerHostSpec hostSpec, Operation op,
            SslTrustCertificateState sslTrust, Consumer<ComputeState> callbackFunction) {
        ComputeState cs = hostSpec.hostState;
        AdapterRequest request = prepareAdapterRequest(ContainerHostOperationType.INFO, cs,
                sslTrust);
        sendAdapterRequest(request, cs, op, callbackFunction, ComputeState.class);
    }

    private String toReadableErrorMessage(Throwable e, Operation op) {
        LocalizableValidationException localizedEx = null;
        if (e instanceof io.netty.handler.codec.DecoderException) {
            if (e.getMessage().contains("Received fatal alert: bad_certificate")) {
                localizedEx = new LocalizableValidationException("Check login credentials",
                        "compute.check.credentials");
            }
        } else if (e instanceof IllegalStateException) {
            if (e.getMessage().contains("Socket channel closed:")) {
                localizedEx = new LocalizableValidationException("Check login credentials",
                        "compute.check.credentials");
            }
        } else if (e.getCause() instanceof ConnectTimeoutException) {
            localizedEx = new LocalizableValidationException(e, "Connection timeout",
                    "compute.connection.timeout");
        } else if (e.getCause() instanceof ProtocolException) {
            localizedEx = new LocalizableValidationException(e, "Protocol exception",
                    "compute.protocol.exception");
        } else if (e instanceof IllegalArgumentException) {
            localizedEx = new LocalizableValidationException(e,
                    "Illegal argument exception: " + e.getMessage(), "compute.illegal.argument",
                    e.getMessage());
        }

        if (localizedEx == null) {
            localizedEx = new LocalizableValidationException(e,
                    String.format("Unexpected error: %s", e.getMessage()),
                    "compute.unexpected.error", e.getMessage());
        }

        return Utils.toValidationErrorResponse(localizedEx, op).message;
    }

    private void updateContainerHostInfo(String documentSelfLink) {
        URI uri = UriUtils.buildUri(getHost(),
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK);
        ContainerHostDataCollectionState state = new ContainerHostDataCollectionState();
        state.createOrUpdateHost = true;
        state.computeContainerHostLinks = Collections.singleton(documentSelfLink);
        sendRequest(Operation.createPatch(uri)
                .setBody(state)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to update host data collection: %s", ex.getMessage());
                    }
                }));
    }

    private void createHostPortProfile(ComputeState computeState, Operation operation) {
        HostPortProfileService.HostPortProfileState hostPortProfileState = new HostPortProfileService.HostPortProfileState();
        hostPortProfileState.hostLink = computeState.documentSelfLink;
        // Make sure there is only one HostPortProfile per Host by generating profile id based on
        // host id
        hostPortProfileState.id = computeState.id;
        hostPortProfileState.documentSelfLink = HostPortProfileService.getHostPortProfileLink(
                computeState.documentSelfLink);

        // POST will be converted to PUT if profile with this id already exists
        sendRequest(OperationUtil
                .createForcedPost(this, HostPortProfileService.FACTORY_LINK)
                .setBody(hostPortProfileState)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        operation.fail(ex);
                        return;
                    }
                    HostPortProfileService.HostPortProfileState result = o.getBody(
                            HostPortProfileService.HostPortProfileState.class);
                    logInfo("Created HostPortProfile for host %s with port range %s-%s.",
                            result.hostLink, result.startPort, result.endPort);
                    completeOperationSuccess(operation);
                }));
    }

    private void triggerEpzEnumeration() {
        EpzComputeEnumerationTaskService.triggerForAllResourcePools(this);
    }

    private void createHost(ContainerHostSpec hostSpec, Operation op) {
        fetchSslTrustAliasProperty(hostSpec, () -> {
            if (hostSpec.acceptHostAddress) {
                if (hostSpec.acceptCertificate) {
                    Operation o = Operation.createGet(null)
                            .setCompletion((completedOp, e) -> {
                                if (e != null) {
                                    storeHost(hostSpec, op);
                                } else {
                                    op.setStatusCode(completedOp.getStatusCode());
                                    op.transferResponseHeadersFrom(completedOp);
                                    op.setBodyNoCloning(completedOp.getBodyRaw());
                                    op.complete();
                                }
                            });
                    EndpointCertificateUtil
                            .validateSslTrust(this, hostSpec, o, () -> storeHost(hostSpec, op));
                } else {
                    storeHost(hostSpec, op);
                }
            } else {
                EndpointCertificateUtil
                        .validateSslTrust(this, hostSpec, op, () -> storeHost(hostSpec, op));
            }
        });
    }

    private void updateHost(ContainerHostSpec hostSpec, Operation op) {
        fetchSslTrustAliasProperty(hostSpec, () -> {
            ComputeState cs = hostSpec.hostState;
            sendRequest(Operation.createPut(this, cs.documentSelfLink)
                    .setBody(cs)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            op.fail(e);
                            return;
                        }
                        createHostPortProfile(cs, op);
                        // when host is updated and ssl property cannot be fetch (power state is
                        // UNKNOWN) do not run data collection (DC) to avoid endless loop. DC will
                        // check for the ssl property and send new update for the host, and so on.
                        if (ComputeService.PowerState.UNKNOWN != hostSpec.hostState.powerState) {
                            // run data collection only if there's no error getting its certificate
                            updateContainerHostInfo(cs.documentSelfLink);
                            triggerEpzEnumeration();
                        }
                    }));
        });

    }

    private void validateConnection(ContainerHostSpec hostSpec, Operation op) {
        EndpointCertificateUtil.validateSslTrust(this, hostSpec, op, () -> {
            fetchSslTrustAliasProperty(hostSpec, () -> {
                pingHost(hostSpec, op, hostSpec.sslTrust, () -> completeOperationSuccess(op));
            });
        });
    }

    protected void completeOperationSuccess(Operation op) {
        op.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
        op.setBody(null);
        op.complete();
    }

    private void postEventlogError(ComputeState hostState, String message) {
        EventLogState eventLog = new EventLogState();
        eventLog.description = message == null ? "Unexpected error" : message;
        eventLog.eventLogType = EventLogType.ERROR;
        eventLog.resourceType = getClass().getName();
        eventLog.tenantLinks = hostState.tenantLinks;

        sendRequest(Operation.createPost(this, EventLogService.FACTORY_LINK)
                .setBody(eventLog)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failed to create event log: %s", Utils.toString(e));
                    }
                }));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        addServiceRequestRoute(d, Action.PUT,
                "Add container host. If host is added successfully, it's reference can be "
                        + "acquired from \"Location\" response header.",
                null);
        return d;
    }
}
