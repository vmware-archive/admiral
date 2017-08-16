/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.ShellContainerExecutorService.ShellContainerExecutorResult;
import com.vmware.admiral.compute.container.ShellContainerExecutorService.ShellContainerExecutorState;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
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

    protected void uploadCertificate(String hostLink, String registryAddress, String certificate,
            List<String> tenantLinks) {

        OperationUtil.getDocumentState(this, hostLink, ComputeState.class,
                (ComputeState host) -> {
                    if (ContainerHostUtil.isVicHost(host)) {
                        logInfo("Skip installing certificate for VIC host [%s]", hostLink);
                        return;
                    }
                    ShellContainerExecutorState execState = new ShellContainerExecutorState();
                    execState.command = new String[] { "sh", "/copy-certificate.sh",
                            getCertificateDirName(registryAddress), certificate };

                    try {
                        processUploadCertificateQuery(execState, hostLink, MAX_RETRIES,
                                registryAddress, tenantLinks);
                    } catch (Throwable t) {
                        logSevere("Fail to upload registry certificate to host %s: "
                                + "failed to connect to shell container executor service.",
                                hostLink);
                    }
                });
    }

    protected void processUploadCertificateQuery(ShellContainerExecutorState execState,
            String hostLink, int retries, String registryAddress, List<String> tenantLinks) {

        logInfo("Uploading certificate on %s for registry %s. Retries %s", hostLink,
                getCertificateDirName(registryAddress), retries);

        Operation post = Operation.createPost(this, ShellContainerExecutorService.SELF_LINK);
        post.setUri(UriUtils.appendQueryParam(post.getUri(),
                ShellContainerExecutorService.HOST_LINK_URI_PARAM, hostLink));

        sendRequest(post.setBody(execState).setCompletion((o, ex) -> {
            if (ex != null) {
                logSevere("Failed to upload registry certificate to host %s: %s",
                        hostLink, Utils.toString(ex));
                if (retries > 0) {
                    getHost().schedule(() -> {
                        processUploadCertificateQuery(execState, hostLink, retries - 1,
                                registryAddress, tenantLinks);
                    }, QUERY_RETRIEVAL_RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
                } else {
                    String errMsg = "Failed to upload registry certificate for [%s] to host [%s]"
                            + " after %s attempts. Your host may experience issues connecting to"
                            + " this registry. For more info see:"
                            + " https://docs.docker.com/registry/insecure/#/using-self-signed-certificates";
                    logSevere(errMsg, registryAddress, hostLink, MAX_RETRIES);
                    publishEventLog(String.format(errMsg, registryAddress, hostLink, MAX_RETRIES),
                            tenantLinks);
                }
            } else {
                ShellContainerExecutorResult result = o.getBody(ShellContainerExecutorResult.class);
                logInfo("Registry certificate successfully uploaded to host %s for registry %s."
                                + " Exit code: %s",
                        hostLink, getCertificateDirName(registryAddress), result.exitCode);
                log(Level.FINEST, "Command result (possibly truncated):\n---\n%1.1024s\n---\n",
                        result.output);
            }
        }));
    }

    protected void publishEventLog(String errMsg, List<String> tenantLinks) {
        EventLogState eventLog = new EventLogState();
        eventLog.description = errMsg;
        eventLog.eventLogType = EventLogType.WARNING;
        eventLog.resourceType = getClass().getName();
        eventLog.tenantLinks = tenantLinks;

        sendRequest(Operation.createPost(this, EventLogService.FACTORY_LINK)
                .setBody(eventLog)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failed to create event log: %s", Utils.toString(e));
                    }
                }));
    }

    protected String getCertificateDirName(String registryAddress) {
        // certificates are stored under /etc/docker/certs.d/{registryAddress}/
        return UriUtilsExtended.extractHostAndPort(registryAddress);
    }
}
