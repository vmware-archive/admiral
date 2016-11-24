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

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.admiral.service.common.SslTrustImportService.SslTrustImportRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Service for distribution of self-signed trusted registry certificates to a single docker host.
 */
public class HostConfigCertificateDistributionService extends
        AbstractCertificateDistributionService {
    public static final String SELF_LINK = ManagementUriParts.CERT_DISTRIBUTION_ADD_HOST;

    public static class HostConfigCertificateDistributionState {
        public String hostLink;
        public List<String> tenantLinks;
    }

    @Override
    public void handlePost(Operation op) {
        try {
            HostConfigCertificateDistributionState distState =
                    op.getBody(HostConfigCertificateDistributionState.class);
            AssertUtil.assertNotNull(distState.hostLink, "hostLink");

            handleAddDockerHostOperation(distState.hostLink, distState.tenantLinks);
        } catch (Throwable t) {
            logSevere("Failed to process certificate distributuon request. %s", Utils.toString(t));
        }
    }

    private void handleAddDockerHostOperation(String hostLink, List<String> tenantLinks) {
        sendRequest(Operation.createGet(this, RegistryService.FACTORY_LINK)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to retrieve registry links. %s", Utils.toString(e));
                        return;
                    }

                    ServiceDocumentQueryResult body = o.getBody(ServiceDocumentQueryResult.class);

                    logFine("Distributing certificates for [%s]", body.documentLinks);
                    for (String registryLink : body.documentLinks) {
                        fetchRegistryState(registryLink, (registry) -> {
                            fetchSslTrustLink(registry.address, (sslTrustLink) -> {
                                fetchSslTrustCertificate(sslTrustLink, (cert) -> {
                                    uploadCertificate(hostLink, registry.address, cert,
                                            tenantLinks);
                                });
                            });
                        });
                    }
                }));
    }

    private void fetchRegistryState(String registryLink, Consumer<RegistryState> callback) {
        sendRequest(Operation.createGet(this, registryLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to retrieve registry state for %s. %s",
                                registryLink, Utils.toString(e));
                        return;
                    }
                    RegistryState registry = o.getBody(RegistryState.class);

                    callback.accept(registry);
                }));
    }

    private void fetchSslTrustLink(String registryAddress, Consumer<String> callback) {
        SslTrustImportRequest sslTrustRequest = new SslTrustImportRequest();
        sslTrustRequest.hostUri = URI.create(registryAddress);

        sendRequest(Operation.createPut(this, SslTrustImportService.SELF_LINK)
                .setBody(sslTrustRequest)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to to connect to registry host %s. "
                                + "SSL certificate cannot be retrieved. %s",
                                registryAddress, Utils.toString(ex));
                    } else {
                        // if location header is present it means a trusted self-signed
                        // certificate has been stored
                        String sslTrustLink = op.getResponseHeader(
                                Operation.LOCATION_HEADER);
                        if (sslTrustLink != null) {
                            callback.accept(sslTrustLink);

                        }
                    }
                }));
    }

    private void fetchSslTrustCertificate(String sslTrustLink, Consumer<String> callback) {

        logFine("Fetching ssl trust: %s", sslTrustLink);
        Operation fetchSslTrust = Operation.createGet(UriUtils.buildUri(getHost(), sslTrustLink))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to retrieve ssl trust state for %s. %s",
                                sslTrustLink, Utils.toString(ex));
                        return;
                    }

                    SslTrustCertificateState sslTrustState = o
                            .getBody(SslTrustCertificateState.class);
                    callback.accept(sslTrustState.certificate);
                });

        sendRequest(fetchSslTrust);
    }
}
