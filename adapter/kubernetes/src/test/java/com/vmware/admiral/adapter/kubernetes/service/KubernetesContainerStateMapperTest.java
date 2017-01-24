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

package com.vmware.admiral.adapter.kubernetes.service;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.adapter.kubernetes.service.apiobject.Container;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerPort;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerState;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerStateRunning;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerStateTerminated;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerStateWaiting;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerStatus;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.EnvVar;
import com.vmware.admiral.compute.container.ContainerService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.PortBinding;

public class KubernetesContainerStateMapperTest {
    private KubernetesContainerStateMapper mapper = new KubernetesContainerStateMapper();

    @Test
    public void TestCorrectContainerIdExtract() {
        String realId = "some-test-id-83f80ae29bc734";
        String id = "docker://" + realId;
        String extracted = KubernetesContainerStateMapper.getId(id);
        Assert.assertEquals(realId, extracted);
    }

    @Test
    public void TestGetIdWithoutPrefix() {
        String inID = "some-random-id";
        String id = KubernetesContainerStateMapper.getId(inID);
        Assert.assertEquals(inID, id);
    }

    @Test
    public void TestCorrectEnvMap() {
        EnvVar e = new EnvVar();
        e.name = "name";
        e.value = "val";
        String env = KubernetesContainerStateMapper.makeEnv(e);
        Assert.assertEquals(e.name + "=" + e.value, env);
    }

    @Test
    public void TestCorrectPortMap() {
        ContainerPort inPort = new ContainerPort();
        inPort.hostIP = "127.0.0.1";
        inPort.hostPort = 321;
        inPort.containerPort = 123;
        inPort.protocol = "udp";
        PortBinding outPort = KubernetesContainerStateMapper.makePort(inPort);
        Assert.assertEquals(inPort.protocol, outPort.protocol);
        Assert.assertEquals(inPort.hostIP, outPort.hostIp);
        Assert.assertEquals(Integer.toString(inPort.hostPort), outPort.hostPort);
        Assert.assertEquals(Integer.toString(inPort.containerPort), outPort.containerPort);
    }

    @Test
    public void TestCorrectMapContainer() {
        ContainerService.ContainerState outState = new ContainerService.ContainerState();
        Container inContainer = new Container();
        ContainerStatus inStatus = new ContainerStatus();

        inContainer.name = "test-name";
        inContainer.image = "test-image";
        inContainer.command = new ArrayList<>();
        inContainer.command.add("cmd1");
        inContainer.command.add("cmd2");
        EnvVar e1 = new EnvVar() {
            {
                name = "name1";
                value = "val1";
            }
        };
        EnvVar e2 = new EnvVar() {
            {
                name = "name2";
                value = "val2";
            }
        };
        inContainer.env = new ArrayList<>();
        inContainer.env.add(e1);
        inContainer.env.add(e2);
        ContainerPort port1 = new ContainerPort() {
            {
                name = "portName1";
                protocol = "tcp";
                hostIP = "192.168.0.1";
                hostPort = 8080;
                containerPort = 80;
            }
        };
        ContainerPort port2 = new ContainerPort() {
            {
                name = "portName2";
                protocol = "tcp";
                hostIP = "192.168.0.2";
                hostPort = 8081;
                containerPort = 81;
            }
        };
        inContainer.ports = new ArrayList<>();
        inContainer.ports.add(port1);
        inContainer.ports.add(port2);

        String id = "test-id-123";
        inStatus.containerID = "docker://" + id;
        inStatus.state = new ContainerState();
        inStatus.state.running = new ContainerStateRunning();

        KubernetesContainerStateMapper.mapContainer(outState, inContainer, inStatus);

        Assert.assertEquals(inContainer.name, outState.name);
        Assert.assertNotNull(outState.names);
        Assert.assertEquals(inContainer.name, outState.names.get(0));
        Assert.assertEquals(inContainer.image, outState.image);
        Assert.assertNotNull(outState.command);
        Assert.assertEquals(inContainer.command.size(), outState.command.length);
        for (int i = 0; i < inContainer.command.size(); i++) {
            Assert.assertEquals(inContainer.command.get(i), outState.command[i]);
        }
        Assert.assertNotNull(outState.env);
        for (int i = 0; i < inContainer.env.size(); i++) {
            EnvVar envVar = inContainer.env.get(i);
            Assert.assertEquals(envVar.name + "=" + envVar.value, outState.env[i]);
        }
        Assert.assertNotNull(outState.ports);
        Assert.assertEquals(inContainer.ports.size(), outState.ports.size());
        for (int i = 0; i < inContainer.ports.size(); i++) {
            ContainerPort inPort = inContainer.ports.get(i);
            PortBinding outPort = outState.ports.get(i);
            Assert.assertEquals(inPort.protocol, outPort.protocol);
            Assert.assertEquals(inPort.hostIP, outPort.hostIp);
            Assert.assertEquals(Integer.toString(inPort.hostPort), outPort.hostPort);
            Assert.assertEquals(Integer.toString(inPort.containerPort), outPort.containerPort);
        }
        Assert.assertEquals(PowerState.RUNNING, outState.powerState);
    }

