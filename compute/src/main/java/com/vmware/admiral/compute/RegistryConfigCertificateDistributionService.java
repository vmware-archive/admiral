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

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;

/**
 * Service for distribution of self-signed trusted registry certificate to all docker hosts.
 */
public class RegistryConfigCertificateDistributionService
        extends AbstractCertificateDistributionService {

    public static final String SELF_LINK = ManagementUriParts.CERT_DISTRIBUTION_ADD_REGISTRY;

    public static class RegistryConfigCertificateDistributionState {
        public String registryAddress;
        public SslTrustCertificateState certState;
    }

    @Override
    public void handlePost(Operation op) {
        try {
            RegistryConfigCertificateDistributionState distState = op
                    .getBody(RegistryConfigCertificateDistributionState.class);

            AssertUtil.assertNotNull(distState.certState, "certState");
            AssertUtil.assertNotNull(distState.registryAddress, "registryAddress");
            String dirName = getCertificateDirName(distState.registryAddress);

            handleAddRegistryHostOperation(dirName, distState.certState.certificate);
        } catch (Throwable t) {
            logSevere("Failed to process certificate distribution request: %s", Utils.toString(t));
        }
    }

    private void handleAddRegistryHostOperation(String dirName, String certificate) {
        sendRequest(Operation.createGet(this, ComputeService.FACTORY_LINK)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        o.fail(ex);
                    } else {
                        ServiceDocumentQueryResult doc = o
                                .getBody(ServiceDocumentQueryResult.class);
                        for (String hostLink : doc.documentLinks) {
                            uploadCertificate(hostLink, dirName, certificate);
                        }
                    }
                }));
    }
}
