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

package com.vmware.admiral.compute;

import java.net.HttpURLConnection;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.SslCertificateResolver;
import com.vmware.admiral.compute.ConfigureHostOverSshTaskService.ConfigureHostOverSshTaskServiceState;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
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
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
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
    public static final String CONTAINER_HOST_ALREADY_EXISTS_MESSAGE = "Container host already exists";

    public static final String DOCKER_COMPUTE_DESC_ID = "docker-host-compute-desc-id";
    public static final String DOCKER_COMPUTE_DESC_LINK = UriUtils.buildUriPath(
            ComputeDescriptionService.FACTORY_LINK, DOCKER_COMPUTE_DESC_ID);

    public static final String HOST_DOCKER_ADAPTER_TYPE_PROP_NAME = "__adapterDockerType";
    public static final String NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME = "__Containers";
    public static final String NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME = "__systemContainers";
    public static final String RETRIES_COUNT_PROP_NAME = "__retriesCount";

    public static final String DOCKER_HOST_PORT_PROP_NAME = "__dockerHostPort";
    public static final String DOCKER_HOST_PATH_PROP_NAME = "__dockerHostPath";
    public static final String DOCKER_HOST_SCHEME_PROP_NAME = "__dockerHostScheme";
    public static final String DOCKER_HOST_ADDRESS_PROP_NAME = "__dockerHostAddress";

    public static final String SSL_TRUST_CERT_PROP_NAME = "__sslTrustCertificate";
    public static final String SSL_TRUST_ALIAS_PROP_NAME = "__sslTrustAlias";

    public static final String DOCKER_HOST_AVAILABLE_STORAGE_PROP_NAME = "__StorageAvailable";
    public static final String DOCKER_HOST_TOTAL_STORAGE_PROP_NAME = "__StorageTotal";
    public static final String DOCKER_HOST_TOTAL_MEMORY_PROP_NAME = "__MemTotal";
    public static final String DOCKER_HOST_NUM_CORES_PROP_NAME = "__NCPU";

    public static final String CUSTOM_PROPERTY_DEPLOYMENT_POLICY = "__deploymentPolicyLink";

    public static final String DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME = "__CpuUsage";
    public static final String DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME = "__MemAvailable";

    public static final String DOCKER_HOST_CLUSTER_STORE_PROP_NAME = "__ClusterStore";

    public static final String DOCKER_HOST_PLUGINS_PROP_NAME = "__Plugins";
    public static final String DOCKER_HOST_PLUGINS_VOLUME_PROP_NAME = "Volume";
    public static final String DOCKER_HOST_PLUGINS_NETWORK_PROP_NAME = "Network";

    public enum DockerAdapterType {
        API
    }

    public static class ContainerHostSpec extends HostSpec {
        /** The state for the container host to be created or validated. */
        public ComputeState hostState;

        /** The given container host exists and has to be updated. */
        public Boolean isUpdateOperation;

        /** Configure the docker daemon of the host over ssh. **/
        public Boolean isConfigureOverSsh = Boolean.valueOf(false);

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
        checkForDefaultDockerHostDescription();
        super.handleStart(startPost);
    }

    @Override
    public void handlePut(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("ContainerHostSpec body is required"));
            return;
        }

        ContainerHostSpec hostSpec = op.getBody(ContainerHostSpec.class);
        validate(hostSpec);

        boolean validateHostConnection = op.getUri().getQuery() != null
                && op.getUri().getQuery()
                        .contains(
                                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);

        if (hostSpec.isConfigureOverSsh) {
            configureOverSsh(op, hostSpec, validateHostConnection);
        } else if (validateHostConnection) {
            validateConnection(hostSpec, op);
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
                                    op.fail(new IllegalArgumentException(
                                            CONTAINER_HOST_ALREADY_EXISTS_MESSAGE));
                                } else if (!found.get()) {
                                    createHost(hostSpec, op);
                                }
                            });
        }
    }

    private void configureOverSsh(Operation op, ContainerHostSpec hostSpec,
            boolean validateHostConnection) {
        ConfigureHostOverSshTaskServiceState state = new ConfigureHostOverSshTaskServiceState();
        state.address = hostSpec.uri.getHost();
        state.port = hostSpec.uri.getPort();
        state.authCredentialsLink = hostSpec.hostState.customProperties
                .get(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME);
        state.placementZoneLink = hostSpec.hostState.resourcePoolLink;
        state.tagLinks = hostSpec.hostState.tagLinks;

        if (validateHostConnection) {
            validateConfigureOverSsh(state, op);
        } else {
            Operation
                    .createPost(UriUtils.buildUri(getHost(),
                            ConfigureHostOverSshTaskService.FACTORY_LINK))
                    .setBody(state)
                    .setReferer(getHost().getUri())
                    .setCompletion((completedOp, failure) -> {
                        if (failure != null) {
                            op.fail(failure);
                            return;
                        }

                        // Return the state to the requester for further tracking
                        op.setBody(completedOp
                                .getBody(ConfigureHostOverSshTaskServiceState.class));
                        op.complete();
                    }).sendWith(getHost());
        }
    }

    /**
     * Fetches server certificate and stores its fingerprint as custom property. It is then used
     * as a hash key to get the client certificate when handshaking.
     */
    private void setSslTrustAliasProperty(ContainerHostSpec hostSpec) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            logInfo("No ssl trust validation is performed in test mode...");
            return;
        }

        if (!hostSpec.isSecureScheme()) {
            logInfo("Using non secure channel, skipping SSL validation for %s", hostSpec.uri);
            return;
        }

        try {
            SslCertificateResolver resolver = SslCertificateResolver.connect(hostSpec.uri);

            X509Certificate[] certificateChain = resolver.getCertificateChain();

            String s = CertificateUtil.generatePureFingerPrint(certificateChain);
            if (hostSpec.hostState.customProperties == null) {
                hostSpec.hostState.customProperties = new HashMap<>();
            }
            hostSpec.hostState.customProperties.put(SSL_TRUST_ALIAS_PROP_NAME, s);
        } catch (Exception e) {
            logWarning("Cannot connect to %s to get remote certificate for sslTrustAlias",
                    hostSpec.uri);
        }
    }

    private void validate(ContainerHostSpec hostSpec) {
        final ComputeState cs = hostSpec.hostState;
        AssertUtil.assertNotNull(cs, "computeState");
        AssertUtil.assertNotEmpty(cs.address, "address");
        AssertUtil.assertNotEmpty(cs.customProperties, "customProperties");
        String adapterDockerType = cs.customProperties.get(HOST_DOCKER_ADAPTER_TYPE_PROP_NAME);
        AssertUtil.assertNotEmpty(adapterDockerType, "__adapterDockerType");
        DockerAdapterType adapterType = DockerAdapterType.valueOf(adapterDockerType);
        AssertUtil.assertNotNull(adapterType, "adapterType");

        cs.address = cs.address.trim();
        hostSpec.uri = getHostUri(cs);
    }

    protected void validateConfigureOverSsh(ConfigureHostOverSshTaskServiceState state,
            Operation op) {
        ConfigureHostOverSshTaskService.validate(getHost(), state, (t) -> {
            if (t != null) {
                op.fail(t);
                return;
            }

            completeOperationSuccess(op);
        });
    }

    protected void storeHost(ContainerHostSpec hostSpec, Operation op) {
        ComputeState cs = hostSpec.hostState;
        if (cs.descriptionLink == null) {
            cs.descriptionLink = DOCKER_COMPUTE_DESC_LINK;
        }

        Operation store = null;
        // This should be the case only when using the addHost manually, e.g. unmanaged external
        // host
        if (cs.documentSelfLink == null
                || !cs.documentSelfLink.startsWith(ComputeService.FACTORY_LINK)) {
            store = Operation.createPost(getHost(), ComputeService.FACTORY_LINK)
                    .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);

            if (cs.id == null) {
                cs.id = UUID.randomUUID().toString();
            } else {
                cs.id = ContainerHostUtil.buildHostId(hostSpec.hostState.tenantLinks, cs.id);
            }
            cs.documentSelfLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, cs.id);
            cs.powerState = ComputeService.PowerState.ON;
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

        sendRequest(store
                .setBody(cs)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        op.fail(e);
                        return;
                    }
                    String documentSelfLink = o.getBody(ComputeState.class).documentSelfLink;
                    if (!documentSelfLink.startsWith(ComputeService.FACTORY_LINK)) {
                        documentSelfLink = UriUtils.buildUriPath(
                                ComputeService.FACTORY_LINK, documentSelfLink);
                    }
                    op.addResponseHeader(Operation.LOCATION_HEADER, documentSelfLink);
                    completeOperationSuccess(op);
                    updateContainerHostInfo(documentSelfLink);
                    triggerEpzEnumeration();
                }));
    }

    private void checkForDefaultDockerHostDescription() {
        new ServiceDocumentQuery<>(getHost(), ComputeDescription.class)
                .queryDocument(
                        DOCKER_COMPUTE_DESC_LINK,
                        (r) -> {
                            if (r.hasException()) {
                                r.throwRunTimeException();
                            } else if (r.hasResult()) {
                                logFine("Default docker compute description exists.");
                            } else {
                                ComputeDescription desc = new ComputeDescription();
                                desc.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
                                desc.supportedChildren = new ArrayList<>(
                                        Collections.singletonList(ComputeType.DOCKER_CONTAINER
                                                .name()));
                                desc.documentSelfLink = DOCKER_COMPUTE_DESC_ID;
                                desc.id = DOCKER_COMPUTE_DESC_ID;
                                sendRequest(Operation
                                        .createPost(this, ComputeDescriptionService.FACTORY_LINK)
                                        .setBody(desc)
                                        .setCompletion(
                                                (o, e) -> {
                                                    if (e != null) {
                                                        logWarning(
                                                                "Default docker description can't be created. Exception: %s",
                                                                e instanceof CancellationException
                                                                        ? e.getMessage() : Utils
                                                                                .toString(e));
                                                        return;
                                                    }
                                                    logInfo("Default docker description created with self link: "
                                                            + DOCKER_COMPUTE_DESC_LINK);
                                                }));
                            }
                        });
    }

    private URI getHostUri(ComputeState hostState) {
        return ContainerDescription.getDockerHostUri(hostState);
    }

    private void pingHost(ContainerHostSpec hostSpec, Operation op,
            SslTrustCertificateState sslTrust, Runnable callbackFunction) {

        ComputeState cs = hostSpec.hostState;

        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = ContainerHostOperationType.PING.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = UriUtils.buildUri(getHost(), ComputeService.FACTORY_LINK);
        request.customProperties = cs.customProperties == null ?
                new HashMap<>() :
                new HashMap<>(cs.customProperties);
        request.customProperties.putIfAbsent(ContainerHostService.DOCKER_HOST_ADDRESS_PROP_NAME,
                cs.address);

        if (sslTrust != null) {
            request.customProperties.put(SSL_TRUST_CERT_PROP_NAME, sslTrust.certificate);
            request.customProperties.put(SSL_TRUST_ALIAS_PROP_NAME,
                    SslTrustCertificateFactoryService.generateSelfLink(sslTrust));
        }

        sendRequest(Operation
                .createPatch(this, ManagementUriParts.ADAPTER_DOCKER_HOST)
                .setBody(request)
                .setContextId(Service.getId(getSelfLink()))
                .setCompletion((o, ex) -> {
                    if (o.getStatusCode() == HttpURLConnection.HTTP_BAD_GATEWAY) {
                        logFine("Got bad gateway response for %s", o.getUri());
                        @SuppressWarnings("unchecked")
                        Map<String, String> identification = o.getBody(Map.class);

                        // update the ComputeState with the new identification and try to ping again
                        if (hostSpec.acceptCertificate) {

                            // the host is not stored yet so just add the custom property the object
                            logInfo("Updating SSH host key: %s", cs.documentSelfLink);

                            pingHost(hostSpec, op, sslTrust, callbackFunction);

                        } else {
                            logWarning("Untrusted server, returning to client for approval: %s",
                                    cs.documentSourceLink);
                            op.setStatusCode(HttpURLConnection.HTTP_BAD_GATEWAY);
                            op.setBody(identification);
                            op.complete();
                        }
                    } else {
                        if (ex != null) {
                            ServiceErrorResponse rsp = Utils.toServiceErrorResponse(ex);
                            toReadableErrorMessage(ex, rsp);
                            rsp.message = String.format("Error connecting to %s : %s",
                                    cs.address, rsp.message);

                            logWarning(rsp.message);
                            postEventlogError(cs, rsp.message);
                            op.setStatusCode(o.getStatusCode());
                            op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
                            op.fail(ex, rsp);
                            return;
                        }

                        callbackFunction.run();
                    }
                }));
    }

    private void toReadableErrorMessage(Throwable e, ServiceErrorResponse response) {
        if (e instanceof io.netty.handler.codec.DecoderException) {
            if (response.message.contains("Received fatal alert: bad_certificate")) {
                response.message = "Check login credentials";
            }
        } else if (e instanceof IllegalStateException) {
            if (response.message.contains("Socket channel closed:")) {
                response.message = "Check login credentials";
            }
        }
    }

    private void updateContainerHostInfo(String documentSelfLink) {
        URI uri = UriUtils.buildUri(getHost(),
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK);
        ContainerHostDataCollectionState state = new ContainerHostDataCollectionState();
        state.computeContainerHostLinks = Collections.singleton(documentSelfLink);
        sendRequest(Operation.createPatch(uri)
                .setBody(state)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to update host data collection: %s", ex.getMessage());
                    }
                }));
    }

    private void triggerEpzEnumeration() {
        EpzComputeEnumerationTaskService.triggerForAllResourcePools(this);
    }

    private void validateSslTrust(ContainerHostSpec hostSpec, Operation op,
            Runnable callbackFunction) {
        EndpointCertificateUtil.validateSslTrust(this, hostSpec, op, callbackFunction);
    }

    private void createHost(ContainerHostSpec hostSpec, Operation op) {
        setSslTrustAliasProperty(hostSpec);

        if (hostSpec.acceptHostAddress) {
            if (hostSpec.acceptCertificate) {
                op.nestCompletion((o) -> {
                    EndpointCertificateUtil.validateSslTrust(this, hostSpec, o, op::complete);
                });
            }

            storeHost(hostSpec, op);
        } else {
            validateSslTrust(hostSpec, op, () -> {
                pingHost(hostSpec, op, hostSpec.sslTrust, () -> storeHost(hostSpec, op));
            });
        }
    }

    private void updateHost(ContainerHostSpec hostSpec, Operation op) {
        setSslTrustAliasProperty(hostSpec);

        ComputeState cs = hostSpec.hostState;
        sendRequest(Operation.createPut(this, cs.documentSelfLink)
                .setBody(cs)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        op.fail(e);
                        return;
                    }
                    completeOperationSuccess(op);
                    updateContainerHostInfo(cs.documentSelfLink);
                    triggerEpzEnumeration();
                }));
    }

    private void validateConnection(ContainerHostSpec hostSpec, Operation op) {
        validateSslTrust(hostSpec, op, () -> {
            setSslTrustAliasProperty(hostSpec);
            pingHost(hostSpec, op, hostSpec.sslTrust, () -> completeOperationSuccess(op));
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

}
