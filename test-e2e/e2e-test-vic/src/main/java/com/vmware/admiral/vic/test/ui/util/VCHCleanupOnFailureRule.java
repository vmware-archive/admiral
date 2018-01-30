/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.vic.test.ui.util;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.DockerClient.ListNetworksParam;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.Network;
import com.spotify.docker.client.messages.Volume;
import com.spotify.docker.client.messages.VolumeList;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.vmware.admiral.test.util.DockerUtils;

public class VCHCleanupOnFailureRule extends TestWatcher {

    private final String hostUrl;

    private final Logger LOG = Logger.getLogger(getClass().getName());

    public VCHCleanupOnFailureRule(String hostUrl) {
        this.hostUrl = hostUrl;
    }

    @Override
    protected void failed(Throwable e, Description description) {
        LOG.info("Deleting content from host: " + hostUrl);
        try {
            DockerClient docker = DockerUtils.createUnsecureDockerClient(hostUrl);
            for (Container container : docker.listContainers(ListContainersParam.allContainers())) {
                docker.removeContainer(container.id(), RemoveContainerParam.forceKill(),
                        RemoveContainerParam.removeVolumes());
            }
            List<Network> networks = docker.listNetworks(ListNetworksParam.customNetworks());
            if (Objects.nonNull(networks) && !networks.isEmpty()) {
                for (Network network : networks) {
                    docker.removeNetwork(network.id());
                }
            }
            VolumeList volumes = docker.listVolumes();
            if (Objects.nonNull(volumes) && Objects.nonNull(volumes.volumes())
                    && !volumes.volumes().isEmpty()) {
                for (Volume volume : volumes.volumes()) {
                    docker.removeVolume(volume.name());
                }
            }
            // for (Image image : docker.listImages(ListImagesParam.allImages())) {
            // docker.removeImage(image.id(), true, false);
            // }
            docker.close();
        } catch (InterruptedException | DockerException ex) {
            LOG.log(Level.SEVERE, "Could not delete the content in the host: ", e);
        }
    }

}
