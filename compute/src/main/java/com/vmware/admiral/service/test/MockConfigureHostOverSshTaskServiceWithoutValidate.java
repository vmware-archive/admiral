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

import java.util.function.Consumer;

import com.vmware.xenon.common.ServiceHost;

public class MockConfigureHostOverSshTaskServiceWithoutValidate extends MockConfigureHostOverSshTaskService {
    @Override
    protected void validate(ConfigureHostOverSshTaskServiceState state,
            Consumer<Throwable> consumer) {
        consumer.accept(null);
    }

    public static void validate(ServiceHost host, ConfigureHostOverSshTaskServiceState state,
            Consumer<Throwable> consumer) {
        consumer.accept(null);
    }
}
