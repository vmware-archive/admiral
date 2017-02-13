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

package com.vmware.admiral.adapter.kubernetes.service.apiobject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.vmware.admiral.compute.content.kubernetes.ObjectMeta;
import com.vmware.admiral.compute.content.kubernetes.namespaces.Namespace;
import com.vmware.admiral.compute.content.kubernetes.namespaces.NamespaceList;
import com.vmware.admiral.compute.content.kubernetes.namespaces.NamespaceSpec;
import com.vmware.xenon.common.Utils;

public class ApiObjectSerializeTest {
    @Test
    public void TestParseNamespaceSchema() {
        final String data = "{\n"
                + "  \"kind\": \"string\",\n"
                + "  \"apiVersion\": \"string\",\n"
                + "  \"metadata\": {\n"
                + "    \"name\": \"string\",\n"
                + "    \"generateName\": \"string\",\n"
                + "    \"namespace\": \"string\",\n"
                + "    \"selfLink\": \"string\",\n"
                + "    \"uid\": \"string\",\n"
                + "    \"resourceVersion\": \"string\",\n"
                + "    \"generation\": 0,\n"
                + "    \"creationTimestamp\": \"string\",\n"
                + "    \"deletionTimestamp\": \"string\",\n"
                + "    \"deletionGracePeriodSeconds\": 0\n"
                + "  },\n"
                + "  \"spec\": {\n"
                + "    \"finalizers\": [\n"
                + "      \"string\"\n"
                + "    ]\n"
                + "  },\n"
                + "  \"status\": {\n"
                + "    \"phase\": \"string\"\n"
                + "  }\n"
                + "}";

        Namespace namespace = Utils.fromJson(data, Namespace.class);
        assertNotNull(namespace);
        /*assertNotNull(namespace.kind);
        assertNotNull(namespace.apiVersion);*/
        assertNotNull(namespace.metadata);
        assertNotNull(namespace.metadata.name);
        /*assertNotNull(namespace.metadata.generateName);
        assertNotNull(namespace.metadata.selfLink);
        assertNotNull(namespace.metadata.uid);
        assertNotNull(namespace.metadata.resourceVersion);
        assertNotNull(namespace.metadata.generation);
        assertNotNull(namespace.metadata.creationTimestamp);
        assertNotNull(namespace.metadata.deletionTimestamp);
        assertNotNull(namespace.metadata.deletionGracePeriodSeconds);
        assertNotNull(namespace.spec);
        assertNotNull(namespace.spec.finalizers);
        assertNotNull(namespace.status);
        assertNotNull(namespace.status.phase);
        assertEquals("string", namespace.kind);
        assertEquals("string", namespace.apiVersion);
        assertEquals("string", namespace.status.phase);
        assertEquals(1, namespace.spec.finalizers.length);*/
    }

    @Test
    public void TestNamespaceCorrectSerialize() {
        Namespace namespace = new Namespace();
        namespace.metadata = new ObjectMeta();

        String json = Utils.toJson(namespace);
        Namespace result = Utils.fromJson(json, Namespace.class);

        assertNotNull(result);
        assertNotNull(result.metadata);
    }

    @Test
    public void TestNamespaceListSchema() {
        final String data = "{\n"
                + "  \"kind\": \"string\",\n"
                + "  \"apiVersion\": \"string\",\n"
                + "  \"metadata\": {\n"
                + "    \"selfLink\": \"string\",\n"
                + "    \"resourceVersion\": \"string\"\n"
                + "  },\n"
                + "  \"items\": [\n"
                + "    {\n"
                + "      \"kind\": \"string\",\n"
                + "      \"apiVersion\": \"string\",\n"
                + "      \"metadata\": {\n"
                + "        \"name\": \"string\",\n"
                + "        \"generateName\": \"string\",\n"
                + "        \"namespace\": \"string\",\n"
                + "        \"selfLink\": \"string\",\n"
                + "        \"uid\": \"string\",\n"
                + "        \"resourceVersion\": \"string\",\n"
                + "        \"generation\": 0,\n"
                + "        \"creationTimestamp\": \"string\",\n"
                + "        \"deletionTimestamp\": \"string\",\n"
                + "        \"deletionGracePeriodSeconds\": 0\n"
                + "      },\n"
                + "      \"spec\": {\n"
                + "        \"finalizers\": [\n"
                + "          \n"
                + "        ]\n"
                + "      },\n"
                + "      \"status\": {\n"
                + "        \"phase\": \"string\"\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        NamespaceList list = Utils.fromJson(data, NamespaceList.class);
        assertNotNull(list);
        /*assertNotNull(list.kind);
        assertNotNull(list.apiVersion);
        assertNotNull(list.metadata);
        assertNotNull(list.metadata.selfLink);
        assertNotNull(list.metadata.resourceVersion);*/
        assertNotNull(list.items);
        assertEquals(1, list.items.size());
        assertNotNull(list.items.get(0).metadata);
        assertEquals("string", list.items.get(0).metadata.name);
    }

