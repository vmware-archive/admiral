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

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainer;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerEnvVar;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerPort;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerState;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerStateRunning;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerStateTerminated;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerStateWaiting;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerStatus;

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
        PodContainerEnvVar e = new PodContainerEnvVar();
        e.name = "name";
        e.value = "val";
        String env = KubernetesContainerStateMapper.makeEnv(e);
        Assert.assertEquals(e.name + "=" + e.value, env);
    }

    @Test
    public void TestCorrectPortMap() {
        PodContainerPort inPort = new PodContainerPort();
        inPort.hostIp = "127.0.0.1";
        inPort.hostPort = 321;
        inPort.containerPort = 123;
        inPort.protocol = "udp";
        PortBinding outPort = KubernetesContainerStateMapper.makePort(inPort);
        Assert.assertEquals(inPort.protocol, outPort.protocol);
        Assert.assertEquals(inPort.hostIp, outPort.hostIp);
        Assert.assertEquals(Integer.toString(inPort.hostPort), outPort.hostPort);
        Assert.assertEquals(Integer.toString(inPort.containerPort), outPort.containerPort);
    }

    @Test
    public void TestCorrectMapContainer() {
        ContainerState outState = new ContainerState();
        PodContainer inContainer = new PodContainer();
        PodContainerStatus inStatus = new PodContainerStatus();

        inContainer.name = "test-name";
        inContainer.image = "test-image";
        inContainer.command = new String[] {"cmd1", "cmd2"};
        PodContainerEnvVar e1 = new PodContainerEnvVar() {
            {
                name = "name1";
                value = "val1";
            }
        };
        PodContainerEnvVar e2 = new PodContainerEnvVar() {
            {
                name = "name2";
                value = "val2";
            }
        };
        inContainer.env = new PodContainerEnvVar[] {e1, e2};
        PodContainerPort port1 = new PodContainerPort() {
            {
                name = "portName1";
                protocol = "tcp";
                hostIp = "192.168.0.1";
                hostPort = 8080;
                containerPort = 80;
            }
        };
        PodContainerPort port2 = new PodContainerPort() {
            {
                name = "portName2";
                protocol = "tcp";
                hostIp = "192.168.0.2";
                hostPort = 8081;
                containerPort = 81;
            }
        };
        inContainer.ports = new PodContainerPort[] {port1, port2};

        String id = "test-id-123";
        inStatus.containerID = "docker://" + id;
        inStatus.state = new PodContainerState();
        inStatus.state.running = new PodContainerStateRunning();

        KubernetesContainerStateMapper.mapContainer(outState, inContainer, inStatus);

        Assert.assertEquals(inContainer.name, outState.name);
        Assert.assertNotNull(outState.names);
        Assert.assertEquals(inContainer.name, outState.names.get(0));
        Assert.assertEquals(inContainer.image, outState.image);
        Assert.assertNotNull(outState.command);
        Assert.assertEquals(inContainer.command.length, outState.command.length);
        for (int i = 0; i < inContainer.command.length; i++) {
            Assert.assertEquals(inContainer.command[i], outState.command[i]);
        }
        Assert.assertNotNull(outState.env);
        for (int i = 0; i < inContainer.env.length; i++) {
            PodContainerEnvVar envVar = inContainer.env[i];
            Assert.assertEquals(envVar.name + "=" + envVar.value, outState.env[i]);
        }
        Assert.assertNotNull(outState.ports);
        Assert.assertEquals(inContainer.ports.length, outState.ports.size());
        for (int i = 0; i < inContainer.ports.length; i++) {
            PodContainerPort inPort = inContainer.ports[i];
            PortBinding outPort = outState.ports.get(i);
            Assert.assertEquals(inPort.protocol, outPort.protocol);
            Assert.assertEquals(inPort.hostIp, outPort.hostIp);
            Assert.assertEquals(Integer.toString(inPort.hostPort), outPort.hostPort);
            Assert.assertEquals(Integer.toString(inPort.containerPort), outPort.containerPort);
        }
        Assert.assertEquals(PowerState.RUNNING, outState.powerState);
    }

    @Test
    public void TestMapContainerWithNullInputContainer() {
        ContainerState outState = new ContainerState();
        PodContainerStatus status = new PodContainerStatus();
        status.containerID = "docker://test-id";
        status.state = new PodContainerState();
        KubernetesContainerStateMapper.mapContainer(outState, null, status);
        Assert.assertNull(outState.id);
        Assert.assertNull(outState.powerState);
    }

    @Test
    public void TestMapContainerWithNullInputStatus() {
        ContainerState outState = new ContainerState();
        PodContainer inContainer = new PodContainer();
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
        PodContainerStatus status = new PodContainerStatus();
        status.state = new PodContainerState();
        status.state.running = new PodContainerStateRunning();
        PowerState state = KubernetesContainerStateMapper.getPowerState(status);
        Assert.assertEquals(PowerState.RUNNING, state);
    }

    @Test
    public void TestGetPowerStatePaused() {
        PodContainerStatus status = new PodContainerStatus();
        status.state = new PodContainerState();
        status.state.waiting = new PodContainerStateWaiting();
        PowerState state = KubernetesContainerStateMapper.getPowerState(status);
        Assert.assertEquals(PowerState.PAUSED, state);
    }

    @Test
    public void TestGetPowerStateStopped() {
        PodContainerStatus status = new PodContainerStatus();
        status.state = new PodContainerState();
        status.state.terminated = new PodContainerStateTerminated();
        PowerState state = KubernetesContainerStateMapper.getPowerState(status);
        Assert.assertEquals(PowerState.STOPPED, state);
    }

    @Test
    public void TestGetPowerStateUnknown() {
        PodContainerStatus status = new PodContainerStatus();
        status.state = new PodContainerState();
        PowerState state = KubernetesContainerStateMapper.getPowerState(status);
        Assert.assertEquals(PowerState.UNKNOWN, state);
    }

    @Test
    public void TestGetPowerStateWithNullStatus() {
        PodContainerStatus status = null;
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

    @Test
    public void TestCorrectParseCPUm() {
        float r = KubernetesContainerStateMapper.parseCPU("549m");
        Assert.assertEquals(0.549F, r, 1e-6F);
    }

    @Test
    public void TestCorrectParseCPU() {
        float r = KubernetesContainerStateMapper.parseCPU("1.43");
        Assert.assertEquals(1.43F, r, 1e-6F);
    }

    @Test
    public void TestInvalidParseCPU() {
        float r = KubernetesContainerStateMapper.parseCPU("m933m");
        Assert.assertEquals(0F, r, 1e-6F);
    }

    @Test
    public void TestParseCPUWithEmptyString() {
        float r = KubernetesContainerStateMapper.parseCPU("");
        Assert.assertEquals(0F, r, 1e-6F);
    }

    @Test
    public void TestParseCPUWithNull() {
        float r = KubernetesContainerStateMapper.parseCPU(null);
        Assert.assertEquals(0F, r, 1e-6F);
    }

    @Test
    public void TestCorrectParseMem() {
        long r = KubernetesContainerStateMapper.parseMem("4000");
        Assert.assertEquals(4000L, r);
    }

    @Test
    public void TestCorrectParseMemK() {
        long r = KubernetesContainerStateMapper.parseMem("4K");
        Assert.assertEquals(4000L, r);
    }

    @Test
    public void TestCorrectParseMemKi() {
        long r = KubernetesContainerStateMapper.parseMem("4Ki");
        Assert.assertEquals(4096L, r);
    }

    @Test
    public void TestCorrectParseMemM() {
        long r = KubernetesContainerStateMapper.parseMem("4M");
        Assert.assertEquals(4_000_000L, r);
    }

    @Test
    public void TestCorrectParseMemMi() {
        long r = KubernetesContainerStateMapper.parseMem("4Mi");
        Assert.assertEquals(4L * 1024L * 1024L, r);
    }

    @Test
    public void TestCorrectParseMemG() {
        long r = KubernetesContainerStateMapper.parseMem("4G");
        Assert.assertEquals(4_000_000_000L, r);
    }

    @Test
    public void TestCorrectParseMemGi() {
        long r = KubernetesContainerStateMapper.parseMem("4Gi");
        Assert.assertEquals(4L * 1024L * 1024L * 1024L, r);
    }

    @Test
    public void TestCorrectParseMemT() {
        long r = KubernetesContainerStateMapper.parseMem("4T");
        Assert.assertEquals(4_000_000_000_000L, r);
    }

    @Test
    public void TestCorrectParseMemTi() {
        long r = KubernetesContainerStateMapper.parseMem("4Ti");
        Assert.assertEquals(4L * 1024L * 1024L * 1024L * 1024L, r);
    }

    @Test
    public void TestCorrectParseMemP() {
        long r = KubernetesContainerStateMapper.parseMem("4P");
        Assert.assertEquals(4_000_000_000_000_000L, r);
    }

    @Test
    public void TestCorrectParseMemPi() {
        long r = KubernetesContainerStateMapper.parseMem("4Pi");
        Assert.assertEquals(4L * 1024L * 1024L * 1024L * 1024L * 1024L, r);
    }

    @Test
    public void TestInvalidParseMem() {
        long r = KubernetesContainerStateMapper.parseMem("4PiP");
        Assert.assertEquals(0L, r);
    }

    @Test
    public void TestParseMemWithEmptyString() {
        long r = KubernetesContainerStateMapper.parseMem("");
        Assert.assertEquals(0L, r);
    }

    @Test
    public void TestParseMemWithNull() {
        long r = KubernetesContainerStateMapper.parseMem(null);
        Assert.assertEquals(0L, r);
    }
}
