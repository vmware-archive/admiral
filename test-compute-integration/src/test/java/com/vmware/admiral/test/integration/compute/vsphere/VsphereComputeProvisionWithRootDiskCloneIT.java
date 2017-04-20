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

import com.vmware.admiral.compute.profile.ComputeImageDescription;
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;

public class VsphereComputeProvisionWithRootDiskCloneIT extends VsphereComputeProvisionIT {

    @Override
    protected String getResourceDescriptionLink() throws Exception {
        return getResourceDescriptionLink(true, DISK_URI_IMAGE_ID);
    }

    @Override
    public void doSetUp() throws Exception {
        // Get the vSphere compute profile and update imageMapping for diskUri
        ComputeProfile computeProfile = getDocument(VSPHERE_COMPUTE_PROFILE, ComputeProfile.class);
        ComputeImageDescription computeImageDescription = new ComputeImageDescription();
        computeImageDescription.image = VSPHERE_DISK_URI;
        computeProfile.imageMapping.put(DISK_URI_IMAGE_ID, computeImageDescription);
        patchDocument(computeProfile);
        super.doSetUp();
    }
}
