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

package com.vmware.admiral.test.integration;

import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;

public class DockerProvisioningOnCoreOsRegistryV1IT extends DockerProvisioningOnCoreOsBase {

    @Test
    public void testProvisionDockerContainerOnCoreOSWithImageDownloadAPI() throws Exception {
        // not using registry, but instead upload the image to the host
        doProvisionDockerContainerOnCoreOS(true, DockerAdapterType.API);
    }

    @Test
    public void testProvisionDockerContainerOnCoreOSWithRegistryImageAPI() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
    }

    @Test
    public void testProvisionDockerContainerOnCoreOSWithInsecureRegistryImageAPI() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API,
                RegistryType.V1_HTTP_INSECURE);
    }

    @Test
    public void testProvisionDockerContainerOnCoreOSUsingLocalImageWithPriority() throws Exception {
        setupCoreOsHost(DockerAdapterType.API);

        logger.info("---------- 5. Create test docker image container description using local image"
                + " with priority. --------");
        String contDescriptionLink = getResourceDescriptionLinkUsingLocalImage(false,
                RegistryType.V1_SSL_SECURE);
        requestContainerAndDelete(contDescriptionLink);
    }

}
