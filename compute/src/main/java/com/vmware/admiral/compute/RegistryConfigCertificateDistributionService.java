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

import static com.vmware.admiral.common.util.CertificateUtilExtended.isSelfSignedCertificate;

import java.util.ArrayList;
import java.util.List;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

/**
 * Service for distribution of self-signed trusted registry certificate to all docker hosts.
 */
public class RegistryConfigCertificateDistributionService
        extends AbstractCertificateDistributionService {

    public static final String SELF_LINK = ManagementUriParts.CERT_DISTRIBUTION_ADD_REGISTRY;

    public static class RegistryConfigCertificateDistributionState {
        public String registryAddress;
        public SslTrustCertificateState certState;
        public List<String> tenantLinks;
    }

    @Override
    public void handlePost(Operation op) {
        try {
            RegistryConfigCertificateDistributionState distState = op
                    .getBody(RegistryConfigCertificateDistributionState.class);

            AssertUtil.assertNotNull(distState.certState, "certState");
            AssertUtil.assertNotNull(distState.registryAddress, "registryAddress");

            op.complete();

            if (!isSelfSignedCertificate(distState.certState.certificate)) {
                logInfo("Skip certificate distribution for registry [%s]: certificate not self-signed.",
                        distState.registryAddress);
                return;
            }

            handleAddRegistryHostOperation(distState.registryAddress,
                    distState.certState.certificate, distState.tenantLinks);
        } catch (Throwable t) {
            logSevere("Failed to process certificate distribution request: %s", Utils.toString(t));
            op.fail(t);
        }
    }

    private void handleAddRegistryHostOperation(String registryAddress, String certificate,
            List<String> tenantLinks) {

        QueryTask q = QueryUtil.buildQuery(ComputeState.class, true);
        QueryTask.Query hostTypeClause = new QueryTask.Query()
                .setTermPropertyName(QuerySpecification.buildCompositeFieldName(
                        ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME))
                .setTermMatchValue(ContainerHostType.DOCKER.toString());
        q.querySpec.query.addBooleanClause(hostTypeClause);
        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
        q.documentExpirationTimeMicros = ServiceDocumentQuery.getDefaultQueryExpiration();

        List<String> hostLinks = new ArrayList<>();
        ServiceDocumentQuery<ComputeState> query = new ServiceDocumentQuery<>(getHost(),
                ComputeState.class);
        query.query(q, (r) -> {
            if (r.hasException()) {
                logWarning("Exception while retrieving docker host states. Error: %s",
                        Utils.toString(r.getException()));
            } else if (r.hasResult()) {
                hostLinks.add(r.getDocumentSelfLink());
            } else {
                for (String hostLink : hostLinks) {
                    uploadCertificate(hostLink, registryAddress, certificate, tenantLinks);
                }
            }
        });
    }
}
