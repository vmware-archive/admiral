/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration.compute.vsphere;

import java.util.Set;

import com.vmware.photon.controller.model.resources.ComputeService.PowerState;

public class VsphereComputeDay2OperationsIT extends VsphereComputeProvisionIT {

    @Override
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {
        super.doWithResources(resourceLinks);

        doDay2Operation(resourceLinks, DAY_2_OPERATION_REBOOT, null);
        validateHostState(resourceLinks, PowerState.ON);
    }
}
