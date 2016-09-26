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

package com.vmware.admiral.test.integration.compute.vsphere;

import java.net.URI;

import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.test.integration.SimpleHttpsClient;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.ImportOvfRequest;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfImporterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class VsphereComputeProvisionOvfIT extends VsphereComputeProvisionIT {

    public static final String OVF_URI = "test.vsphere.ovf.uri";

    @Override
    protected ComputeDescription createComputeDescription(
            EndpointType endpointType,
            TestDocumentLifeCycle documentLifeCycle) throws Exception {

        ComputeDescription computeDesc = prepareComputeDescription(endpointType);
        //remove the image key, as we don't need for Ovf provisioning
        computeDesc.customProperties
                .remove(ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_IMAGE_ID_NAME);

        importOvf(computeDesc);

        return findComputeDescription();
    }

    private void importOvf(ComputeDescription computeDesc)
            throws Exception {
        ImportOvfRequest req = new ImportOvfRequest();
        req.ovfUri = URI.create(getTestProp(OVF_URI));
        req.template = computeDesc;

        sendRequest(SimpleHttpsClient.HttpMethod.PATCH, OvfImporterService.SELF_LINK,
                Utils.toJson(req));
    }

    /**
     * Do a query to get the ComputeDescription. Perhaps the OvfImporter should return the
     * links of the created descriptions?
     */
    private ComputeDescription findComputeDescription() throws Exception {

        QueryTask.QuerySpecification qs = new QueryTask.QuerySpecification();
        qs.query.addBooleanClause(QueryTask.Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_ID, "ovf-*",
                        QueryTask.QueryTerm.MatchType.WILDCARD).build());
        QueryTask qt = QueryTask.create(qs).setDirect(true);

        String resultJson = sendRequest(SimpleHttpsClient.HttpMethod.POST,
                ServiceUriPaths.CORE_QUERY_TASKS, Utils.toJson(qt));

        QueryTask result = Utils.fromJson(resultJson, QueryTask.class);
        result.results.documentLinks.get(0);

        String descJson = sendRequest(SimpleHttpsClient.HttpMethod.GET,
                result.results.documentLinks.get(0), null);
        return Utils.fromJson(descJson, ComputeDescription.class);
    }

}
