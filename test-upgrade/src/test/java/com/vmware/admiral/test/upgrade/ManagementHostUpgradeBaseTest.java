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

package com.vmware.admiral.test.upgrade;

import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_ASSIGNMENT;
import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_PREFIX;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.test.upgrade.common.UpgradeHost;
import com.vmware.admiral.test.upgrade.common.UpgradeUtil;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldHost;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.QueryTaskClientHelper;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.QueryTask;

public abstract class ManagementHostUpgradeBaseTest {

    private static final long DEFAULT_OPERATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);

    @Rule
    public TemporaryFolder test = new TemporaryFolder();

    protected static ServiceClient serviceClient;

    protected UpgradeHost upgradeHost;

    protected UpgradeHost startHost(Class<? extends UpgradeHost> type, int port, String sandboxPath)
            throws Throwable {

        String[] hostArgs = new String[] {
                ARGUMENT_PREFIX
                        + "bindAddress"
                        + ARGUMENT_ASSIGNMENT
                        + "0.0.0.0",
                ARGUMENT_PREFIX
                        + "sandbox"
                        + ARGUMENT_ASSIGNMENT
                        + sandboxPath,
                ARGUMENT_PREFIX
                        + "port"
                        + ARGUMENT_ASSIGNMENT
                        + port
        };

        UpgradeHost host;
        if (type.equals(UpgradeOldHost.class)) {
            host = UpgradeOldHost.createManagementHost(hostArgs);
        } else {
            host = UpgradeNewHost.createManagementHost(hostArgs);
        }

        host.log(Level.INFO, "Host ('%s', '%s', '%s') '%s' started.", type.getSimpleName(),
                host.getPort(), host.getStorageSandbox().toString(), host.getUri().toString());

        return host;
    }

    protected void stopHost(UpgradeHost host) {

        if (host == null) {
            return;
        }

        String hostname = host.getUri().toString();
        host.log(Level.INFO, "Stopping host '%s'...", hostname);

        try {
            host.stop();
            host.log(Level.INFO, "Host '%s' stopped.", hostname);
        } catch (Exception e) {
            throw new RuntimeException("Exception stopping host!", e);
        }

        host = null;
    }

    protected Operation sendRequest(ServiceClient serviceClient, Operation op)
            throws InterruptedException, ExecutionException, TimeoutException {
        return sendRequest(serviceClient, op, DEFAULT_OPERATION_TIMEOUT_MILLIS);
    }

    private Operation sendRequest(ServiceClient serviceClient, Operation op, long timeoutMilis)
            throws InterruptedException, ExecutionException, TimeoutException {

        CompletableFuture<Operation> c = new CompletableFuture<>();
        serviceClient.send(op
                .setReferer(URI.create("/"))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        c.completeExceptionally(ex);

                    } else {
                        c.complete(o);
                    }
                }));

        return c.get(timeoutMilis, TimeUnit.MILLISECONDS);
    }

    protected void waitForServiceAvailability(ServiceHost h, String serviceLink) throws Throwable {
        TestContext ctx = BaseTestCase.testCreate(1);
        h.registerForServiceAvailability(ctx.getCompletion(), serviceLink);
        ctx.await();
    }

    @SuppressWarnings("unchecked")
    protected <T extends ServiceDocument> T createUpgradeServiceInstance(T state) throws Exception {
        URI uri = UriUtils.buildUri(upgradeHost, UpgradeUtil.getFactoryLinkByDocumentKind(state));
        Operation op = sendRequest(serviceClient, Operation.createPost(uri).setBody(state));
        String link = op.getBody(state.getClass()).documentSelfLink;
        return (T) getUpgradeServiceInstance(link, state.getClass());
    }

    @SuppressWarnings("unchecked")
    protected <T extends ServiceDocument> T updateUpgradeServiceInstance(T state) throws Exception {
        URI uri = UriUtils.buildUri(upgradeHost, state.documentSelfLink);
        Operation op = sendRequest(serviceClient, Operation.createPut(uri).setBody(state));
        String link = op.getBody(state.getClass()).documentSelfLink;
        return (T) getUpgradeServiceInstance(link, state.getClass());
    }

    protected <T extends ServiceDocument> T getUpgradeServiceInstance(String link, Class<T> type)
            throws Exception {
        URI uri = UriUtils.buildUri(upgradeHost, link);
        Operation op = sendRequest(serviceClient, Operation.createGet(uri));
        return op.getBody(type);
    }

    public <T extends ServiceDocument> Collection<T> queryUpgradeServiceInstances(Class<T> type,
            String key, String value) throws Throwable {

        final Collection<T> instances = new ArrayList<>();

        QueryTask q = QueryUtil.buildPropertyQuery(type, key, value);
        q = QueryUtil.addExpandOption(q);

        TestContext ctx = BaseTestCase.testCreate(1);
        QueryTaskClientHelper.create(type).setQueryTask(q).setResultHandler((r, e) -> {
            if (e != null) {
                ctx.failIteration(e);
                return;
            } else if (r.hasResult()) {
                instances.add(r.getResult());
            } else {
                ctx.completeIteration();
            }
        }).sendWith(upgradeHost);
        ctx.await();

        return instances;
    }

}
