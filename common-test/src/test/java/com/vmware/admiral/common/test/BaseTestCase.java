/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.TestServerX509TrustManager;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;

public abstract class BaseTestCase {

    private static final int WAIT_FOR_STAGE_CHANGE_COUNT = Integer.getInteger(
            "dcp.management.test.change.count", 100);
    private static final int WAIT_FOR_STAGE_CHANGE_COUNT_LONGER = Integer.getInteger(
            "dcp.management.test.change.longer.count", 200);
    protected static final int WAIT_THREAD_SLEEP_IN_MILLIS = Integer.getInteger(
            "dcp.management.test.wait.thread.sleep.millis", 500);
    private static final int HOST_TIMEOUT_SECONDS = 60;

    protected static final int MAINTENANCE_INTERVAL_MILLIS = 20;
    protected VerificationHost host;

    private static class CustomizationVerificationHost extends VerificationHost {
        private Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains = new HashMap<>();

        public CustomizationVerificationHost(
                Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
            this.chains.putAll(chains);
        }

        @Override
        public ServiceHost startService(Operation post, Service service) {
            Class<? extends Service> serviceClass = service.getClass();
            if (!applyOperationChainIfNeed(service, serviceClass, serviceClass, false)) {
                if (service instanceof FactoryService) {
                    try {
                        Service actualInstance = ((FactoryService) service).createServiceInstance();
                        Class<? extends Service> instanceClass = actualInstance.getClass();
                        applyOperationChainIfNeed(service, instanceClass, FactoryService.class,
                                false);
                    } catch (Throwable e) {
                        log(Level.SEVERE, "Failure: %s", Utils.toString(e));
                    }
                } else if (service instanceof StatefulService) {
                    applyOperationChainIfNeed(service, serviceClass, StatefulService.class,
                            true);
                }
            }
            return super.startService(post, service);
        }

        private boolean applyOperationChainIfNeed(Service service,
                Class<? extends Service> serviceClass, Class<? extends Service> parameterClass,
                boolean logOnError) {
            if (chains.containsKey(serviceClass)) {
                try {
                    service.setOperationProcessingChain(
                            chains.get(serviceClass)
                                    .getDeclaredConstructor(parameterClass)
                                    .newInstance(service));
                    return true;
                } catch (Exception e) {
                    if (logOnError) {
                        log(Level.SEVERE, "Failure: %s", Utils.toString(e));
                    }
                }
            }
            return false;
        }
    }

    @Before
    public void before() throws Throwable {
        host = createHost();
    }

    @After
    public void after() throws Throwable {
        try {
            host.tearDownInProcessPeers();
            host.tearDown();

        } catch (CancellationException e) {
            host.log(Level.FINE, e.getClass().getName());
        }
        host = null;
    }

    protected VerificationHost createHost() throws Throwable {
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null; // ask runtime to pick a random storage location
        args.port = 0; // ask runtime to pick a random port
        Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains = new HashMap<>();
        customizeChains(chains);

        VerificationHost h = VerificationHost.initialize(new CustomizationVerificationHost(chains),
                args);
        h.setMaintenanceIntervalMicros(this.getMaintenanceIntervalMillis() * 1000);
        h.setTimeoutSeconds(HOST_TIMEOUT_SECONDS);

        h.setPeerSynchronizationEnabled(this.getPeerSynchronizationEnabled());

        h.start();

        return h;
    }

    protected Map.Entry<VerificationHost, ServerX509TrustManager> createHostWithTrustManager(
            long reloadTime) throws Throwable {
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null; // ask runtime to pick a random storage location
        args.port = 0; // ask runtime to pick a random port
        Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains =
                new HashMap<>();
        customizeChains(chains);

        VerificationHost h = VerificationHost.initialize(new CustomizationVerificationHost(chains),
                args);
        h.setMaintenanceIntervalMicros(this.getMaintenanceIntervalMillis() * 1000);

        ServerX509TrustManager trustManager = new TestServerX509TrustManager(h, reloadTime);
        SSLContext sslContext = CertificateUtil.createSSLContext(trustManager, null);

        h.setClient(createServiceClient(sslContext, 0, h));
        h.setPeerSynchronizationEnabled(this.getPeerSynchronizationEnabled());

        h.start();

        return new AbstractMap.SimpleEntry<>(h, trustManager);
    }

