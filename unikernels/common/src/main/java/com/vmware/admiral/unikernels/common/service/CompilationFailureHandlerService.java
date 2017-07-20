/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.unikernels.common.service;

import com.vmware.admiral.unikernels.common.service.UnikernelCreationTaskService.UnikernelCreationTaskServiceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;

public class CompilationFailureHandlerService extends StatelessService {

    public static final String SELF_LINK = UnikernelManagementURIParts.FAILURE_CB;

    @Override
    public void handlePost(Operation post) {
        CompilationData data = new CompilationData();
        data.setEmptyFields();

        UnikernelCreationTaskServiceState wrappedData = new UnikernelCreationTaskServiceState();
        wrappedData.taskInfo.stage = TaskStage.FAILED;
        wrappedData.data = data;

        Operation request = Operation
                .createPatch(this, UnikernelManagementURIParts.CREATION)
                .setReferer(getSelfLink())
                .setBody(wrappedData)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        post.fail(e);
                    } else {
                        post.complete();
                    }
                });

        sendRequest(request);
    }

}
