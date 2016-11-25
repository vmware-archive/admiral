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

package com.vmware.admiral.service.common;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.vmware.admiral.common.util.SshServiceUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class MockSshServiceUtil extends SshServiceUtil {

    public MockSshServiceUtil(ServiceHost host) {
        super(host);
    }

    @Override
    public void exec(String hostname, AuthCredentialsServiceState credentials,
            String command,
            final CompletionHandler completionHandler, Function<String, ?> mapper, int timeout,
            TimeUnit unit) {
        completionHandler.handle(Operation.createPatch(null)
                .setBody("DUMMY"), null);
    }

    @Override
    public void upload(String hostname, AuthCredentialsServiceState credentials, byte[] data,
            String remoteFile, CompletionHandler completionHandler) {
        completionHandler.handle(Operation.createPatch(null)
                .setBody(new ScpResult(hostname, credentials, remoteFile)), null);
    }
}
