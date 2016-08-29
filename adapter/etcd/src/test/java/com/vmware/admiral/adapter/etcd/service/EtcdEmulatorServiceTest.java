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

package com.vmware.admiral.adapter.etcd.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.etcd.service.EtcdEmulatorService.EtcdNode;
import com.vmware.admiral.adapter.etcd.service.EtcdEmulatorService.EtcdNodeError;
import com.vmware.admiral.adapter.etcd.service.EtcdEmulatorService.EtcdNodeResult;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitEtcdAdapterServiceConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.serialization.JsonMapper;

public class EtcdEmulatorServiceTest extends BaseTestCase {

    @Before
    public void startServices() throws Throwable {
        HostInitEtcdAdapterServiceConfig.startServices(host, true);
    }

    @Test
    public void testAddKeyValue() throws Throwable {

        /*
         * GET /v2/keys/key1
         *
         * {
         *   "errorCode": 100,
         *   "message": "Key not found",
         *   "cause": "/key1"
         * }
         */

        EtcdNodeError error = doGet(EtcdEmulatorService.SELF_LINK + "/key1",
                EtcdNodeError.class);
        assertErrorKeyNotFound("/key1", error);

        /*
         * PUT /v2/keys/key1 value="value"
         *
         * {
         *   "action": "set",
         *   "node": {
         *     "key": "/key1",
         *     "value": "value"
         *   }
         * }
         */

        EtcdNodeResult result = doPut(EtcdEmulatorService.SELF_LINK + "/key1", "value");
        assertResult("set", "/key1", "value", result);

        /*
         * GET /v2/keys/key1
         *
         * {
         *   "action": "get",
         *   "node": {
         *     "key": "/key1",
         *     "value": "value"
         *   }
         * }
         */

        result = doGet(EtcdEmulatorService.SELF_LINK + "/key1", EtcdNodeResult.class);
        assertResult("get", "/key1", "value", result);
    }

    @Test
    public void testUpdateKeyValue() throws Throwable {

        /*
         * PUT /v2/keys/key2 value="value"
         *
         * {
         *   "action": "set",
         *   "node": {
         *     "key": "/key2",
         *     "value": "value"
         *   }
         * }
         */

        EtcdNodeResult result = doPut(EtcdEmulatorService.SELF_LINK + "/key2", "value");
        assertResult("set", "/key2", "value", result);

        /*
         * GET /v2/keys/key2
         *
         * {
         *   "action": "get",
         *   "node": {
         *     "key": "/key2",
         *     "value": "value"
         *   }
         * }
         */

        result = doGet(EtcdEmulatorService.SELF_LINK + "/key2", EtcdNodeResult.class);
        assertResult("get", "/key2", "value", result);

        /*
         * PUT /v2/keys/key2 value="new value"
         *
         * {
         *   "action": "set",
         *   "node": {
         *     "key": "/key2",
         *     "value": "value"
         *   }
         * }
         */

        result = doPut(EtcdEmulatorService.SELF_LINK + "/key2", "new value");
        assertResult("set", "/key2", "new value", result);

        /*
         * GET /v2/keys/key2
         *
         * {
         *   "action": "get",
         *   "node": {
         *     "key": "/key2",
         *     "value": "new value"
         *   }
         * }
         */

        result = doGet(EtcdEmulatorService.SELF_LINK + "/key2", EtcdNodeResult.class);
        assertResult("get", "/key2", "new value", result);
    }

