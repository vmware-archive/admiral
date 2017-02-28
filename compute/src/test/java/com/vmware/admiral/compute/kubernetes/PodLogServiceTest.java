/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.entities.pods.Container;
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodSpec;
import com.vmware.admiral.compute.kubernetes.service.PodLogService;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class PodLogServiceTest extends ComputeBaseTest {
    private List<String> documentLinksForDeletion;

    @Before
    public void setUp() throws Throwable {
        documentLinksForDeletion = new ArrayList<>();
        waitForServiceAvailability(LogService.FACTORY_LINK);
        waitForServiceAvailability(PodService.FACTORY_LINK);
        waitForServiceAvailability(PodLogService.SELF_LINK);

    }

    @After
    public void tearDown() throws Throwable {
        for (String selfLink : documentLinksForDeletion) {
            delete(selfLink);
        }
    }

    @Test
    public void testLog() throws Throwable {
        PodState podState = createPodState();
        createLogStates(podState);

        getPodLogs(podState, (logsMap) -> {
            assertNotNull(logsMap);
            assertEquals(3, logsMap.size());
            for (int i = 0; i < podState.pod.spec.containers.size(); i++) {
                Container temp = podState.pod.spec.containers.get(i);
                assertTrue(logsMap.containsKey(temp.name));
                assertEquals("test-log-" + i,
                        new String(logsMap.get(temp.name).logs));
            }

        });
    }

    private void getPodLogs(PodState podState, Consumer<Map<String, LogServiceState>> callback)
            throws Throwable {

        host.testStart(1);
        host.send(Operation.createGet(
                UriUtils.buildUri(host, PodLogService.SELF_LINK,
                        PodLogService.POD_ID_QUERY_PARAM + "=" + extractId(
                                podState.documentSelfLink)))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    try {
                        Map<String, Object> tempMap = o.getBody(Map.class);
                        Map<String, LogServiceState> logsMap = new HashMap<>();

                        for (Entry<String, Object> entry : tempMap.entrySet()) {
                            Object obj = entry.getValue();
                            LogServiceState logState = Utils.fromJson(Utils.toJson(obj),
                                    LogServiceState.class);
                            logsMap.put(entry.getKey(), logState);
                        }
                        callback.accept(logsMap);
                        host.completeIteration();
                    } catch (Throwable ex) {
                        host.failIteration(ex);
                    }

                }));
        host.testWait();

    }

    private PodState createPodState() throws Throwable {
        PodState podState = new PodState();
        podState.pod = new Pod();
        podState.pod.spec = new PodSpec();
        podState.pod.spec.containers = new ArrayList<>();
        Container container1 = new Container();
        container1.name = "container1";
        Container container2 = new Container();
        container2.name = "container2";
        Container container3 = new Container();
        container3.name = "container3";
        podState.pod.spec.containers.add(container1);
        podState.pod.spec.containers.add(container2);
        podState.pod.spec.containers.add(container3);
        podState.pod.metadata = new ObjectMeta();
        podState.pod.metadata.selfLink = "/api/v1/namespaces/default/pods/test-pod";
        podState = doPost(podState, PodService.FACTORY_LINK);
        documentLinksForDeletion.add(podState.documentSelfLink);
        return podState;
    }

    private void createLogStates(PodState podState) throws Throwable {
        for (int i = 0; i < podState.pod.spec.containers.size(); i++) {
            LogServiceState logState = new LogServiceState();
            logState.logs = ("test-log-" + i).getBytes();
            logState.documentSelfLink = podState.documentSelfLink + "-" + podState.pod.spec
                    .containers.get(i).name;
            logState = doPost(logState, LogService.FACTORY_LINK);
            documentLinksForDeletion.add(logState.documentSelfLink);
        }
    }

}
