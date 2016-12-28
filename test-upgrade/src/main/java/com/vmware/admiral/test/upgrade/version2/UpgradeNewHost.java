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
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService1.UpgradeNewService1State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService2.UpgradeNewService2State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService3.UpgradeNewService3State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService4.UpgradeNewService4State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService5.UpgradeNewService5State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService6.UpgradeNewService6State;
import com.vmware.xenon.common.Utils;

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

        Utils.registerKind(UpgradeNewService1State.class, UpgradeNewService1State.KIND);
        Utils.registerKind(UpgradeNewService2State.class, UpgradeNewService2State.KIND);
        Utils.registerKind(UpgradeNewService3State.class, UpgradeNewService3State.KIND);
        Utils.registerKind(UpgradeNewService4State.class, UpgradeNewService4State.KIND);
        Utils.registerKind(UpgradeNewService5State.class, UpgradeNewService5State.KIND);
        Utils.registerKind(UpgradeNewService6State.class, UpgradeNewService6State.KIND);

        this.log(Level.INFO, "New services starting ...");

        startServiceFactories(this,
                BrandNewService.class,
                UpgradeNewService1.class,
                UpgradeNewService2.class,
                UpgradeNewService3.class,
                UpgradeNewService4.class,
                UpgradeNewService5.class,
                UpgradeNewService6.class);

        this.log(Level.INFO, "New services started.");
    }

}
