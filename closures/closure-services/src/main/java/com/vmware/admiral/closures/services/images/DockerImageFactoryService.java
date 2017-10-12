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

package com.vmware.admiral.closures.services.images;

import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.AbstractSecuredFactoryService;
import com.vmware.xenon.common.Service;

public class DockerImageFactoryService extends AbstractSecuredFactoryService {

    public static final String FACTORY_LINK = ManagementUriParts.CLOSURES_IMAGES;

    private DriverRegistry driverRegistry;

    public DockerImageFactoryService() {
        super(DockerImage.class);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    public DockerImageFactoryService(DriverRegistry driverRegistry) {
        this();
        this.driverRegistry = driverRegistry;
    }

    @Override
    public Service createServiceInstance() {
        return new DockerImageService(driverRegistry);
    }
}