    @Test
    public void testDeleteKeyValue() throws Throwable {

        /*
         * PUT /v2/keys/key3 value="value"
         *
         * {
         *   "action": "set",
         *   "node": {
         *     "key": "/key3",
         *     "value": "value"
         *   }
         * }
         */

        EtcdNodeResult result = doPut(EtcdEmulatorService.SELF_LINK + "/key3", "value");
        assertResult("set", "/key3", "value", result);

        /*
         * GET /v2/keys/key3
         *
         * {
         *   "action": "get",
         *   "node": {
         *     "key": "/key3",
         *     "value": "value"
         *   }
         * }
         */

        result = doGet(EtcdEmulatorService.SELF_LINK + "/key3", EtcdNodeResult.class);
        assertResult("get", "/key3", "value", result);

        /*
         * DELETE /v2/keys/key3
         *
         * {
         *   "action": "delete",
         *   "node": {
         *     "key": "/key3",
         *     "value": "value"
         *   }
         * }
         */

        result = doDelete(EtcdEmulatorService.SELF_LINK + "/key3", EtcdNodeResult.class);
        assertResult("delete", "/key3", null, result);

        /*
         * GET /v2/keys/key3
         *
         * {
         *   "errorCode": 100,
         *   "message": "Key not found",
         *   "cause": "/key3"
         * }
         */

        EtcdNodeError error = doGet(EtcdEmulatorService.SELF_LINK + "/key3", EtcdNodeError.class);
        assertErrorKeyNotFound("/key3", error);

        /*
         * DELETE /v2/keys/key3
         *
         * {
         *   "errorCode": 100,
         *   "message": "Key not found",
         *   "cause": "/key3"
         * }
         */

        error = doDelete(EtcdEmulatorService.SELF_LINK + "/key3", EtcdNodeError.class);
        assertErrorKeyNotFound("/key3", error);
    }

    @Test
    public void testAddKeyValueInDirectory() throws Throwable {

        /*
         * GET /v2/keys/dir1/dir2/key4
         *
         * {
         *   "errorCode": 100,
         *   "message": "Key not found",
         *   "cause": "/dir1/dir2/key4"
         * }
         */

        EtcdNodeError error = doGet(EtcdEmulatorService.SELF_LINK + "/dir1/dir2/key4",
                EtcdNodeError.class);
        assertErrorKeyNotFound("/dir1/dir2/key4", error);

        /*
         * PUT /v2/keys/dir1/dir2/key4 value="value"
         *
         * {
         *   "action": "set",
         *   "node": {
         *     "key": "/dir1/dir2/key4",
         *     "value": "value"
         *   }
         * }
         */

        EtcdNodeResult result = doPut(EtcdEmulatorService.SELF_LINK + "/dir1/dir2/key4", "value");
        assertResult("set", "/dir1/dir2/key4", "value", result);

        /*
         * GET /v2/keys/dir1/dir2/key4
         *
         * {
         *   "action": "get",
         *   "node": {
         *     "key": "/dir1/dir2/key4",
         *     "value": "value"
         *   }
         * }
         */

        result = doGet(EtcdEmulatorService.SELF_LINK + "/dir1/dir2/key4", EtcdNodeResult.class);
        assertResult("get", "/dir1/dir2/key4", "value", result);

        /*
         * GET /v2/keys/dir1/dir2
         *
         * {
         *   "action": "get",
         *   "node": {
         *     "key": "/dir1/dir2",
         *     "dir": true,
         *     "nodes": [
         *       {
         *         "key": "/dir1/dir2/key4",
         *         "value": "value"
         *       }
         *     ]
         *   }
         * }
         */

        result = doGet(EtcdEmulatorService.SELF_LINK + "/dir1/dir2", EtcdNodeResult.class);
        assertResult("get", "/dir1/dir2", null, result);

        /*
         * GET /v2/keys/dir1
         *
         * {
         *   "action": "get",
         *   "node": {
         *     "key": "/dir1",
         *     "dir": true,
         *     "nodes": [
         *       {
         *         "key": "/dir1/dir2",
         *         "value": "value"
         *       }
         *     ]
         *   }
         * }
         */

        result = doGet(EtcdEmulatorService.SELF_LINK + "/dir1", EtcdNodeResult.class);
        assertResult("get", "/dir1", null, result);
        assertEquals(1, result.node.nodes.size());

        /*
         * GET /v2/keys/dir1?recursive=true
         *
         * {
         *   "action": "get",
         *   "node": {
         *     "key": "/dir1",
         *     "dir": true,
         *     "nodes": [
         *       {
         *         "key": "/dir1/dir2",
         *         "dir": true,
         *         "nodes": [
         *           {
         *             "key": "/dir1/dir2/key4",
         *             "value": "value"
         *           }
         *         ]
         *       }
         *     ]
         *   }
         * }
         */

        // TODO - if needed, listing a directory with recursive=true returns all the children
    }

