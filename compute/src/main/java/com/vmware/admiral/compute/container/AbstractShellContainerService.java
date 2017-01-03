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

package com.vmware.admiral.compute.container;

import java.net.URI;
import java.util.function.Consumer;

import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.util.ContainerUtil;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;

public class AbstractShellContainerService extends StatelessService {

    protected void loadContainerShellURI(String hostLink, Operation op, Consumer<URI> callback) {
        loadContainerShellURI(hostLink, null, null, op, callback);
    }

    private void loadContainerShellURI(String hostLink, ComputeService.ComputeState host,
            ContainerState shellContainer, Operation op, Consumer<URI> callback) {

        if (host == null) {
            sendRequest(Operation.createGet(this, hostLink).setCompletion((o, e) -> {
                if (e != null) {
                    op.fail(e);
                    return;
                }
                ComputeService.ComputeState computeState = o
                        .getBody(ComputeService.ComputeState.class);
                loadContainerShellURI(hostLink, computeState, shellContainer, op,
                        callback);
            }));
            return;
        }

        if (shellContainer == null) {
            String shellContainerLink = SystemContainerDescriptions.getSystemContainerSelfLink(
                    SystemContainerDescriptions.AGENT_CONTAINER_NAME, Service.getId(hostLink));
            sendRequest(Operation.createGet(this, shellContainerLink).setCompletion((o, e) -> {
                if (e != null) {
                    op.fail(e);
                    return;
                }
                ContainerService.ContainerState containerState = o
                        .getBody(ContainerService.ContainerState.class);
                loadContainerShellURI(hostLink, host, containerState, op, callback);
            }));
            return;
        }

        URI shellUri;
        try {
            shellUri = ContainerUtil.getShellUri(host, shellContainer);
        } catch (Exception e) {
            op.fail(e);
            return;
        }

        callback.accept(shellUri);
    }

}
