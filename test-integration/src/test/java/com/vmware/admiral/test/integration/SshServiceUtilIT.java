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

package com.vmware.admiral.test.integration;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.SshServiceUtil;
import com.vmware.admiral.common.util.SshServiceUtil.GcData;
import com.vmware.admiral.common.util.SshServiceUtil.ScpResult;
import com.vmware.admiral.common.util.SshServiceUtil.ScpState;
import com.vmware.admiral.common.util.SshUtil;
import com.vmware.admiral.common.util.SshUtil.Result;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class SshServiceUtilIT extends BaseTestCase {

    private final String SSH_HOST = getSystemOrTestProp("ssh.host.hostname");
    private final String SSH_USER = getSystemOrTestProp("ssh.host.username");
    private final String SSH_PASS = getSystemOrTestProp("ssh.host.password");

    @Test
    public void exec() throws Throwable {
        SshServiceUtil sshServiceUtil = new SshServiceUtil(
                host);

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        sshServiceUtil.exec(SSH_HOST, getPasswordCredentials(), "echo Hello", handler,
                (String s) -> {
                    return s;
                }, SshServiceUtil.SSH_OPERATION_TIMEOUT_SHORT, TimeUnit.SECONDS);

        handler.join(5, TimeUnit.MINUTES);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertEquals("Body should contain STDOUT!", "Hello\n",
                handler.op.getBody(String.class));

        gcAndWaitCompletion(sshServiceUtil);
    }

    @Test
    public void upload() throws Throwable {
        SshServiceUtil sshServiceUtil = new SshServiceUtil(
                host);

        String target = "/tmp/test" + System.currentTimeMillis();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        sshServiceUtil.upload(SSH_HOST, getPasswordCredentials(), "Hello".getBytes(),
                target, handler);

        handler.join(5, TimeUnit.MINUTES);

        ScpResult result = handler.op.getBody(ScpResult.class);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertEquals("Body should contain STDOUT!", target,
                handler.op.getBody(ScpState.class).target);

        Result execResult = SshUtil.exec(SSH_HOST, getPasswordCredentials(), "ls " + target);
        Assert.assertEquals("Failed to find uploaded file", 0, execResult.exitCode);

        result.scheduleForGc(sshServiceUtil);

        gcAndWaitCompletion(sshServiceUtil);

        execResult = SshUtil.exec(SSH_HOST, getPasswordCredentials(), "ls " + target);
        Assert.assertEquals("Uploaded file should have been scrapped", 2, execResult.exitCode);
    }

    @Test
    public void gc() throws Throwable {
        SshServiceUtil sshServiceUtil = new SshServiceUtil(
                host);

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        sshServiceUtil.exec(SSH_HOST, getPasswordCredentials(), "echo Hello", handler,
                (String s) -> {
                    return s;
                }, SshServiceUtil.SSH_OPERATION_TIMEOUT_SHORT, TimeUnit.SECONDS);

        int retryCount = 0;
        while (sshServiceUtil.gcData.size() < 3 && retryCount < 45) {
            Thread.sleep(1000);
            retryCount++;
        }
        Assert.assertTrue("Timed out filling gcData", retryCount < 45);
        Assert.assertEquals("Unexpected number of files for gc", 3, sshServiceUtil.gcData.size());
        List<String> files = new ArrayList<>();
        for (GcData data : sshServiceUtil.gcData) {
            files.add(data.filePath);
        }
        sshServiceUtil.gc();
        Assert.assertEquals("Unexpected number of files for gc", 0, sshServiceUtil.gcData.size());
        Iterator<String> it = files.iterator();
        while (it.hasNext()) {
            String currentFile = it.next();
            waitFor("Failed to delete file on time: " + currentFile, () -> {
                Result res = SshUtil.exec(SSH_HOST, getPasswordCredentials(), "ls " + currentFile);
                return res.consume().err.contains("No such file");
            });
        }
    }

    public static void gcAndWaitCompletion(SshServiceUtil sshServiceUtil)
            throws Throwable {
        sshServiceUtil.gc();
        waitFor("Timed out filling gcData", () -> {
            return sshServiceUtil.gcData.size() == 0;
        });
    }

    public static class DefaultSshOperationResultCompletionHandler implements CompletionHandler {
        public boolean done = false;
        public Throwable failure;
        public Operation op;

        @Override
        public void handle(Operation completedOp, Throwable failure) {
            this.failure = failure;
            this.op = completedOp;
            this.done = true;
        }

        public void join(long timeout, TimeUnit unit)
                throws InterruptedException, TimeoutException {
            long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
            while (!done && endTime > System.currentTimeMillis()) {
                Thread.sleep(1000);
            }

            if (!done) {
                throw new TimeoutException("Operation failed to complete on time!");
            }
        }
    }

    private AuthCredentialsServiceState getPasswordCredentials() {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.userEmail = SSH_USER;
        creds.type = "Password";
        creds.privateKey = SSH_PASS;

        return creds;
    }
}