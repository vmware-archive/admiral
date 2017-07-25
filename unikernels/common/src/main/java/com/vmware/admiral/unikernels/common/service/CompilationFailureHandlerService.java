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

import java.net.URI;

import com.vmware.admiral.unikernels.common.service.UnikernelCreationTaskService.UnikernelCreationTaskServiceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;


public class CompilationFailureHandlerService extends StatelessService {

    public static final String SELF_LINK = UnikernelManagementURIParts.FAILURE_CB;

    @Override
    public void handlePost(Operation post) {
        String[] receivedData = post.getBody(String[].class);
        CompilationData data = new CompilationData();
        data.setEmptyFields();

        UnikernelCreationTaskServiceState wrappedData = new UnikernelCreationTaskServiceState();
        wrappedData.taskInfo = new TaskState();
        wrappedData.taskInfo.stage = TaskStage.FAILED;
        wrappedData.data = data;

        URI creationTaskServiceURI = UriUtils.buildUri(receivedData[1]);
        Operation request = Operation
                .createPatch(creationTaskServiceURI)
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
