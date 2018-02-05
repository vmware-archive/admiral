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

package com.vmware.admiral.request;

import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.TaskServiceDocument;

public class TestTaskService extends AbstractTaskStatefulService<TestTaskService.TestTaskServiceDocument, DefaultSubStage> {

    public static final String FACTORY_LINK = "/requests/test";

    public static class TestTaskServiceDocument extends TaskServiceDocument<DefaultSubStage> {

    }

    public TestTaskService() {
        super(TestTaskServiceDocument.class, DefaultSubStage.class, "Test Task");
    }

    @Override
    protected void handleStartedStagePatch(TestTaskServiceDocument state) {
    }
}
