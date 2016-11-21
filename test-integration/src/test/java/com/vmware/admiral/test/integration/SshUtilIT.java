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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.schmizz.sshj.SSHClient;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.common.util.SshUtil;
import com.vmware.admiral.common.util.SshUtil.AsyncResult;
import com.vmware.admiral.common.util.SshUtil.ConsumedResult;
import com.vmware.admiral.common.util.SshUtil.Result;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class SshUtilIT extends BaseTestCase {

    public static final String SSH_HOST = getSystemOrTestProp("ssh.host.hostname");
    public static final String SSH_USER = getSystemOrTestProp("ssh.host.username");
    public static final String SSH_PASS = getSystemOrTestProp("ssh.host.password");
    public static final String SSH_PKEY_FILE = getSystemOrTestProp("ssh.host.pkey");

    @Test
    public void testPasswordAuth() throws IOException {
        sayHello(SSH_HOST, getPasswordCredentials());
    }

    @Test
    public void testPublicAuth() throws IOException {
        sayHello(SSH_HOST, getPrivateKeyCredentials());
    }

    @Test
    public void testErrorStream() throws IOException {
        Result result = SshUtil.exec(SSH_HOST, getPrivateKeyCredentials(), "x");
        assertResult(result.consume(), 127, "", "bash: x: command not found\n", false);
    }

    @Test
    public void testError() throws IOException {
        Result result = SshUtil.exec("does.not.exist.vmware.com", getPrivateKeyCredentials(),
                "echo hello");
        assertResult(result.consume(), -1, null, null, true);
    }

    @Test
    @Ignore("Takes too much time to execute with every build")
    public void testStability() throws IOException, InterruptedException {
        AuthCredentialsServiceState creds = getPrivateKeyCredentials();
        for (int i = 0; i < 100; i++) {
            sayHello(SSH_HOST, creds);
            /*
             * Depending on the host, if it's flooded with requests it does reject to execute after
             * X amounts of requests in Y amount of time. The host that is being used here does
             * handle the requests fairly slow anyway, so possibly the test will pass even without
             * the sleep.
             */
            Thread.sleep(100);
            System.out.println(i);
        }
    }

    @Test
    public void testAsync() throws InterruptedException, IOException, TimeoutException {
        try (AsyncResult asyncExec = SshUtil.asyncExec(SSH_HOST, getPrivateKeyCredentials(),
                "sleep 5")) {
            new TimeoutOperation() {
                @Override
                public boolean isDone() {
                    return asyncExec.isDone();
                }
            }.poll(15000, 1000);
            Result result = asyncExec.toResult();
            assertResult(result.consume(), 0, "", "", false);
        }
    }

    @Test
    public void testAsyncError() throws InterruptedException, IOException, TimeoutException {
        try (AsyncResult asyncExec = SshUtil.asyncExec("does.not.exist.vmware.com",
                getPasswordCredentials(),
                "sleep 5")) {
            new TimeoutOperation() {
                @Override
                public boolean isDone() {
                    return asyncExec.isDone();
                }
            }.poll(15000, 1000);
            Result result = asyncExec.toResult();
            assertResult(result.consume(), -1, null, null, true);
        }
    }

    @Test
    public void testJoinOnAsyncError() throws InterruptedException, IOException {
        try (AsyncResult asyncExec = SshUtil.asyncExec("does.not.exist.vmware.com",
                getPasswordCredentials(),
                "sleep 5")) {
            Result result = asyncExec.join();
            assertResult(result.consume(), -1, null, null, true);
        }
    }

    @Test
    public void testScp() throws IOException {
        String testResource = "pkey";
        AuthCredentialsServiceState creds = getPasswordCredentials();
        String remotePath = "/tmp/test" + System.currentTimeMillis();
        Assert.assertNull(SshUtil.upload(SSH_HOST, creds,
                Thread.currentThread().getContextClassLoader().getResourceAsStream(testResource),
                remotePath));
        File target = File.createTempFile("test", "txt");
        Assert.assertNull(SshUtil.download(SSH_HOST, creds, target.getAbsolutePath(), remotePath));
        String expected = FileUtil.getResourceAsString("/" + testResource, true);
        String actual = FileUtil.getResourceAsString(target.getAbsolutePath(), false);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testScpError() throws IOException {
        String testResource = "pkey";
        AuthCredentialsServiceState creds = getPasswordCredentials();
        String remotePath = "/";
        Assert.assertTrue(SshUtil.upload(SSH_HOST, creds,
                Thread.currentThread().getContextClassLoader().getResourceAsStream(testResource),
                remotePath).getMessage().contains("Permission denied"));

        Assert.assertTrue(SshUtil.download(SSH_HOST, creds,
                File.createTempFile("test", "txt").getAbsolutePath(), "~/not-existing").getMessage()
                .contains("No such file"));
    }

    @Test
    public void testAsyncScp()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        String testResource = "pkey";
        AuthCredentialsServiceState creds = getPasswordCredentials();
        String remotePath = "/tmp/test" + System.currentTimeMillis();
        Future<Throwable> uploadFuture = SshUtil.asyncUpload(SSH_HOST, creds,
                Thread.currentThread().getContextClassLoader().getResourceAsStream(testResource),
                remotePath);
        Assert.assertNull(uploadFuture.get(60, TimeUnit.SECONDS));

        File target = File.createTempFile("test", "txt");
        Future<Throwable> downloadFuture = SshUtil.asyncDownload(SSH_HOST, creds,
                target.getAbsolutePath(), remotePath);
        Assert.assertNull(downloadFuture.get(60, TimeUnit.SECONDS));

        String expected = FileUtil.getResourceAsString("/" + testResource, true);
        String actual = FileUtil.getResourceAsString(target.getAbsolutePath(), false);
        Assert.assertEquals(expected, actual);
    }

    @Test
    @Ignore("Takes too much time to execute with every build")
    public void testAsyncScpStability()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        for (int i = 0; i < 200; i++) {
            System.out.println(i);
            testAsyncScp();
        }
    }

    @Ignore("Failing in local unit test")
    @Test
    public void testAsyncScpError()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        String testResource = "pkey";
        AuthCredentialsServiceState creds = getPasswordCredentials();
        String remotePath = "/";
        Future<Throwable> uploadFuture = SshUtil.asyncUpload(SSH_HOST, creds,
                Thread.currentThread().getContextClassLoader().getResourceAsStream(testResource),
                remotePath);
        new TimeoutOperation() {
            @Override
            public boolean isDone() {
                return uploadFuture.isDone();
            }
        }.poll(5000, 1000);
        Assert.assertTrue(uploadFuture.get().getMessage().contains("Permission denied"));

        Future<Throwable> downloadFuture = SshUtil.asyncDownload(SSH_HOST, creds,
                File.createTempFile("test", ".txt").getAbsolutePath(), "~/not-existing");
        new TimeoutOperation() {
            @Override
            public boolean isDone() {
                return downloadFuture.isDone();
            }
        }.poll(5000, 1000);
        Assert.assertTrue(SshUtil.download(SSH_HOST, creds,
                File.createTempFile("test", "txt").getAbsolutePath(), "~/not-existing").getMessage()
                .contains("No such file"));
    }

    @Test
    @Ignore("Takes too much time to execute with every build and require high MaxSession sshd property")
    public void testMaxConcurentExecutions()
            throws InterruptedException, IOException, TimeoutException {
        List<AsyncResult> results = new ArrayList<>();
        SSHClient client = SshUtil.getDefaultSshClient(SSH_HOST, getPrivateKeyCredentials());
        for (int i = 0; i < 250; i++) {
            results.add(SshUtil.asyncExec(client,
                    "sleep " + (150 - (i / 4))));
            if (i % 10 == 0) {
                System.out.println(i);
            }
        }
        System.out.println("Done");
        int i = 0;
        for (AsyncResult r : results) {
            System.out.println(i);
            i++;
            new TimeoutOperation() {
                @Override
                public boolean isDone() {
                    return r.isDone();
                }
            }.poll(150000, 1000);
            Result result = r.toResult();
            assertResult(result.consume(), 0, "", "", false);
        }
    }

    private abstract class TimeoutOperation {
        public abstract boolean isDone();

        public void poll(long timeout, long pollInterval)
                throws InterruptedException, TimeoutException {
            long timeoutDate = System.currentTimeMillis() + timeout;
            while (!isDone() && timeoutDate > System.currentTimeMillis()) {
                Thread.sleep(pollInterval);
            }

            if (timeoutDate < System.currentTimeMillis()) {
                throw new TimeoutException("Operation timed out.");
            }
        }
    }

    public static AuthCredentialsServiceState getPasswordCredentials() {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.userEmail = SSH_USER;
        creds.type = "Password";
        creds.privateKey = SSH_PASS;

        return creds;
    }

    public static AuthCredentialsServiceState getPrivateKeyCredentials() {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.userEmail = SSH_USER;
        creds.type = "PublicKey";
        creds.privateKey = FileUtil.getResourceAsString(SSH_PKEY_FILE, true);

        return creds;
    }

    private void sayHello(String hostname, AuthCredentialsServiceState creds) throws IOException {
        Result result = SshUtil.exec(hostname, creds, "echo hello");
        assertResult(result.consume(), 0, "hello\n", "", false);
    }

    private void assertResult(ConsumedResult result, int exitCode, String out, String err,
            boolean isErrorExpected) throws IOException {
        if (isErrorExpected) {
            Assert.assertNotNull("Expected error missing", result.error);
        } else {
            if (result.error != null) {
                result.error.printStackTrace();
            }
            Assert.assertNull("Unexpected error has occured", result.error);
        }

        Assert.assertEquals("Unexpected exit code", exitCode, result.exitCode);
        Assert.assertEquals("Unexpected out", out, result.out);
        Assert.assertEquals("Unexpected err", err, result.err);
    }
}
