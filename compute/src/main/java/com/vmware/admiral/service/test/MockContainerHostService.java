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

package com.vmware.admiral.service.test;

import com.vmware.admiral.compute.ConfigureHostOverSshTaskService.ConfigureHostOverSshTaskServiceState;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.xenon.common.Operation;

public class MockContainerHostService extends ContainerHostService {

    @Override
    protected void validateConfigureOverSsh(ConfigureHostOverSshTaskServiceState state,
            Operation op) {
        MockConfigureHostOverSshTaskServiceWithoutValidate.validate(getHost(), state, (t) -> {
            // I'm pretty sure there's no way 't' to ever be something different then null with the
            // mock adapter, but just in case for the future
            if (t != null) {
                op.fail(t);
                return;
            }

            completeOperationSuccess(op);
        });
    }
}
