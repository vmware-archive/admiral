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

package com.vmware.admiral.closures.services.closure;

import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

public class ClosureFactoryService extends FactoryService {

    public static final String FACTORY_LINK = ManagementUriParts.CLOSURES;

    private static final long DEFAULT_MAINTENANCE_TIMEOUT = 5 * 1000 * 1000; // 5 seconds

    private long maintenanceTimeout = DEFAULT_MAINTENANCE_TIMEOUT;

    private DriverRegistry driverRegistry;

    public ClosureFactoryService() {
        super(Closure.class);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    public ClosureFactoryService(DriverRegistry driverRegistry) {
        this();
        this.driverRegistry = driverRegistry;
    }

    public ClosureFactoryService(long maintenanceTimeout) {
        this();
        this.maintenanceTimeout = maintenanceTimeout;
    }

    public ClosureFactoryService(DriverRegistry driverRegistry, long maintenanceTimeout) {
        this(maintenanceTimeout);
        this.driverRegistry = driverRegistry;
    }

    @Override
    public Service createServiceInstance() {
        return new ClosureService(driverRegistry, maintenanceTimeout);
    }

    public DriverRegistry getDriverRegistry() {
        return driverRegistry;
    }

}