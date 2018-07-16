/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.pks;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSConstants;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.TenantLinksUtil;
import com.vmware.admiral.compute.EndpointCertificateUtil;
import com.vmware.admiral.compute.HostSpec;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class PKSCreateEndpointService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.PKS_CREATE_ENDPOINT;

    public static class EndpointSpec extends HostSpec {

        /**
         * The endpoint exists and has to be updated.
         */
        public Boolean isUpdateOperation;
        public String acceptCertificateForHost;
        public Endpoint endpoint;

        URI uaaUri;
        URI apiUri;

        @Override
        public boolean isSecureScheme() {
            return uri == null || !UriUtils.HTTP_SCHEME.equals(uri.getScheme());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<String> getHostTenantLinks() {
            return endpoint == null ? null : endpoint.tenantLinks;
        }

    }

    @Override
    public void handlePut(Operation op) {
        if (!op.hasBody()) {
            op.fail(new LocalizableValidationException("body is required",
                    "compute.body.required"));
            return;
        }

        EndpointSpec endpointSpec = op.getBody(EndpointSpec.class);
        validate(endpointSpec);

        String query = op.getUri().getQuery();
        boolean validateConnection = query != null
                && query.contains(ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);

        if (validateConnection) {
            validateConnection(endpointSpec, op);
        } else if (endpointSpec.isUpdateOperation != null && endpointSpec.isUpdateOperation) {
            createOrUpdateEndpoint(endpointSpec, op);
        } else {
            QueryTask q = QueryUtil.buildPropertyQuery(Endpoint.class,
                    Endpoint.FIELD_NAME_API_ENDPOINT,
                    endpointSpec.endpoint.apiEndpoint);

            List<String> tenantLinks = endpointSpec.getHostTenantLinks();
            if (tenantLinks != null) {
                tenantLinks = tenantLinks.stream()
                        .filter(TenantLinksUtil::isTenantLink)
                        .collect(Collectors.toList());
            }

            if (tenantLinks != null && !tenantLinks.isEmpty()) {
                q.querySpec.query
                        .addBooleanClause(QueryUtil.addTenantGroupAndUserClause(tenantLinks));
            }

            AtomicBoolean found = new AtomicBoolean(false);
            new ServiceDocumentQuery<>(getHost(), Endpoint.class)
                    .query(q, (r) -> {
                        if (r.hasException()) {
                            op.fail(r.getException());
                        } else if (r.hasResult()) {
                            found.set(true);
                            op.fail(new LocalizableValidationException(
                                    "Endpoint already exists", "compute.endpoint.exists"));
                        } else if (!found.get()) {
                            createOrUpdateEndpoint(endpointSpec, op);
                        }
                    });
        }
    }

    private void validateSslTrust(EndpointSpec spec, Operation op, Runnable callback) {
        boolean acceptAll = "*".equals(spec.acceptCertificateForHost);

        spec.uri = spec.uaaUri;
        String uaaUri = spec.uaaUri.toString();
        spec.acceptCertificate = uaaUri.equals(spec.acceptCertificateForHost) || acceptAll;
        EndpointCertificateUtil.validateSslTrust(this, spec, op, () -> {
            spec.uri = spec.apiUri;
            String apiUri = spec.apiUri.toString();
            spec.acceptCertificate = apiUri.equals(spec.acceptCertificateForHost) || acceptAll;
            spec.sslTrust = null;
            EndpointCertificateUtil.validateSslTrust(this, spec, op, callback);
        });
    }

    private void validateConnection(EndpointSpec endpointSpec, Operation op) {
        validateSslTrust(endpointSpec, op,
                () -> pingEndpoint(endpointSpec, op, (ignored) -> completeOperationSuccess(op))
        );
    }

    private void createOrUpdateEndpoint(EndpointSpec endpointSpec, Operation op) {
        if (endpointSpec.acceptHostAddress) {
            if (endpointSpec.acceptCertificate) {
                Operation o = Operation.createGet(null)
                        .setCompletion((completedOp, e) -> {
                            if (e != null) {
                                storeEndpoint(endpointSpec, op);
                            } else {
                                op.setStatusCode(completedOp.getStatusCode());
                                op.transferResponseHeadersFrom(completedOp);
                                op.setBodyNoCloning(completedOp.getBodyRaw());
                                op.complete();
                            }
                        });
                validateSslTrust(endpointSpec, o, () -> storeEndpoint(endpointSpec, op));
            } else {
                storeEndpoint(endpointSpec, op);
            }
        } else {
            validateSslTrust(endpointSpec, op, () -> storeEndpoint(endpointSpec, op));
        }
    }

    private void storeEndpoint(EndpointSpec endpointSpec, Operation op) {
        if (endpointSpec.acceptHostAddress) {
            doStoreEndpoint(endpointSpec, op);
        } else {
            // connect to endpoint
            pingEndpoint(endpointSpec, op, (ignored) -> doStoreEndpoint(endpointSpec, op));
        }
    }

    private void pingEndpoint(EndpointSpec spec, Operation op, Consumer<Object> callback) {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = PKSOperationType.LIST_PLANS.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = URI.create("");
        request.customProperties = new HashMap<>(6);
        request.customProperties.put(PKSConstants.VALIDATE_CONNECTION, "true");
        request.customProperties.put(PKSConstants.CREDENTIALS_LINK, spec.endpoint.authCredentialsLink);
        request.customProperties.put(Endpoint.FIELD_NAME_UAA_ENDPOINT, spec.uaaUri.toString());
        request.customProperties.put(Endpoint.FIELD_NAME_API_ENDPOINT, spec.apiUri.toString());

        Operation patchOp = Operation
                .createPatch(this, ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(request)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        LocalizableValidationException localizedEx =
                                new LocalizableValidationException(e,
                                String.format("Unexpected error: %s", e.getMessage()),
                                "compute.unexpected.error", e.getMessage());

                        ServiceErrorResponse rsp = Utils.toValidationErrorResponse(localizedEx, op);

                        logWarning("Error sending adapter request with type %s : %s. Cause: %s",
                                request.operationTypeId, rsp.message, Utils.toString(e));
                        op.setStatusCode(o.getStatusCode());
                        op.fail(localizedEx, rsp);
                        return;
                    }

                    if (callback != null) {
                        callback.accept(null);
                    }
                });

        String languageHeader = op.getRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER);
        if (languageHeader != null) {
            patchOp.addRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER, languageHeader);
        }
        sendRequest(patchOp);
    }

    private void doStoreEndpoint(EndpointSpec endpointSpec, Operation op) {
        Endpoint e = endpointSpec.endpoint;

        if (e.id == null) {
            e.id = UUID.randomUUID().toString();
        }

        Operation store;
        if (e.documentSelfLink == null
                || !e.documentSelfLink.startsWith(PKSEndpointFactoryService.SELF_LINK)) {
            store = Operation
                    .createPost(getHost(), PKSEndpointFactoryService.SELF_LINK)
                    .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
        } else {
            store = Operation.createPut(getHost(), e.documentSelfLink);
        }
        if (e.creationTimeMicros == null) {
            e.creationTimeMicros = Utils.getSystemNowMicrosUtc();
        }

        store.setBody(e)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        op.fail(ex);
                        return;
                    }
                    Endpoint endpoint = o.getBody(Endpoint.class);
                    String documentSelfLink = endpoint.documentSelfLink;
                    if (!documentSelfLink.startsWith(PKSEndpointFactoryService.SELF_LINK)) {
                        documentSelfLink = UriUtils.buildUriPath(
                                PKSEndpointFactoryService.SELF_LINK, documentSelfLink);
                    }

                    op.addResponseHeader(Operation.LOCATION_HEADER, documentSelfLink);
                    completeOperationSuccess(op);
                })
                .sendWith(this);
    }

    private void completeOperationSuccess(Operation op) {
        op.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
        op.setBody(null);
        op.complete();
    }

    private void validate(EndpointSpec spec) {
        AssertUtil.assertNotNull(spec, "endpoint spec");
        AssertUtil.assertNotNull(spec.endpoint, "endpoint");
        AssertUtil.assertNotNullOrEmpty(spec.endpoint.uaaEndpoint, "UAA endpoint");
        AssertUtil.assertNotNullOrEmpty(spec.endpoint.apiEndpoint, "API endpoint");

        URI uri;
        Endpoint e = spec.endpoint;

        try {
            logInfo("Parsing uaa endpoint: %s", e.uaaEndpoint);
            uri = URI.create(e.uaaEndpoint);
        } catch (Exception ex) {
            throw new LocalizableValidationException("Invalid host address: " + e.uaaEndpoint,
                    "common.host.address.invalid", e.uaaEndpoint);
        }
        validateUri(uri);
        spec.uaaUri = uri;

        try {
            logInfo("Parsing api endpoint: %s", e.apiEndpoint);
            uri = URI.create(e.apiEndpoint);
        } catch (Exception ex) {
            throw new LocalizableValidationException("Invalid host address: " + e.apiEndpoint,
                    "common.host.address.invalid", e.apiEndpoint);
        }
        validateUri(uri);
        spec.apiUri = uri;
    }

    private void validateUri(URI uri) {
        if (!UriUtils.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())
                && !UriUtils.HTTP_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new LocalizableValidationException("Unsupported scheme: " + uri.getScheme(),
                    "common.unsupported.scheme", uri.getScheme());
        }
    }

}