    @Test
    public void TestMapContainerWithNullInputContainer() {
        ContainerService.ContainerState outState = new ContainerService.ContainerState();
        ContainerStatus status = new ContainerStatus();
        status.containerID = "docker://test-id";
        status.state = new ContainerState();
        KubernetesContainerStateMapper.mapContainer(outState, null, status);
        Assert.assertNull(outState.id);
        Assert.assertNull(outState.powerState);
    }

    @Test
    public void TestMapContainerWithNullInputStatus() {
        ContainerService.ContainerState outState = new ContainerService.ContainerState();
        Container inContainer = new Container();
        inContainer.name = "test";
        inContainer.image = "image";
        KubernetesContainerStateMapper.mapContainer(outState, inContainer, null);
        Assert.assertNull(outState.name);
        Assert.assertNull(outState.names);
        Assert.assertNull(outState.id);
        Assert.assertNull(outState.image);
        Assert.assertNull(outState.command);
        Assert.assertNull(outState.env);
        Assert.assertNull(outState.ports);
        Assert.assertNull(outState.powerState);
    }

    @Test
    public void TestGetPowerStateRunning() {
        ContainerStatus status = new ContainerStatus();
        status.state = new ContainerState();
        status.state.running = new ContainerStateRunning();
        PowerState state = KubernetesContainerStateMapper.getPowerState(status);
        Assert.assertEquals(PowerState.RUNNING, state);
    }

    @Test
    public void TestGetPowerStatePaused() {
        ContainerStatus status = new ContainerStatus();
        status.state = new ContainerState();
        status.state.waiting = new ContainerStateWaiting();
        PowerState state = KubernetesContainerStateMapper.getPowerState(status);
        Assert.assertEquals(PowerState.PAUSED, state);
    }

    @Test
    public void TestGetPowerStateStopped() {
        ContainerStatus status = new ContainerStatus();
        status.state = new ContainerState();
        status.state.terminated = new ContainerStateTerminated();
        PowerState state = KubernetesContainerStateMapper.getPowerState(status);
        Assert.assertEquals(PowerState.STOPPED, state);
    }

    @Test
    public void TestGetPowerStateUnknown() {
        ContainerStatus status = new ContainerStatus();
        status.state = new ContainerState();
        PowerState state = KubernetesContainerStateMapper.getPowerState(status);
        Assert.assertEquals(PowerState.UNKNOWN, state);
    }

    @Test
    public void TestGetPowerStateWithNullStatus() {
        ContainerStatus status = null;
        PowerState state = KubernetesContainerStateMapper.getPowerState(status);
        Assert.assertEquals(PowerState.UNKNOWN, state);
    }

    @Test
    public void TestCorrectDateParse() {
        KubernetesContainerStateMapper.parseDate("2017-01-09T10:43:32Z");
    }

    @Test(expected = IllegalArgumentException.class)
    public void TestInvalidDateParse() {
        KubernetesContainerStateMapper.parseDate("2017-01-09 10:43:32");
    }

    @Test(expected = IllegalArgumentException.class)
    public void TestParseDateWithNullString() {
        KubernetesContainerStateMapper.parseDate(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void TestParseDateWithEmptyString() {
        KubernetesContainerStateMapper.parseDate("");
    }
}
