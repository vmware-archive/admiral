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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

public class ContainerDeploymentIT extends BaseProvisioningOnCoreOsIT {

    @Before
    public void setUp() throws Exception {
        setupCoreOsHost(DockerAdapterType.API);
    }

    @Test
    public void testFailContainerOnStartCleanup() throws Exception {
        ContainerDescription containerDescription = createContainerDescription();

        try {
            requestContainer(containerDescription.documentSelfLink);
            fail("Provisioning container expected to fail");
        } catch (IllegalStateException e) {
            // Verify no containers with the name of the container description are left
            String query = "/resources/containers?%24filter=names.item%20eq%20%27"
                    + containerDescription.name + "*%27";
            ServiceDocumentQueryResult result =
                    getDocument(query, ServiceDocumentQueryResult.class);
            assertEquals(0, result.documentCount.intValue());
        }
    }


    private ContainerDescription createContainerDescription()
            throws Exception {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.image = "httpd";
        containerDesc.name = "test-failing-container-cleanup";
        containerDesc.portBindings = new PortBinding[] { PortBinding
                .fromDockerPortMapping(DockerPortMapping.fromString("8080::80")) };
        containerDesc.command = new String[] { "non-existing-command" };

        containerDesc = postDocument(ContainerDescriptionService.FACTORY_LINK, containerDesc);
        documentsForDeletion.add(containerDesc);

        return containerDesc;
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {
        return null;
    }

}