    @Test
    public void testGetWait() throws Throwable {
        doPut(EtcdEmulatorService.SELF_LINK + "/keyForWait", "value");

        host.testStart(2);

        AtomicReference<EtcdNodeResult> waitResult = new AtomicReference<>();

        Operation get = Operation
                .createGet(
                        UriUtils.buildUri(host, EtcdEmulatorService.SELF_LINK
                                + "/keyForWait?wait=true"))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        waitResult.set(o.getBody(EtcdNodeResult.class));
                        host.completeIteration();
                    }
                });

        host.send(get);

        Thread.sleep(1000);

        // Get did not return even after 1 second
        assertNull(waitResult.get());

        AtomicReference<EtcdNodeResult> putResult = new AtomicReference<>();

        Operation put = Operation
                .createPut(UriUtils.buildUri(host, EtcdEmulatorService.SELF_LINK + "/keyForWait"))
                .setBody("value=" + "new-value")
                .setContentType(Operation.MEDIA_TYPE_APPLICATION_X_WWW_FORM_ENCODED)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                            } else {
                                putResult.set(o.getBody(EtcdNodeResult.class));
                                host.completeIteration();
                            }
                        });

        host.send(put);

        host.testWait();
        assertNotNull(waitResult.get());
        assertNotNull(putResult.get());

        JsonMapper mapper = new JsonMapper();
        assertEquals(mapper.toJson(putResult.get().node), mapper.toJson(waitResult.get().node));
    }

    private EtcdNodeResult doPut(String path, String value)
            throws Throwable {
        waitForServiceAvailability(EtcdEmulatorService.SELF_LINK);

        AtomicReference<EtcdNodeResult> result = new AtomicReference<>();

        Operation put = Operation
                .createPut(UriUtils.buildUri(host, path))
                .setBody("value=" + value)
                .setContentType(Operation.MEDIA_TYPE_APPLICATION_X_WWW_FORM_ENCODED)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                            } else {
                                result.set(o.getBody(EtcdNodeResult.class));
                                host.completeIteration();
                            }
                        });

        host.testStart(1);
        host.send(put);
        host.testWait();

        return result.get();
    }

    private <T> T doGet(String path, Class<T> clazz) throws Throwable {
        waitForServiceAvailability(EtcdEmulatorService.SELF_LINK);

        AtomicReference<T> result = new AtomicReference<>();

        Operation get = Operation
                .createGet(UriUtils.buildUri(host, path))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                            } else {
                                result.set(o.getBody(clazz));
                                host.completeIteration();
                            }
                        });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result.get();
    }

    private <T> T doDelete(String path, Class<T> clazz) throws Throwable {
        waitForServiceAvailability(EtcdEmulatorService.SELF_LINK);

        AtomicReference<T> result = new AtomicReference<>();

        Operation get = Operation
                .createDelete(UriUtils.buildUri(host, path))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                            } else {
                                result.set(o.getBody(clazz));
                                host.completeIteration();
                            }
                        });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result.get();
    }

    private static void assertResult(String action, String key, String value,
            EtcdNodeResult result) {
        assertNotNull(result);
        assertEquals(action, result.action);
        assertNotNull(result.node);

        EtcdNode node = result.node;
        assertEquals(key, node.key);
        assertEquals(value, node.value);
    }

    private static void assertErrorKeyNotFound(String key, EtcdNodeError error) {
        assertNotNull(error);
        assertEquals(100, error.errorCode);
        assertEquals("Key not found", error.message);
        assertEquals(key, error.cause);
    }

}
