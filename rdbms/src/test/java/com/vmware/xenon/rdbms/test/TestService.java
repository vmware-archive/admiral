/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.xenon.rdbms.test;

import java.util.ArrayList;
import java.util.List;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

public class TestService extends StatefulService {
    public static final String FACTORY_LINK = "/resources/test-items";

    public static class TestState extends ServiceDocument {
        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String firstName;

        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL, indexing =
                { PropertyIndexingOption.CASE_INSENSITIVE, PropertyIndexingOption.SORT })
        public String lastName;

        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                indexing = PropertyIndexingOption.SORT )
        public Long age;

        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Integer intValue;

        public List<String> tenantLinks;

        public List<Integer> intValues = new ArrayList<>();
    }

    public TestService() {
        super(TestState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handlePatch(Operation patch) {
        TestState body = getBody(patch);
        TestState state = getState(patch);
        Utils.mergeWithState(getStateDescription(), state, body);
        if (body.intValue != null) {
            state.intValues.add(body.intValue);
        }
        if (body.documentExpirationTimeMicros > 0) {
            state.documentExpirationTimeMicros = body.documentExpirationTimeMicros;
        }
        patch.setBodyNoCloning(state).complete();
    }

    @Override
    public void handleDelete(Operation delete) {
        TestUtils.findFactoryService(this, s -> {
            s.adjustStat("child-delete", 1);
        });
        super.handleDelete(delete);
        logInfo("Service deleted");
    }

    @Override
    public void handleStop(Operation delete) {
        TestUtils.findFactoryService(this, s -> {
            s.adjustStat("child-stop", 1);
        });
        super.handleStop(delete);
        logInfo("Service stopped");
    }

}
