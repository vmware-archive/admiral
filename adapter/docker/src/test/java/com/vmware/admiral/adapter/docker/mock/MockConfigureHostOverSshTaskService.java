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

package com.vmware.admiral.adapter.docker.mock;

import com.vmware.admiral.adapter.docker.service.ConfigureHostOverSshTaskService;

public class MockConfigureHostOverSshTaskService extends ConfigureHostOverSshTaskService {

    @Override
    public String getInstallCommand(SetupOverSshServiceState state) {
        return String.format(
                "tar -zxvf %s && cd installer",
                INSTALLER_RESOURCE);
    }

}
