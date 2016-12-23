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

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.test.upgrade.common.UpgradeUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a base service that will be upgraded.
 */
public class UpgradeOldService1 extends StatefulService {

    public static final String FACTORY_LINK = UpgradeUtil.UPGRADE_SERVICE1_FACTORY_LINK;

    public static class UpgradeOldService1State extends ServiceDocument {

        public static final String KIND = UpgradeUtil.UPGRADE_SERVICE1_STATE_KIND;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field1;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field2;
    }

    public UpgradeOldService1() {
        super(UpgradeOldService1State.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        UpgradeOldService1State body = post.getBody(UpgradeOldService1State.class);
        AssertUtil.assertNotNull(body, "body");
        // validate based on annotations
        Utils.validateState(getStateDescription(), body);
        super.handleCreate(post);
    }

}