    private ServiceClient createServiceClient(SSLContext sslContext,
            int requestPayloadSizeLimit, VerificationHost verificationHost) {
        try {
            String userAgent = ServiceHost.class.getSimpleName();
            ServiceClient serviceClient = NettyHttpServiceClient.create(userAgent,
                    null,
                    verificationHost.getScheduledExecutor(),
                    verificationHost);
            if (requestPayloadSizeLimit > 0) {
                serviceClient.setRequestPayloadSizeLimit(requestPayloadSizeLimit);
            }
            serviceClient.setSSLContext(sslContext);

            return serviceClient;

        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to create ServiceClient", e);
        }
    }

    /**
     * Returns default peer synchronization flag. For hosts started in single mode it should be
     * false till https://www.pivotaltracker.com/n/projects/1471320/stories/138426713 is resolved.
     * <p/>
     * Tests for clustered nodes AND tests calling registerForServiceAvailability with
     * checkForReplica true SHOULD overwrite this method and return <code>true</code>.
     *
     * @return boolean value
     */
    protected boolean getPeerSynchronizationEnabled() {
        return false;
    }

    /**
     * Returns maintenance interval millis to be set to the host
     * @return milliseconds
     */
    protected long getMaintenanceIntervalMillis() {
        return MAINTENANCE_INTERVAL_MILLIS;
    }

    protected void customizeChains(
            Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
    }

    public static TestContext testCreate(int count) {
        long expIntervalMicros = TimeUnit.MILLISECONDS.toMicros(
                WAIT_FOR_STAGE_CHANGE_COUNT * WAIT_THREAD_SLEEP_IN_MILLIS);
        return TestContext.create(count, expIntervalMicros);
    }

    public static void testWait(TestContext ctx) throws Throwable {
        ctx.await();
    }

    protected void clearFailure() throws Exception {
        // the VerificationHost doesn't clear the failure field after each test so all subsequent
        // tests (as well as tearDown operations) in the class will also fail
        Field failure = VerificationHost.class.getDeclaredField("failure");
        setPrivateField(failure, host, null);
    }

    protected void waitForServiceAvailability(String... serviceLinks) throws Throwable {
        waitForServiceAvailability(host, serviceLinks);
    }

    protected void waitForServiceAvailability(ServiceHost h, String... serviceLinks)
            throws Throwable {
        if (serviceLinks == null || serviceLinks.length == 0) {
            throw new IllegalArgumentException("null or empty serviceLinks");
        }
        TestContext ctx = testCreate(serviceLinks.length);
        h.registerForServiceAvailability(ctx.getCompletion(), serviceLinks);
        ctx.await();
    }

    protected URI register(VerificationHost host, Class<? extends Service> type)
            throws Exception {
        try {
            TestContext ctx = testCreate(1);
            Field f = type.getField("SELF_LINK");
            String path = (String) f.get(null);
            host.registerForServiceAvailability(ctx.getCompletion(), path);
            ctx.await();
        } catch (Throwable e) {
            throw new Exception(e);
        }

        return UriUtils.buildUri(host, type);
    }

    protected void verifyService(Class<? extends FactoryService> factoryClass,
            Class<? extends ServiceDocument> serviceDocumentType,
            TestServiceDocumentInitialization serviceDocumentInit,
            TestServiceDocumentAssertion assertion) throws Throwable {
        URI factoryUri = UriUtils.buildUri(host, factoryClass);
        FactoryService factoryInstance = getNewInstance(factoryClass);
        verifyService(factoryUri, factoryInstance, serviceDocumentType, serviceDocumentInit,
                assertion);
    }

