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

package com.vmware.admiral.test.upgrade.version1;

import java.util.logging.Level;

import com.vmware.admiral.test.upgrade.common.UpgradeHost;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService1.UpgradeOldService1State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService2.UpgradeOldService2State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService3.UpgradeOldService3State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService4.UpgradeOldService4State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService5.UpgradeOldService5State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService6.UpgradeOldService6State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService7.UpgradeOldService7State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService8.UpgradeOldService8State;
import com.vmware.xenon.common.Utils;

/**
 * Represents an Admiral host before an upgrade.
 */
public class UpgradeOldHost extends UpgradeHost {

    public static void main(String[] args) throws Throwable {
        createManagementHost(args);
    }

    public static UpgradeOldHost createManagementHost(String[] args) throws Throwable {
        UpgradeOldHost h = new UpgradeOldHost();
        h.initializeHostAndServices(args);
        return h;
    }

    @Override
    protected void startServices() {

        Utils.registerKind(UpgradeOldService1State.class, UpgradeOldService1State.KIND);
        Utils.registerKind(UpgradeOldService2State.class, UpgradeOldService2State.KIND);
        Utils.registerKind(UpgradeOldService3State.class, UpgradeOldService3State.KIND);
        Utils.registerKind(UpgradeOldService4State.class, UpgradeOldService4State.KIND);
        Utils.registerKind(UpgradeOldService5State.class, UpgradeOldService5State.KIND);
        Utils.registerKind(UpgradeOldService6State.class, UpgradeOldService6State.KIND);
        Utils.registerKind(UpgradeOldService7State.class, UpgradeOldService7State.KIND);
        Utils.registerKind(UpgradeOldService8State.class, UpgradeOldService8State.KIND);

        this.log(Level.INFO, "Old services starting ...");

        startServiceFactories(this,
                UpgradeOldService1.class,
                UpgradeOldService2.class,
                UpgradeOldService3.class,
                UpgradeOldService4.class,
                UpgradeOldService5.class,
                UpgradeOldService6.class,
                UpgradeOldService7.class,
                UpgradeOldService8.class);

        this.log(Level.INFO, "Old services started.");
    }

}
