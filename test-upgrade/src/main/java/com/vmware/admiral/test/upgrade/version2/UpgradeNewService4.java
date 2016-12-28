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
public class UpgradeNewService4 extends StatefulService {

    public static final String FACTORY_LINK = UpgradeUtil.UPGRADE_SERVICE4_FACTORY_LINK;

    public static class UpgradeNewService4State extends ServiceDocument {

        public static final String KIND = UpgradeUtil.UPGRADE_SERVICE4_STATE_KIND;

        public static final String FIELD3_PREFIX = "/new-field3/";
        private static final String FIELD3_PREFIX_DEPRECATED = "/field3/";

        public static final String FIELD4_PREFIX = "/new-field3/";

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field1;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field2;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field3;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_3)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field4;
    }

    public UpgradeNewService4() {
        super(UpgradeNewService4State.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        UpgradeNewService4State body = post.getBody(UpgradeNewService4State.class);
        AssertUtil.assertNotNull(body, "body");

        // upgrade the old entities accordingly...
        handleStateUpgrade(body);

        // validate based on annotations
        Utils.validateState(getStateDescription(), body);
        super.handleStart(post);
    }

    private void handleStateUpgrade(UpgradeNewService4State state) {

        boolean upgraded = false;

        if ((state.field3 != null)
                && (state.field3.startsWith(UpgradeNewService4State.FIELD3_PREFIX_DEPRECATED))) {
            state.field3 = state.field3.replaceFirst(
                    UpgradeNewService4State.FIELD3_PREFIX_DEPRECATED,
                    UpgradeNewService4State.FIELD3_PREFIX);
            upgraded = true;
        }

        if ((state.field4 != null)
                && (!state.field4.startsWith(UpgradeNewService4State.FIELD4_PREFIX))) {
            state.field4 = UpgradeNewService4State.FIELD4_PREFIX + state.field4;
            upgraded = true;
        }

        if (upgraded) {
            UpgradeUtil.forceLuceneIndexUpdate(getHost(), state);
        }
    }

}
