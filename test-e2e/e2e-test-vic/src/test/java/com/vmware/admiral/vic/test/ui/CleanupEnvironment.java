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

package com.vmware.admiral.vic.test.ui;

import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Test;

import com.vmware.admiral.test.util.SshCommandExecutor;
import com.vmware.admiral.test.util.SshCommandExecutor.CommandResult;
import com.vmware.admiral.vic.test.VicTestProperties;

public class CleanupEnvironment {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    @Test
    public void killUtilityVm() {
        UtilityVmInfo.readInfo();
        String vmName = UtilityVmInfo.getVmName();
        LOG.info("Killing utility vm with name: " + vmName);
        SshCommandExecutor executor = SshCommandExecutor.createWithPasswordAuthentication(
                "nimbus-gateway.eng.vmware.com", VicTestProperties.nimbusUsername(),
                VicTestProperties.nimbusPassword());
        CommandResult result = null;
        try {
            result = executor.execute("nimbus-ctl kill " + UtilityVmInfo.getVmName(),
                    120);
        } catch (Throwable e) {
            LOG.warning(String.format("Could not kill VM with name '%s', error:%n%s", vmName,
                    ExceptionUtils.getStackTrace(e)));
            return;
        }
        if (result.getExitStatus() == 0) {
            LOG.info("Successfully killed utility VM with name: " + vmName);
        } else {
            LOG.warning(String.format("Could not kill utility VM with name: %s, error: %s", vmName,
                    result.getErrorOutput()));
        }

    }

}
