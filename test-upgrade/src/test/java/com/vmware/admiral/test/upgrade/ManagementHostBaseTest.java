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

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.test.upgrade.common.UpgradeHost;
import com.vmware.admiral.test.upgrade.common.UpgradeTaskService;
import com.vmware.admiral.test.upgrade.common.UpgradeTaskService.UpgradeServiceRequest;
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
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public abstract class ManagementHostBaseTest {

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

    protected void upgradeService(ServiceHost h, String factoryLink,
            Class<? extends ServiceDocument> clazz) throws Throwable {
        waitForServiceAvailability(h, UpgradeTaskService.SELF_LINK);
        waitForServiceAvailability(h, factoryLink);

        UpgradeServiceRequest request = new UpgradeServiceRequest();
        request.version = ReleaseConstants.API_VERSION_0_9_1;
        request.clazz = clazz.getName();

        TestContext ctx = BaseTestCase.testCreate(1);
        h.sendRequest(Operation.createPost(UriUtils.buildUri(h, UpgradeTaskService.SELF_LINK))
                .setBody(request)
                .setReferer("/")
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    ctx.complete();
                }));
        ctx.await();
    }

    protected void waitForServiceAvailability(ServiceHost h, String serviceLink) throws Throwable {
        TestContext ctx = BaseTestCase.testCreate(1);
        h.registerForServiceAvailability(ctx.getCompletion(), serviceLink);
        ctx.await();
    }

    protected <T extends ServiceDocument> T createUpgradeServiceInstance(T state) throws Exception {
        return createUpgradeServiceInstance(state, null);
    }

    @SuppressWarnings("unchecked")
    protected <T extends ServiceDocument> T createUpgradeServiceInstance(T state, String apiVersion)
            throws Exception {
        URI uri = UriUtils.buildUri(upgradeHost, UpgradeUtil.getFactoryLinkByDocumentKind(state));
        Operation op = Operation.createPost(uri).setBody(state);
        UpgradeUtil.setOperationRequestApiVersion(op, apiVersion);
        op = sendRequest(serviceClient, op);
        String link = op.getBody(state.getClass()).documentSelfLink;
        return (T) getUpgradeServiceInstance(link, state.getClass(), apiVersion);
    }

    protected <T extends ServiceDocument> T updateUpgradeServiceInstance(T state) throws Exception {
        return updateUpgradeServiceInstance(state, null);
    }

    @SuppressWarnings("unchecked")
    protected <T extends ServiceDocument> T updateUpgradeServiceInstance(T state, String apiVersion)
            throws Exception {
        URI uri = UriUtils.buildUri(upgradeHost, state.documentSelfLink);
        Operation op = Operation.createPut(uri).setBody(state);
        UpgradeUtil.setOperationRequestApiVersion(op, apiVersion);
        op = sendRequest(serviceClient, op);
        String link = op.getBody(state.getClass()).documentSelfLink;
        return (T) getUpgradeServiceInstance(link, state.getClass(), apiVersion);
    }

    protected <T extends ServiceDocument> T getUpgradeServiceInstance(String link, Class<T> type)
            throws Exception {
        return getUpgradeServiceInstance(link, type, null);
    }

    protected <T extends ServiceDocument> T getUpgradeServiceInstance(String link, Class<T> type,
            String apiVersion) throws Exception {
        URI uri = UriUtils.buildUri(upgradeHost, link);
        Operation op = Operation.createGet(uri);
        UpgradeUtil.setOperationRequestApiVersion(op, apiVersion);
        op = sendRequest(serviceClient, op);
        return op.getBody(type);
    }

    public <T extends ServiceDocument> Collection<T> queryUpgradeServiceInstances(Class<T> type,
            String... keysAndValues) throws Throwable {

        QueryTask q = QueryUtil.buildPropertyQuery(type, keysAndValues);

        return queryUpgradeServiceInstances(type, q);
    }

    public <T extends ServiceDocument> Collection<T> queryUpgradeServiceInstances(Class<T> type,
            String key, Number value) throws Throwable {

        QueryTask.Query query = new QueryTask.Query().setTermMatchType(MatchType.TERM)
                .setTermPropertyName(key)
                .setNumericRange(NumericRange.createEqualRange(value));

        QueryTask q = QueryUtil.buildQuery(type, true, query);

        return queryUpgradeServiceInstances(type, q);
    }

    private <T extends ServiceDocument> Collection<T> queryUpgradeServiceInstances(Class<T> type,
            QueryTask q) {
        q = QueryUtil.addExpandOption(q);

        final Collection<T> instances = new ArrayList<>();

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
