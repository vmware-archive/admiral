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

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;

public class VsphereComputeProvisionOvfIT extends VsphereComputeProvisionIT {

    public static final String OVF_URI = "test.vsphere.ovf.uri";

    @Override
    protected void extendComputeDescription(ComputeDescription cd)
            throws Exception {
        super.extendComputeDescription(cd);
        cd.customProperties.remove(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME);

        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_REF_NAME, getTestProp(OVF_URI));
    }
}
