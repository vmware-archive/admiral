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

package com.vmware.admiral.unikernels.osv.compilation.service;

import com.vmware.admiral.unikernels.osv.compilation.service.CompilationTaskService.CompilationTaskServiceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class CompilationBrockerService extends StatelessService {

    public static final String SELF_LINK = UnikernelManagementURIParts.ROUTER;

    @Override
    public void handlePost(Operation post) {
        CompilationData data = post.getBody(CompilationData.class);
        data.creationTaskServiceURI = post.getReferer().toString();
        CompilationTaskServiceState wrappedData = new CompilationTaskServiceState();

        wrappedData.data = data;

        Operation request = Operation.createPost(this, UnikernelManagementURIParts.COMPILE_TASK)
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
