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

package com.vmware.admiral.test.integration.compute.azure;

import java.util.Set;

import org.junit.Ignore;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;

@Ignore("We have to modify first the CI jobs to pass the Azure accessKey,secret,subscriptionId and tenant")
public class AzureDockerHostProvisionIT extends AzureComputeProvisionIT {

    @Override
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {
        ContainerDescription containerDescription = createContainerDescription();
        requestContainerAndDelete(containerDescription.documentSelfLink);
    }

    @Override
    protected void extendComputeDescription(ComputeDescription computeDescription)
            throws Exception {
        super.extendComputeDescription(computeDescription);

        enableContainerHost(computeDescription);

    }

    @Override
    protected void doSetUp() throws Exception {
        doSetupContainerHostPrereq();
    }

}
