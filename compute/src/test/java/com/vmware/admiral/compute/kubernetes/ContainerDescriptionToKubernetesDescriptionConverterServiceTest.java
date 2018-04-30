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

package com.vmware.admiral.compute.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.logging.Level;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.kubernetes.service.ContainerDescriptionToKubernetesDescriptionConverterService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.xenon.common.UriUtils;

public class ContainerDescriptionToKubernetesDescriptionConverterServiceTest extends ComputeBaseTest {
    private static final String CONTAINER_DESC_NAME = "dcp-test";
    private static final String CONTAINER_DESC_IMAGE = "dcp-test:latest";
    private static final int CONTAINER_DESC_CLUSTER_SIZE = 4;

    private String expectedYamlDefinition = String.format("---\n"
            + "apiVersion: \"extensions/v1beta1\"\n"
            + "kind: \"Deployment\"\n"
            + "metadata:\n"
            + "  name: \"%1$s\"\n"
            + "  labels:\n"
            + "    app: \"%1$s\"\n"
            + "spec:\n"
            + "  replicas: 4\n"
            + "  template:\n"
            + "    metadata:\n"
            + "      labels:\n"
            + "        app: \"%1$s\"\n"
            + "        tier: \"%1$s\"\n"
            + "    spec:\n"
            + "      containers:\n"
            + "      - name: \"%1$s\"\n"
            + "        image: \"%2$s\"", CONTAINER_DESC_NAME, CONTAINER_DESC_IMAGE);

    @Test
    public void testConvertContainerDescriptionToKubernetesDescription() throws Throwable {

        host.log(Level.INFO, "Creating container description.");
        ContainerDescription cd = createContainerDescription(CONTAINER_DESC_NAME);
        cd = doPost(cd, ContainerDescriptionService.FACTORY_LINK);

        host.log(Level.INFO, "Converting...");
        KubernetesDescription kd = doPost(cd, ContainerDescriptionToKubernetesDescriptionConverterService.SELF_LINK,
                KubernetesDescription.class);
        assertNotNull(kd);
        assertEquals(expectedYamlDefinition, kd.kubernetesEntity);
    }

    private ContainerDescription createContainerDescription(String name) {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.documentSelfLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, name);;
        containerDesc.name = name;
        containerDesc.image = CONTAINER_DESC_IMAGE;
        containerDesc._cluster = CONTAINER_DESC_CLUSTER_SIZE;

        return containerDesc;
    }
}
