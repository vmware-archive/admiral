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
import java.util.concurrent.TimeUnit;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

public class TestPeriodicService extends StatefulService {
    public static final String FACTORY_LINK = "/resources/test-periodic-items";

    public static class TestPeriodicState extends ServiceDocument {
        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String firstName;

        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String lastName;

        public long calls;

        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Integer intValue;

        public List<String> tenantLinks;

        public List<Integer> intValues = new ArrayList<>();
    }

    public TestPeriodicService() {
        super(TestPeriodicState.class);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
        toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(200));
    }

    @Override
    public void handlePatch(Operation patch) {
        // logInfo("handlePatch called");
        TestPeriodicState body = getBody(patch);
        TestPeriodicState state = getState(patch);
        Utils.mergeWithState(getStateDescription(), state, body);
        state.calls += body.calls;
        if (body.documentExpirationTimeMicros > 0) {
            state.documentExpirationTimeMicros = body.documentExpirationTimeMicros;
        }
        if (body.intValue != null) {
            state.intValues.add(body.intValue);
        }
        patch.setBodyNoCloning(state).complete();
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        // logInfo("handlePeriodicMaintenance called, node=%s", getHost().getId());
        TestPeriodicState body = new TestPeriodicState();
        body.calls = 1;

        Operation.createPatch(getUri())
                .setBodyNoCloning(body)
                .setCompletion((op2, ex2) -> {
                    if (ex2 != null) {
                        logSevere(ex2);
                        post.fail(ex2);
                        return;
                    }

                    post.complete();
                })
                .sendWith(this);
    }

}