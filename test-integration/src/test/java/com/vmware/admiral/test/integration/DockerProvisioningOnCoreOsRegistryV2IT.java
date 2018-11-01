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

public class DockerProvisioningOnCoreOsRegistryV2IT extends DockerProvisioningOnCoreOsBase {

    @Test
    public void testProvisionDockerContainerOnCoreOSWithV2RegistryImageAPI() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API,
                RegistryType.V2_SSL_SECURE);
    }

    @Test
    public void testProvisionDockerContainerOnCoreOSWithSecureRegistryImageAPI() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API,
                RegistryType.V2_BASIC_AUTH);
    }

}
