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

package com.vmware.admiral.test.upgrade.version2;

import java.util.logging.Level;

import com.vmware.admiral.test.upgrade.common.UpgradeHost;
import com.vmware.admiral.test.upgrade.common.UpgradeTaskService;

/**
 * Represents an Admiral host after an upgrade.
 */
public class UpgradeNewHost extends UpgradeHost {

    public static void main(String[] args) throws Throwable {
        createManagementHost(args);
    }

    public static UpgradeNewHost createManagementHost(String[] args) throws Throwable {
        UpgradeNewHost h = new UpgradeNewHost();
        h.initializeHostAndServices(args);
        return h;
    }

    @Override
    protected void startServices() {
        this.log(Level.INFO, "New services starting ...");

        startServices(this,
                UpgradeTaskService.class);

        startServiceFactories(this,
                BrandNewService.class,
                UpgradeNewService1.class,
                UpgradeNewService2.class,
                UpgradeNewService3.class,
                UpgradeNewService4.class,
                UpgradeNewService5.class,
                UpgradeNewService6.class,
                UpgradeNewService7.class,
                UpgradeNewService8.class);

        this.log(Level.INFO, "New services started.");
    }

}