    protected void verifyService(Service factoryInstance,
            Class<? extends ServiceDocument> serviceDocumentType,
            TestServiceDocumentInitialization serviceDocumentInit,
            TestServiceDocumentAssertion assertion) throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(host, ((FactoryService) factoryInstance)
                .createServiceInstance().getClass());
        verifyService(factoryUri, (FactoryService) factoryInstance, serviceDocumentType,
                serviceDocumentInit, assertion);
    }

    protected void verifyService(URI factoryUri, FactoryService factoryInstance,
            Class<? extends ServiceDocument> serviceDocumentType,
            TestServiceDocumentInitialization serviceDocumentInit,
            TestServiceDocumentAssertion assertion) throws Throwable {
        int childCount = 1;
        TestContext ctx = testCreate(childCount);
        String prefix = "example-";
        URI[] childURIs = new URI[childCount];
        for (int i = 0; i < childCount; i++) {
            ServiceDocument serviceDocument = serviceDocumentInit.create(prefix, i);
            final int finalI = i;
            // create a ServiceDocument instance.
            Operation createPost = OperationUtil.createForcedPost(factoryUri)
                    .setBody(serviceDocument).setCompletion((o, e) -> {
                        if (e != null) {
                            ctx.failIteration(e);
                            return;
                        }
                        ServiceDocument rsp = o.getBody(serviceDocumentType);
                        childURIs[finalI] = UriUtils.buildUri(host, rsp.documentSelfLink);
                        ctx.completeIteration();
                    });
            host.send(createPost);
        }

        try {
            // verify factory and service instance wiring.
            factoryInstance.setHost(host);
            Service serviceInstance = factoryInstance.createServiceInstance();
            serviceInstance.setHost(host);
            assertNotNull(serviceInstance);

            ctx.await();

            // do GET on all child URIs
            Map<URI, ? extends ServiceDocument> childStates = host.getServiceState(null,
                    serviceDocumentType, childURIs);
            for (ServiceDocument s : childStates.values()) {
                assertion.assertState(prefix, s);
            }

            // verify template GET works on factory
            ServiceDocumentQueryResult templateResult = host.getServiceState(null,
                    ServiceDocumentQueryResult.class,
                    UriUtils.extendUri(factoryUri, ServiceHost.SERVICE_URI_SUFFIX_TEMPLATE));

            assertTrue(templateResult.documentLinks.size() == templateResult.documents.size());

            ServiceDocument childTemplate = Utils.fromJson(
                    templateResult.documents.get(templateResult.documentLinks.iterator().next()),
                    serviceDocumentType);

            assertTrue(childTemplate.documentDescription != null);
            assertTrue(childTemplate.documentDescription.propertyDescriptions != null
                    && childTemplate.documentDescription.propertyDescriptions
                            .size() > 0);

            if (!TaskServiceDocument.class.isAssignableFrom(childTemplate.getClass())) {
                Field[] allFields = childTemplate.getClass().getDeclaredFields();
                for (Field field : allFields) {
                    if (Modifier.isPublic(field.getModifiers())) {
                        Object value = field.get(childTemplate);
                        if (!field.getName().equals("taskSubStage")) {
                            assertNotNull("field value is null with name: "
                                    + field.getName()
                                    + ". Add default value in getDocumentTemplate().", value);
                        }
                        if (!Modifier.isStatic(field.getModifiers())) {
                            assertTrue(childTemplate.documentDescription.propertyDescriptions
                                    .containsKey(field.getName()));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw new RuntimeException(t);
        }
    }

    protected void verifyOperation(Operation op) throws Throwable {
        verifyOperation(op, null);
    }

    protected void verifyOperation(Operation op, Consumer<Operation> verification)
            throws Throwable {
        TestContext ctx = testCreate(1);
        op.setCompletion((o, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
            } else {
                try {
                    if (verification != null) {
                        verification.accept(o);
                    }
                    ctx.completeIteration();
                } catch (Throwable x) {
                    ctx.failIteration(x);
                }
            }
        });

        host.send(op);
        ctx.await();
    }

    protected <T> T getNewInstance(Class<T> clazz) {
        Objenesis objenesis = new ObjenesisStd();
        ObjectInstantiator<T> instantiator = objenesis.getInstantiatorOf(clazz);
        return instantiator.newInstance();
    }

    public static interface TestServiceDocumentInitialization {
        ServiceDocument create(String prefix, int index) throws Throwable;
    }

    public static interface TestServiceDocumentAssertion {
        void assertState(String prefix, ServiceDocument s) throws Throwable;
    }

    protected URI getFactoryUrl(Class<? extends FactoryService> factoryClass) {
        return UriUtils.buildUri(host, factoryClass);
    }

    protected static AtomicInteger waitForStageChangeCount() {
        return new AtomicInteger(WAIT_FOR_STAGE_CHANGE_COUNT);
    }

    protected static AtomicInteger waitForStageChangeCountLonger() {
        return new AtomicInteger(WAIT_FOR_STAGE_CHANGE_COUNT_LONGER);
    }

    protected static void waitFor(TestWaitForHandler handler) throws Throwable {
        waitFor("Failed waiting for condition... ", handler);
    }

    protected static void waitFor(String errorMessage, TestWaitForHandler handler)
            throws Throwable {
        int iterationCount = waitForStageChangeCount().get();
        Thread.sleep(WAIT_THREAD_SLEEP_IN_MILLIS / 5);
        for (int i = 0; i < iterationCount; i++) {
            if (handler.test()) {
                return;
            }

            Thread.sleep(WAIT_THREAD_SLEEP_IN_MILLIS);
        }
        fail(errorMessage);
    }

    // utility method to be used as helper for discovering issues with test when run in a loop
    protected void runInLoop(int count, RunnableHandler function) throws Throwable {
        for (int i = 0; i < count; i++) {
            System.out.println("########################################################");
            System.out.println("################ Iteration: " + i);
            System.out.println("########################################################");
            function.run();
        }
    }

    @FunctionalInterface
    public static interface TestWaitForHandler {
        boolean test() throws Throwable;
    }

    /**
     * Waits until the specified document field matches the given value. Throws an exception
     * if times out (default timeout is used). Nested properties can be used to (e.g. "x.y.z").
     */
    protected <T> T waitForPropertyValue(String documentSelfLink, Class<T> type, String propName,
            Object propValue) throws Throwable {
        return waitForPropertyValue(documentSelfLink, type, propName, Arrays.asList(propValue),
                true, waitForStageChangeCount());
    }

    /**
     * Waits until the specified document field matches the given value or differs from the given
     * value. Throws an exception if times out. Nested properties can be used to (e.g. "x.y.z").
     */
    protected <T> T waitForPropertyValue(String documentSelfLink, Class<T> type, String propName,
            Object propValue, boolean shouldMatch, AtomicInteger count)
            throws Throwable {
        @SuppressWarnings("unchecked")
        List<Object> prop = propValue instanceof List ? (List<Object>) propValue : Arrays
                .asList(propValue);

        return waitForPropertyValue(documentSelfLink, type, propName, prop, shouldMatch, count);
    }

    /**
     * Waits until the specified document field is within the given set of values (or not within
     * the set, depending on the shouldMatch parameter). Throws an exception if times out.
     * Nested properties can be used to (e.g. "x.y.z").
     */
    protected <T> T waitForPropertyValue(String documentSelfLink, Class<T> type, String propName,
            Collection<Object> propValues, boolean shouldMatch, AtomicInteger count)
            throws Throwable {
        Thread.sleep(WAIT_THREAD_SLEEP_IN_MILLIS / 5);
        Object lastRetrievedValue = null;
        int iterationCount = count.get();
        for (int i = 0; i < iterationCount; i++) {
            T document = getDocument(type, documentSelfLink);
            assertNotNull(document);

            if (document instanceof String) {
                JsonElement jelement = new JsonParser().parse((String) document);
                JsonObject jobject = jelement.getAsJsonObject();
                final String valueAsString = jobject.get(propName).getAsString();
                boolean matches = propValues.stream()
                        .anyMatch((obj) -> obj.toString().equals(valueAsString));
                if (matches == shouldMatch) {
                    return document;
                }
                lastRetrievedValue = valueAsString;
            } else {
                lastRetrievedValue = getObjectFieldValueByPath(document, propName);
                boolean matches = propValues.contains(lastRetrievedValue);
                if (matches == shouldMatch) {
                    return document;
                }
            }
            Thread.sleep(WAIT_THREAD_SLEEP_IN_MILLIS);
        }

        throw new RuntimeException(String.format(
                "Failed waiting property %s in document %s to %s %s. Last value was: %s",
                propName, documentSelfLink, shouldMatch ? "match" : "not match",
                propValues.size() == 1 ? propValues.iterator().next() : propValues,
                lastRetrievedValue));
    }

    /**
     * Waits until the given task succeeds and returns its final state.
     *
     * Note: will stop polling if the task transitions to any final state.
     */
    protected <T extends TaskServiceDocument<E>, E extends Enum<E>> T waitForTaskSuccess(
            String documentSelfLink, Class<T> type) throws Throwable {
        T taskState = waitForTaskCompletion(documentSelfLink, type);
        boolean isSuccess = taskState.taskInfo.stage.equals(TaskStage.FINISHED);
        assertTrue("Task " + documentSelfLink + " was expected to succeed but failed", isSuccess);
        return taskState;
    }

    /**
     * Waits until the given task fails and returns its final state.
     *
     * Note: will stop polling if the task transitions to any final state.
     */
    protected <T extends TaskServiceDocument<E>, E extends Enum<E>> T waitForTaskError(
            String documentSelfLink, Class<T> type) throws Throwable {
        T taskState = waitForTaskCompletion(documentSelfLink, type);
        boolean isFailure = !taskState.taskInfo.stage.equals(TaskStage.FINISHED);
        assertTrue("Task " + documentSelfLink + " was expected to fail but succeeded", isFailure);
        return taskState;
    }

    /**
     * Waits until the given task completes and returns its final state.
     */
    protected <T extends TaskServiceDocument<E>, E extends Enum<E>> T waitForTaskCompletion(
            String documentSelfLink, Class<T> type) throws Throwable {
        return waitForPropertyValue(documentSelfLink, type,
                TaskServiceDocument.FIELD_NAME_TASK_STAGE,
                Arrays.asList(TaskStage.FINISHED, TaskStage.FAILED, TaskStage.CANCELLED), true,
                waitForStageChangeCountLonger());
    }

    /**
     * Helper to find the value of a direct or nested field in an object by the dot-separated
     * path to it (e.g. "parent.configuration.state"). Throws an exception if the field is not found
     * or not accessible.
     */
    private static Object getObjectFieldValueByPath(Object obj, String path) throws Exception {
        Field field = null;
        Object currentObj = obj;
        for (String pathHop : path.split("\\.")) {
            field = currentObj.getClass().getField(pathHop);
            currentObj = field.get(currentObj);
        }
        return currentObj;
    }

    protected void setUpDockerHostAuthentication() throws Throwable {
        waitForServiceAvailability(AuthCredentialsService.FACTORY_LINK);

        ServiceDocumentQuery<AuthCredentialsServiceState> query = new ServiceDocumentQuery<>(host,
                AuthCredentialsServiceState.class);

        TestContext ctx = testCreate(1);
        query.queryDocument(UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                CommonTestStateFactory.AUTH_CREDENTIALS_ID),
                (r) -> {
                    if (r.hasException()) {
                        r.throwRunTimeException();
                    } else if (!r.hasResult()) {
                        host.send(Operation.createPost(
                                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK))
                                .setBody(CommonTestStateFactory.createAuthCredentials(false))
                                .setCompletion(ctx.getCompletion()));
                    } else {
                        ctx.completeIteration();
                    }
                });
        ctx.await();
    }

    protected static String extractId(String link) {
        AssertUtil.assertNotNull(link, "link");
        if (link.endsWith(UriUtils.URI_PATH_CHAR)) {
            link = link.substring(0, link.length() - 1);
        }
        return link.substring(link.lastIndexOf(UriUtils.URI_PATH_CHAR) + 1);
    }

    protected <T> T getDocument(Class<T> type, String selfLink) throws Throwable {
        return getDocument(type, selfLink, new String[0]);
    }

    @SuppressWarnings("unchecked")
    protected <T> T getDocument(Class<T> type, String selfLink, String... keyValues)
            throws Throwable {
        TestContext ctx = testCreate(1);
        URI uri = UriUtils.buildUri(host, selfLink);
        uri = UriUtils.extendUriWithQuery(uri, keyValues);
        Object[] result = new Object[1];
        Operation get = Operation
                .createGet(uri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setReferer(host.getReferer())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.log("Can't load document %s. Error: %s", selfLink,
                                        Utils.toString(e));
                                ctx.failIteration(e);
                            } else {
                                result[0] = o.getBody(type);
                                ctx.completeIteration();
                            }
                        });

        host.send(get);
        ctx.await();
        return (T) result[0];
    }

    /**
     * Retrieves the document by the given link without waiting for service availability.
     * No exception is thrown if not found, just null is returned.
     */
    @SuppressWarnings("unchecked")
    protected <T> T getDocumentNoWait(Class<T> type, String selfLink)
            throws Throwable {
        TestContext ctx = testCreate(1);
        URI uri = UriUtils.buildUri(host, selfLink);
        Object[] result = new Object[1];
        Operation get = Operation
                .createGet(uri)
                .setReferer(host.getReferer())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.log(Level.WARNING, "Can't load document %s. Error: %s",
                                        selfLink, e.toString());
                            } else {
                                result[0] = o.getBody(type);
                            }
                            ctx.completeIteration();
                        });

        host.send(get);
        ctx.await();
        return (T) result[0];
    }

    @SuppressWarnings("unchecked")
    protected <T extends ServiceDocument> T searchForDocument(Class<T> type, String selfLink)
            throws Throwable {
        Object[] result = new Object[] { null };
        TestContext ctx = testCreate(1);
        @SuppressWarnings("rawtypes")
        ServiceDocumentQuery<?> query = new ServiceDocumentQuery(host, type);
        query.queryDocument(
                selfLink,
                (r) -> {
                    if (r.hasException()) {
                        host.log(
                                Level.SEVERE,
                                "Exception during search for resource not found: %s for type: %s. Error message: %s",
                                selfLink, type.getSimpleName(), r.getException().getMessage());
                        ctx.failIteration(r.getException());
                    } else if (r.hasResult()) {
                        result[0] = r.getResult();
                        host.log(Level.WARNING, "Resource found: %s for type %s", selfLink,
                                type.getSimpleName());
                        ctx.completeIteration();
                    } else {
                        host.log("Resource not found: %s for type %s", selfLink,
                                type.getSimpleName());
                        ctx.completeIteration();
                    }
                });
        ctx.await();
        return (T) result[0];
    }

    /**
     * Tries to retrieve the given document and creates it (through a POST request), if not found.
     * Assuming the {@code documentSelfLink} in the given document does not contain the full path
     * but only the relative path from the given {@code factoryLink}.
     *
     * Returns the document state as retrieved from the server.
     */
    protected <T extends ServiceDocument> T getOrCreateDocument(T inState, String factoryLink)
            throws Throwable {
        @SuppressWarnings("unchecked")
        T foundDocument = searchForDocument((Class<T>) inState.getClass(),
                UriUtils.buildUriPath(factoryLink, inState.documentSelfLink));
        if (foundDocument != null) {
            return foundDocument;
        } else {
            return doPost(inState, factoryLink);
        }
    }

    protected <T extends ServiceDocument> T doPost(T inState, String fabricServiceUrlPath)
            throws Throwable {
        return doPost(inState, UriUtils.buildUri(host, fabricServiceUrlPath), false);
    }

    protected <T extends ServiceDocument> T doPost(T inState, URI uri, boolean expectFailure)
            throws Throwable {
        String documentSelfLink = doOperation(inState, uri, expectFailure,
                Action.POST).documentSelfLink;
        @SuppressWarnings("unchecked")
        T outState = (T) host.getServiceState(null,
                inState.getClass(),
                UriUtils.buildUri(uri.getHost(), uri.getPort(), documentSelfLink, null));
        if (outState.documentSelfLink == null) {
            outState.documentSelfLink = documentSelfLink;
        }
        return outState;
    }

    protected <T extends ServiceDocument> T doPatch(T inState, String serviceDocumentSelfLink)
            throws Throwable {
        return doPatch(inState, serviceDocumentSelfLink, false);
    }

    protected <T extends ServiceDocument> T doPatch(T inState, String serviceDocumentSelfLink,
            boolean expectFailure)
            throws Throwable {
        URI uri = UriUtils.buildUri(host, serviceDocumentSelfLink);
        doOperation(inState, uri, expectFailure, Action.PATCH);
        @SuppressWarnings("unchecked")
        T outState = (T) host.getServiceState(null,
                inState.getClass(),
                uri);
        return outState;
    }

    protected <T extends ServiceDocument> T doPut(T inState)
            throws Throwable {
        URI uri = UriUtils.buildUri(host, inState.documentSelfLink);
        doOperation(inState, uri, false, Action.PUT);
        @SuppressWarnings("unchecked")
        T outState = (T) host.getServiceState(null,
                inState.getClass(), uri);
        return outState;
    }

    protected void doDelete(URI uri, boolean expectFailure) throws Throwable {
        doOperation(new ServiceDocument(), uri, expectFailure, Action.DELETE);
    }

    @SuppressWarnings("unchecked")
    protected <T extends ServiceDocument> T doOperation(T inState, URI uri,
            boolean expectFailure, Action action) throws Throwable {
        return (T) doOperation(inState, uri, ServiceDocument.class, expectFailure, action);
    }

    protected <T extends ServiceDocument> T doOperation(T inState, URI uri, Class<T> type,
            boolean expectFailure, Action action) throws Throwable {
        host.log("Executing operation %s for resource: %s ...", action.name(), uri);
        final List<T> doc = Arrays.asList((T) null);
        final Throwable[] error = { null };
        TestContext ctx = testCreate(1);

        Operation op;
        if (action == Action.POST) {
            op = OperationUtil.createForcedPost(uri);
        } else {
            // createPost sets the proper authorization context for the operation
            op = Operation.createPost(uri);
            // replace POST with the provided action
            op.setAction(action);
        }

        op.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY);

        op.setBody(inState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                if (expectFailure) {
                                    error[0] = e;
                                    ctx.completeIteration();
                                } else {
                                    ctx.failIteration(e);
                                }
                                return;
                            }
                            if (!o.hasBody()) {
                                ctx.failIteration(new IllegalStateException("body was expected"));
                                return;
                            }
                            doc.set(0, o.getBody(type));
                            if (expectFailure) {
                                ctx.failIteration(new IllegalStateException(
                                        "ERROR: operation completed successfully but exception excepted."));
                            } else {
                                ctx.completeIteration();
                            }
                        });
        host.send(op);
        ctx.await();
        host.logThroughput();

        if (expectFailure) {
            Throwable ex = error[0];
            throw ex;
        }
        return doc.get(0);
    }

    public void delete(String selfLink) throws Throwable {
        TestContext ctx = testCreate(1);
        Operation delete = Operation
                .createDelete(UriUtils.buildUri(host, selfLink))
                .setBody(new ServiceDocument())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx.failIteration(e);
                        return;
                    }
                    ctx.completeIteration();
                });
        host.send(delete);
        ctx.await();
    }

    protected void safeDelete(ServiceDocument doc) throws Throwable {
        if (doc != null) {
            try {
                delete(doc.documentSelfLink);
            } catch (Throwable e) {
                host.log("Exception during cleanup for: " + doc.documentSelfLink);
            }
        }
    }

    public List<String> findResourceLinks(Class<? extends ServiceDocument> type,
            Collection<String> resourceLinks) throws Throwable {
        TestContext ctx = testCreate(1);
        QueryTask query = QueryUtil.buildQuery(type, true);
        QueryUtil.addListValueClause(query, ServiceDocument.FIELD_NAME_SELF_LINK, resourceLinks);

        List<String> result = new LinkedList<>();
        new ServiceDocumentQuery<>(
                host, type).query(query,
                        (r) -> {
                            if (r.hasException()) {
                                ctx.failIteration(r.getException());
                                return;
                            }
                            if (r.hasResult()) {
                                result.add(r.getDocumentSelfLink());
                                return;
                            }
                            ctx.completeIteration();
                        });
        ctx.await();

        return result;
    }

    protected void stopService(Service s) throws Throwable {
        if (s == null || s.getSelfLink() == null || s.getSelfLink().isEmpty()) {
            return;
        }
        TestContext ctx = testCreate(1);
        Operation deleteOp = Operation.createDelete(UriUtils.buildUri(host, s.getSelfLink()))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_INDEX_UPDATE)
                .setReplicationDisabled(true)
                .setCompletion(ctx.getCompletion())
                .setReferer(host.getUri());
        host.send(deleteOp);
        ctx.await();
    }

    protected static void setFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);

        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(null, newValue);
    }

    @SuppressWarnings("unchecked")
    protected static <T> T getPrivateField(Field field, Object instance) throws Exception {
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    protected static void setPrivateField(Field field, Object instance, Object newValue)
            throws Exception {
        field.setAccessible(true);
        field.set(instance, newValue);
    }

    protected void validateLocalizableException(LocalizableExceptionHandler handler, String expectation)
            throws Throwable {
        try {
            handler.call();
            fail("LocalizableValidationException expected: " + expectation);
        } catch (LocalizableValidationException e) {
            // expected
        }
    }

    protected void waitForInitialBootServiceToBeSelfStopped(String bootServiceSelfLink)
            throws Throwable {
        waitFor("Failed waiting for " + bootServiceSelfLink
                + " to self stop itself after all instances created.",
                () -> {
                    TestContext ctx = testCreate(1);
                    URI uri = UriUtils.buildUri(host, bootServiceSelfLink);
                    AtomicBoolean serviceStopped = new AtomicBoolean();
                    Operation get = Operation
                            .createGet(uri)
                            .setReferer(host.getReferer())
                            .setCompletion((o, e) -> {
                                if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                                    serviceStopped.set(true);
                                    ctx.completeIteration();
                                    return;
                                }

                                if (e != null) {
                                    host.log("Can't get status of  %s. Error: %s", uri,
                                            Utils.toString(e));
                                    ctx.failIteration(e);
                                    return;
                                }

                                ctx.completeIteration();
                            });
                    host.send(get);
                    ctx.await();

                    return serviceStopped.get();
                });
    }

    @FunctionalInterface
    protected static interface LocalizableExceptionHandler {
        void call() throws Throwable;
    }

    @FunctionalInterface
    protected static interface RunnableHandler {
        void run() throws Throwable;
    }
}
