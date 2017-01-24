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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;

import com.vmware.admiral.adapter.kubernetes.service.apiobject.Container;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerPort;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerStatus;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.EnvVar;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.PortBinding;

public class KubernetesContainerStateMapper {
    public static String makeEnv(EnvVar env) {
        return env.name + "=" + env.value;
    }

    /**
     * Change kubernetes container id
     * @param id The kubernetes container id is in the form 'docker://<container-id>'
     * @return
     */
    public static String getId(String id) {
        if (id.startsWith("docker://")) {
            return id.substring("docker://".length());
        }
        return id;
    }

    public static PortBinding makePort(ContainerPort port) {
        PortBinding result = new PortBinding();

        result.containerPort = Integer.toString(port.containerPort);
        result.hostPort = Integer.toString(port.hostPort);
        result.hostIp = port.hostIP;
        result.protocol = port.protocol;

        return result;
    }

    public static void mapContainer(ContainerState outContainerState, Container inContainer,
            ContainerStatus status) {
        if (outContainerState == null || inContainer == null || status == null) {
            return;
        }
        outContainerState.id = getId(status.containerID);
        outContainerState.name = inContainer.name;
        outContainerState.names = Arrays.asList(inContainer.name);
        outContainerState.image = inContainer.image;
        if (inContainer.command != null) {
            outContainerState.command = inContainer.command.toArray(
                    new String[inContainer.command.size()]);
        }

        if (inContainer.env != null) {
            outContainerState.env = new String[inContainer.env.size()];
            for (int i = 0; i < outContainerState.env.length; ++i) {
                outContainerState.env[i] = makeEnv(inContainer.env.get(i));
            }
        }
        if (inContainer.ports != null) {
            outContainerState.ports = new ArrayList<>(inContainer.ports.size());
            for (int i = 0; i < inContainer.ports.size(); ++i) {
                outContainerState.ports.add(makePort(inContainer.ports.get(i)));
            }
        }

        outContainerState.powerState = getPowerState(status);
    }

    public static PowerState getPowerState(ContainerStatus status) {
        if (status == null || status.state == null) {
            return PowerState.UNKNOWN;
        }
        // NOTE: this power state will not be changeable by the user
        if (status.state.running != null) {
            return PowerState.RUNNING;
        } else if (status.state.waiting != null) {
            return PowerState.PAUSED;
        } else if (status.state.terminated != null) {
            return PowerState.STOPPED;
        } else {
            return PowerState.UNKNOWN;
        }
    }

    public static long parseDate(String value) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        try {
            return sdf.parse(value).getTime();
        } catch (ParseException | NullPointerException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
