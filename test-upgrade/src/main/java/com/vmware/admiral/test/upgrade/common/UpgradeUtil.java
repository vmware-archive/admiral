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

package com.vmware.admiral.test.upgrade.common;

import com.vmware.admiral.test.upgrade.version1.UpgradeOldService1.UpgradeOldService1State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService2.UpgradeOldService2State;
import com.vmware.xenon.common.Utils;

public class UpgradeUtil {

    public static final String UPGRADE_SERVICE1_FACTORY_LINK = "/upgrade/service1-services";
    public static final String UPGRADE_SERVICE2_FACTORY_LINK = "/upgrade/service2-services";

    public static final String UPGRADE_SERVICE1_STATE_KIND = Utils
            .buildKind(UpgradeOldService1State.class);
    public static final String UPGRADE_SERVICE2_STATE_KIND = Utils
            .buildKind(UpgradeOldService2State.class);

}
