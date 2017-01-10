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

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.serialization.ThreadLocalVersionHolder;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.test.upgrade.common.UpgradeUtil;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService4;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents the base service {@link UpgradeOldService4} with changed field name.
 */
public class UpgradeNewService7 extends StatefulService {

    public static final String FACTORY_LINK = UpgradeUtil.UPGRADE_SERVICE7_FACTORY_LINK;

    public static class UpgradeNewService7State extends ServiceDocument {

        public static final String KIND = UpgradeUtil.UPGRADE_SERVICE7_STATE_KIND;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field1;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field2;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String upgradedField3;
    }

    public UpgradeNewService7() {
        super(UpgradeNewService7State.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    static {
        UpgradeNewService7StateConverter.INSTANCE.init();
    }

    @Override
    public void handleRequest(Operation request) {
        ThreadLocalVersionHolder.setVersion(request);
        try {
            super.handleRequest(request);
        } finally {
            ThreadLocalVersionHolder.clearVersion();
        }
    }

    @Override
    public void handleStart(Operation post) {
        UpgradeNewService7State body = post.getBody(UpgradeNewService7State.class);
        AssertUtil.assertNotNull(body, "body");
        // validate based on annotations
        Utils.validateState(getStateDescription(), body);
        super.handleStart(post);
    }

}
