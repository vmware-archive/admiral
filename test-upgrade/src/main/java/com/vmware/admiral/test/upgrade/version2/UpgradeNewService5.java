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

import java.util.Arrays;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.test.upgrade.common.UpgradeUtil;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService4;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents the base service {@link UpgradeOldService4} with new field values.
 */
public class UpgradeNewService5 extends StatefulService {

    public static final String FACTORY_LINK = UpgradeUtil.UPGRADE_SERVICE5_FACTORY_LINK;

    public static class UpgradeNewService5State extends ServiceDocument {

        public static final String KIND = UpgradeUtil.UPGRADE_SERVICE5_STATE_KIND;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field1;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field2;

        @Deprecated
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field3;

        @Deprecated
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field4;

        @Deprecated
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field5;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field345;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field6;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field7;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field8;

        @Deprecated
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field678;
    }

    public UpgradeNewService5() {
        super(UpgradeNewService5State.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        UpgradeNewService5State body = post.getBody(UpgradeNewService5State.class);
        AssertUtil.assertNotNull(body, "body");

        // upgrade the old entities accordingly...
        handleStateUpgrade(body);

        // validate based on annotations
        Utils.validateState(getStateDescription(), body);
        super.handleCreate(post);
    }

    private void handleStateUpgrade(UpgradeNewService5State state) {

        if (state.field345 == null) {
            state.field345 = String.join("#",
                    Arrays.asList(state.field3, state.field4, state.field5));
            state.field3 = null;
            state.field4 = null;
            state.field5 = null;
        }

        if (state.field678 != null) {
            String[] values = state.field678.split("/");
            state.field6 = values[0];
            state.field7 = values[1];
            state.field8 = values[2];
            state.field678 = null;
        }
    }

}
