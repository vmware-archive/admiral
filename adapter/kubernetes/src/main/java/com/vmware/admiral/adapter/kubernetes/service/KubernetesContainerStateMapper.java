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

package com.vmware.admiral.adapter.kubernetes.service;

import java.util.ArrayList;
import java.util.Arrays;

import com.vmware.admiral.adapter.kubernetes.service.apiobject.Container;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerPort;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.EnvVar;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;

public class KubernetesContainerStateMapper {
    private static String makeEnv(EnvVar env) {
        return env.name + "=" + env.value;
    }

    private static PortBinding makePort(ContainerPort port) {
        PortBinding result = new PortBinding();

        result.containerPort = Integer.toString(port.containerPort);
        result.hostPort = Integer.toString(port.hostPort);
        result.hostIp = port.hostIP;
        result.protocol = port.protocol;

        return result;
    }

    public static void mapContainer(ContainerState outContainerState, Container inContainer, String id) {
        outContainerState.id = id;
        outContainerState.name = inContainer.name;
        outContainerState.names = Arrays.asList(inContainer.name);
        outContainerState.image = inContainer.image;
        outContainerState.command = inContainer.command;

        if (inContainer.env != null) {
            outContainerState.env = new String[inContainer.env.length];
            for (int i = 0; i < inContainer.env.length; ++i) {
                outContainerState.env[i] = makeEnv(inContainer.env[i]);
            }
        }
        if (inContainer.ports != null) {
            outContainerState.ports = new ArrayList<>(inContainer.ports.length);
            for (int i = 0; i < inContainer.ports.length; ++i) {
                outContainerState.ports.add(makePort(inContainer.ports[i]));
            }
        }
    }
}
