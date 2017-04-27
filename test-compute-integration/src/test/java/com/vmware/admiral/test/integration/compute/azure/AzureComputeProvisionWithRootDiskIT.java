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

public class AzureComputeProvisionWithRootDiskIT extends AzureComputeProvisionIT {

    private static final long CUSTOM_DISK_SIZE = 32 * 1024;

    @Override protected String getResourceDescriptionLink() throws Exception {
        return getResourceDescriptionLink(true, null);
    }

    @Override
    protected long getRootDiskSize() {
        return CUSTOM_DISK_SIZE;
    }
}
