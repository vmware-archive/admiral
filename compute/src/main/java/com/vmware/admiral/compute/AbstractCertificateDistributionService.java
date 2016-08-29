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

package com.vmware.admiral.compute;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.ShellContainerExecutorService.ShellContainerExecutorState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Service for distribution of self-signed trusted registry certificates to docker hosts.
 * See: https://docs.docker.com/docker-trusted-registry/userguide/
 */
public class AbstractCertificateDistributionService extends StatelessService {
    public static final int MAX_RETRIES = Integer.getInteger(
            "cmp.management.query.certificatedistribution.maxRetry", 3);
    public static final long QUERY_RETRIEVAL_RETRY_INTERVAL_SECONDS = Integer.getInteger(
            "cmp.management.query.certificatedistribution.maxRetryIntervalSec", 5);

    protected void uploadCertificate(String hostLink, String dirName, String certificate) {
        OperationUtil.getDocumentState(this, hostLink, ComputeState.class,
                (ComputeState host) -> {
                    if (ContainerHostUtil.isVicHost(host)) {
                        logInfo("Skip installing certificate for VIC host [%s]", hostLink);
                        return;
                    }
                    ShellContainerExecutorState execState = new ShellContainerExecutorState();
                    execState.command = new String[]
                            { "sh", "/copy-certificate.sh", dirName, certificate };

                    try {
                        processUploadCertificateQuery(execState, hostLink, MAX_RETRIES);
                    } catch (Throwable t) {
                        logSevere("Fail to upload registry certificate to host %s: "
                                + "failed to connect to shell container executor service.",
                                hostLink);
                    }
                });
    }

    protected void processUploadCertificateQuery(ShellContainerExecutorState execState,
            String hostLink, int retries) {
        Operation post = Operation.createPost(this, ShellContainerExecutorService.SELF_LINK);
        post.setUri(UriUtils.appendQueryParam(post.getUri(),
                ShellContainerExecutorService.HOST_LINK_URI_PARAM, hostLink));

        sendRequest(post.setBody(execState).setCompletion((o, ex) -> {
            if (ex != null) {
                logSevere("Failed to upload registry certificate to host %s: %s",
                        hostLink, Utils.toString(ex));
                if (retries > 0) {
                    getHost().schedule(() -> {
                        processUploadCertificateQuery(execState, hostLink, retries - 1);
                    }, QUERY_RETRIEVAL_RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
                } else {
                    logSevere("Failed to upload registry certificate to host %s after %s attempts. "
                            + "Suspending host.", hostLink, MAX_RETRIES);
                    suspendHost(hostLink);
                }
            } else {
                logInfo("Registry certificate successfully uploaded to host %s", hostLink);
                log(Level.FINEST, () -> String.format(
                        "Command result (possibly truncated):\n---\n%1.1024s\n---\n",
                        o.getBody(String.class)));
            }
        }));
    }

    protected void suspendHost(String hostLink) {
        ComputeState computeState = new ComputeState();
        computeState.powerState = PowerState.SUSPEND;
        sendRequest(Operation.createPatch(this, hostLink)
                .setBody(computeState));
    }

    protected String getCertificateDirName(String registryAddress) {
        // certificates are stored under /etc/docker/certs.d/{registryAddress}/
        return UriUtilsExtended.extractHostAndPort(registryAddress);
    }
}