    @Test
    public void TestNamespaceListRegression() {
        final String data = "{"
                + "\"kind\":\"NamespaceList\","
                + "\"apiVersion\":\"v1\","
                + "\"metadata\":{"
                    + "\"selfLink\":\"/api/v1/namespaces\","
                    + "\"resourceVersion\":\"1805115\""
                + "},"
                + "\"items\":["
                    + "{"
                        + "\"metadata\":{"
                            + "\"name\":\"default\","
                            + "\"selfLink\":\"/api/v1/namespaces/default\","
                            + "\"uid\":\"ab67fc5e-b86f-11e6-a8d4-0050569de380\","
                            + "\"resourceVersion\":\"6\","
                            + "\"creationTimestamp\":\"2016-12-02T09:14:03Z\""
                        + "},"
                        + "\"spec\":{"
                            + "\"finalizers\":[\"kubernetes\"]"
                        + "},"
                        + "\"status\":{"
                            + "\"phase\":\"Active\""
                        + "}"
                    + "},"
                    + "{"
                        + "\"metadata\":{"
                            + "\"name\":\"kube-system\","
                            + "\"selfLink\":\"/api/v1/namespaces/kube-system\","
                            + "\"uid\":\"ab6d6957-b86f-11e6-a8d4-0050569de380\","
                            + "\"resourceVersion\":\"79\","
                            + "\"creationTimestamp\":\"2016-12-02T09:14:03Z\","
                            + "\"annotations\":{"
                                + "\"kubectl.kubernetes.io/last-applied-configuration\":\"{"
                                    + "\\\"kind\\\":\\\"Namespace\\\","
                                    + "\\\"apiVersion\\\":\\\"v1\\\","
                                    + "\\\"metadata\\\":{"
                                        + "\\\"name\\\":\\\"kube-system\\\","
                                        + "\\\"creationTimestamp\\\":null"
                                    + "},"
                                    + "\\\"spec\\\":{},"
                                    + "\\\"status\\\":{}"
                                + "}\""
                            + "}"
                        + "},"
                        + "\"spec\":{"
                            + "\"finalizers\":[\"kubernetes\"]"
                        + "},"
                        + "\"status\":{"
                            + "\"phase\":\"Active\""
                        + "}"
                    + "},"
                    + "{"
                        + "\"metadata\":{"
                            + "\"name\":\"my-namespace\","
                            + "\"selfLink\":\"/api/v1/namespaces/my-namespace\","
                            + "\"uid\":\"83e52c12-c601-11e6-a2ea-0050569de380\","
                            + "\"resourceVersion\":\"1634212\","
                            + "\"creationTimestamp\":\"2016-12-19T15:40:49Z\""
                        + "},"
                        + "\"spec\":{"
                            + "\"finalizers\":[\"kubernetes\"]"
                        + "},"
                        + "\"status\":{"
                            + "\"phase\":\"Active\""
                        + "}"
                    + "}]"
                + "}";

        NamespaceList list = Utils.fromJson(data, NamespaceList.class);
        assertNotNull(list);
        /*assertNotNull(list.kind);
        assertNotNull(list.apiVersion);
        assertNotNull(list.metadata);
        assertNotNull(list.metadata.selfLink);
        assertNotNull(list.metadata.resourceVersion);*/
        assertNotNull(list.items);
    }

    @Test
    public void TestNamespaceSpecRegression() {
        final String data = "{\"finalizers\":[]}";

        NamespaceSpec spec = Utils.fromJson(data, NamespaceSpec.class);
        assertNotNull(spec);
        assertEquals(0, spec.finalizers.size());
    }
}
