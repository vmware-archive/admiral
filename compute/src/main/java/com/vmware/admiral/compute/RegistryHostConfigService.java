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

import static com.vmware.admiral.compute.EndpointCertificateUtil.REQUEST_PARAM_VALIDATE_OPERATION_NAME;
import static com.vmware.admiral.compute.EndpointCertificateUtil.validateSslTrust;
import static com.vmware.admiral.service.common.RegistryService.API_VERSION_PROP_NAME;

import java.net.HttpURLConnection;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.SSLException;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ImageOperationType;
import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.adapter.registry.service.RegistryAdapterService.RegistryPingResponse;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.RegistryConfigCertificateDistributionService.RegistryConfigCertificateDistributionState;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.KeyUtil;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper service for adding and validating docker registries
 */
public class RegistryHostConfigService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.REGISTRY_HOSTS;

    /**
     * name of the feature toggle that allows plain HTTP connections to registries
     */
    public static final String ALLOW_REGISTRY_PLAIN_HTTP_CONNECTION_PROP_NAME = "allow.registry.plain.http.connection";

    public static class RegistryHostSpec extends HostSpec {
        /** The state for the registry host to be created or validated. */
        public RegistryState hostState;

        @Override
        public boolean isSecureScheme() {
            if (uri != null && UriUtils.HTTP_SCHEME.equals(uri.getScheme())) {
                return false;
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
    public void handleStart(Operation post) {
        // we depend on another service, start, when it starts
        getHost().registerForServiceAvailability((o, e) -> {
            if (e != null) {
                post.fail(e);
            } else {
                post.complete();
            }
        }, RegistryConfigCertificateDistributionService.SELF_LINK);
    }

    @Override
    public void handlePut(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("RegistryHostSpec body is required"));
            return;
        }

        RegistryHostSpec hostSpec = op.getBody(RegistryHostSpec.class);
        validate(hostSpec);

        boolean validateHostConnection = op.getUri().getQuery() != null
                && op.getUri().getQuery().contains(REQUEST_PARAM_VALIDATE_OPERATION_NAME);

        if (validateHostConnection) {
            validateConnection(hostSpec, op, false);
        } else {
            createHost(hostSpec, op);
        }
    }

    private void validate(RegistryHostSpec hostSpec) {
        RegistryState state = hostSpec.hostState;
        AssertUtil.assertNotNull(state, "registryState");
        AssertUtil.assertNotNull(state.address, "registryState.address");
        AssertUtil.assertNotNull(state.name, "registryState.name");
        state.address = state.address.trim().replaceAll("/+$", "");
        state.name = state.name.trim();

        hostSpec.uri = getHostUri(state);
    }

    private void storeHost(RegistryHostSpec hostSpec, Operation op) {
        RegistryState hostState = hostSpec.hostState;
        Operation store = null;
        if (hostState.documentSelfLink == null
                || !hostState.documentSelfLink.startsWith(RegistryFactoryService.SELF_LINK)) {
            URI uri = UriUtils.buildUri(getHost(), RegistryFactoryService.SELF_LINK);
            store = OperationUtil.createForcedPost(uri);
        } else {
            URI uri = UriUtils.buildUri(getHost(), hostState.documentSelfLink);
            store = Operation.createPut(uri);
        }

        sendRequest(store
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setContextId(op.getContextId())
                .setBody(hostState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logSevere("Failed to store registry %s", hostState.address);
                                op.fail(e);
                                return;
                            }

                            logInfo("Stored registry %s", hostState.address);

                            RegistryState rs = o.getBody(RegistryState.class);
                            String documentSelfLink = rs.documentSelfLink;
                            op.addResponseHeader(Operation.LOCATION_HEADER, documentSelfLink);

                            // nest completion for success only. If distribute cert works, we get
                            // called,
                            // otherwise parent operation will complete with failure
                            op.nestCompletion((nestedOp) -> {
                                completeOperationSuccess(op);
                            });
                            distributeCertificate(hostState, op);
                        }));
    }

    private URI getHostUri(RegistryState hostState) {
        return UriUtilsExtended.buildDockerRegistryUri(hostState.address);
    }

    private void pingHost(RegistryHostSpec hostSpec, Operation op,
            SslTrustCertificateState sslTrust, Runnable callbackFunction) {

        RegistryState hostState = hostSpec.hostState;

        try {
            if (sslTrust != null) {
                validateHostAddress(hostState, sslTrust);
            }
        } catch (LocalizableValidationException e) {
            failOperation(hostSpec, op, e);
            return;
        }

        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = ImageOperationType.PING.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = getHostUri(hostState);

        request.customProperties = new HashMap<>();
        if (sslTrust != null) {
            request.customProperties.put(RegistryAdapterService.SSL_TRUST_CERT_PROP_NAME,
                    sslTrust.certificate);

            request.customProperties.put(RegistryAdapterService.SSL_TRUST_ALIAS_PROP_NAME,
                    sslTrust.getAlias());
        }
        if (hostState.authCredentialsLink != null) {
            request.customProperties.put(RegistryState.FIELD_NAME_AUTH_CREDENTIALS_LINK,
                    hostState.authCredentialsLink);
        }

        sendRequest(Operation
                .createPatch(this, ManagementUriParts.ADAPTER_REGISTRY)
                .setBody(request)
                .setContextId(request.getRequestId())
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failOperation(hostSpec, op, ex);
                        return;
                    }

                    String apiVersion = o.getBody(RegistryPingResponse.class).apiVersion.name();
                    if (hostSpec.hostState.customProperties == null) {
                        hostSpec.hostState.customProperties = new HashMap<>();
                    }
                    hostSpec.hostState.customProperties.put(API_VERSION_PROP_NAME, apiVersion);
                    callbackFunction.run();
                }));
    }

    private void createHost(RegistryHostSpec hostSpec, Operation op) {
        if (hostSpec.acceptHostAddress) {
            storeHost(hostSpec, op);
        } else {
            validateConnection( hostSpec,  op,  true);
        }
    }

    private void validateConnection(RegistryHostSpec hostSpec, Operation op, boolean storeHost) {
        validatePlainHttpConnection(this, hostSpec, op,
                () ->
                        validateSslTrust(this, hostSpec, op,
                                () -> {

                                    if (hostSpec.sslTrust != null && hostSpec.hostState != null) {
                                        if (hostSpec.hostState.customProperties == null) {
                                            hostSpec.hostState.customProperties = new HashMap<>();
                                        }
                                        hostSpec.hostState.customProperties.put(RegistryService
                                                .REGISTRY_TRUST_CERTS_PROP_NAME, UriUtils
                                                .buildUriPath( SslTrustCertificateService
                                                        .FACTORY_LINK, hostSpec
                                                .sslTrust.documentSelfLink));
                                    }

                                    pingHost(hostSpec, op, hostSpec.sslTrust,
                                            () -> {
                                                if (storeHost) {
                                                    storeHost(hostSpec, op);
                                                } else {
                                                    completeOperationSuccess(op);
                                                }
                                            });
                                })
        );
    }

    private void validatePlainHttpConnection(Service sender, HostSpec hostSpec, Operation op,
            Runnable callbackFunction) {
        boolean allowRegistryInsecureConnection = Boolean.valueOf(
                ConfigurationUtil.getProperty(ALLOW_REGISTRY_PLAIN_HTTP_CONNECTION_PROP_NAME));
        if (!hostSpec.isSecureScheme() && !allowRegistryInsecureConnection) {
            String message = String.format("Plain HTTP registry connections are not allowed.");
            LocalizableValidationException ex = new LocalizableValidationException(message,
                    "compute.registry.plain.http.not.allowed");
            ServiceErrorResponse rsp = Utils.toValidationErrorResponse(ex, op);
            logSevere(rsp.message);
            op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
            op.fail(ex, rsp);
        } else {
            callbackFunction.run();
        }
    }

    private void distributeCertificate(RegistryState registry, Operation parentOp) {
        parentOp.complete();

        logInfo("Distributing certificate for %s. Fetching registry certificate", registry.address);

        RegistryService.fetchRegistryCertificate(registry, (certificate) -> {
            RegistryConfigCertificateDistributionState distributionState = new RegistryConfigCertificateDistributionState();
            distributionState.registryAddress = registry.address;
            distributionState.tenantLinks = registry.tenantLinks;
            distributionState.certState = new SslTrustCertificateState();
            distributionState.certState.certificate = certificate;

            logInfo("Distributing certificate for %s. Fetched registry certificate.",
                    registry.address);

            sendRequest(Operation.createPost(this,
                    RegistryConfigCertificateDistributionService.SELF_LINK)
                    .setContextId(parentOp.getContextId())
                    .setBody(distributionState));
        }, getHost());
    }

    /**
     * Validates that certificate CN equals the hostname specified by user. Docker daemon will be
     * later instructed to find and trust this certificate only if these two matches. See:
     * https://docs.docker.com/docker-trusted-registry/userguide/
     */
    private void validateHostAddress(RegistryState state, SslTrustCertificateState sslTrust) {

        String hostname = UriUtilsExtended.extractHost(state.address);

        X509Certificate certificate = null;

        try {
            certificate = KeyUtil.decodeCertificate(sslTrust.certificate);
        } catch (CertificateException e1) {
            throw new LocalizableValidationException(
                    String.format("Invalid certificate provided from host: %s", hostname),
                    "compute.registry.host.address.invalid.certificate", hostname);
        }

        try {
            new DefaultHostnameVerifier().verify(hostname, certificate);
        } catch (SSLException e) {
            String errorMessage = String.format(
                    "Registry hostname (%s) does not match certificates CN (%s).",
                    hostname, sslTrust.commonName);
            throw new LocalizableValidationException(errorMessage,
                    "compute.registry.host.name.mismatch", hostname, sslTrust.commonName);
        }

    }

    private void completeOperationSuccess(Operation op) {
        op.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
        op.setBody(null);
        op.complete();
    }

    private void failOperation(RegistryHostSpec hostSpec, Operation op, Throwable t) {
        String errMsg = String.format("Operation for registry %s failed: %s",
                hostSpec.uri, t.getMessage());
        op.fail(new Exception(errMsg, t));
    }
}
