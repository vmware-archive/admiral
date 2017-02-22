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

package com.vmware.admiral.service.test;

import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.xenon.common.Service;

public class MockClosureFactoryService extends ClosureFactoryService {

    public MockClosureFactoryService() {
        super();
    }

    public MockClosureFactoryService(DriverRegistry driverRegistry) {
        super(driverRegistry);
    }

    public MockClosureFactoryService(DriverRegistry driverRegistry, long maintenanceTimeout) {
        super(driverRegistry, maintenanceTimeout);
    }

    @Override
    public Service createServiceInstance() {
        return new MockClosureService(driverRegistry, maintenanceTimeout);
    }

}
