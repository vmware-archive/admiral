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

package com.vmware.admiral.adapter.common.service.mock;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;

public class MockTaskService extends StatefulService {

    public static class MockTaskState extends ServiceDocument {
        public TaskState taskInfo = new TaskState();
    }

    public static interface StateChangeCallback {
        public void stateChanged(Operation op);
    }

    public MockTaskService() {
        super(MockTaskState.class);
    }

    @Override
    public void handlePatch(Operation patch) {
        MockTaskState body = patch.getBody(MockTaskState.class);
        setState(patch, body);
        patch.complete();
    }
}
