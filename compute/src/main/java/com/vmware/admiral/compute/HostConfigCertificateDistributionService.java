/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute;

import static com.vmware.admiral.common.util.CertificateUtilExtended.isSelfSignedCertificate;

import java.util.List;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
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

            op.complete();

            handleAddDockerHostOperation(distState.hostLink, distState.tenantLinks);
        } catch (Throwable t) {
            logSevere("Failed to process certificate distribution request. %s", Utils.toString(t));
            op.fail(t);
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
                            RegistryService.fetchRegistryCertificate(registry, (cert) -> {
                                if (!isSelfSignedCertificate(cert)) {
                                    logInfo("Skip certificate distribution for registry [%s]: "
                                                    + "certificate not self-signed.",
                                            registryLink);
                                    return;
                                }
                                uploadCertificate(hostLink, registry.address, cert, tenantLinks);
                            }, getHost());
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

}
