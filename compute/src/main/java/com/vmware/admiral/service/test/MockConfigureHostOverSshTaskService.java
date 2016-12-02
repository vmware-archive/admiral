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

import com.vmware.admiral.common.util.SshServiceUtil;
import com.vmware.admiral.compute.ConfigureHostOverSshTaskService;
import com.vmware.admiral.service.common.MockSshServiceUtil;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class MockConfigureHostOverSshTaskService extends ConfigureHostOverSshTaskService {

    private static MockSshServiceUtil sshServiceUtil;

    @Override
    public String getInstallCommand(ConfigureHostOverSshTaskServiceState state,
            AuthCredentialsServiceState credentials) {
        return "cd installer && ls " + INSTALLER_RESOURCE;
    }

    @Override
    protected SshServiceUtil getSshServiceUtil() {
        synchronized (MockConfigureHostOverSshTaskService.class) {
            if (sshServiceUtil == null) {
                sshServiceUtil = new MockSshServiceUtil(getHost());
            }

            return sshServiceUtil;
        }
    }
}
