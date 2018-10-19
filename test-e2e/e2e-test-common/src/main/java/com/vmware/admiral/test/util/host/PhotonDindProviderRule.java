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

package com.vmware.admiral.test.util.host;

import java.util.Objects;

import com.vmware.admiral.test.util.SshCommandExecutor.CommandResult;

public class PhotonDindProviderRule extends AbstractContainerHostProviderRule {

    private PhotonDindProvider PROVIDER;

    public PhotonDindProviderRule(boolean useServerCertificate, boolean useClientVerification) {
        super(useServerCertificate, useClientVerification);
    }

    public CommandResult executeCommandInContainer(String command, int timeoutSeconds) {
        getHost();
        return PROVIDER.executeCommandInContainer(command, timeoutSeconds);
    }

    @Override
    protected ContainerHostProvider getProvider() {
        if (Objects.isNull(PROVIDER)) {
            PROVIDER = new PhotonDindProvider();
        }
        return PROVIDER;
    }

}
