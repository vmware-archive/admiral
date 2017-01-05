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

package com.vmware.admiral.request.compute;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.CommonContinuousQueries;
import com.vmware.admiral.compute.CommonContinuousQueries.ContinuousQueryId;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService.ComputeRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A stateless service that monitors for computes going to a RETIRED state and initiates their
 * graceful removal through {@link ComputeRemovalTaskService}.
 */
public class ComputeRemovalWatchService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.COMPUTE_REMOVAL_WATCH;

    public ComputeRemovalWatchService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        startPost.complete();
        CommonContinuousQueries.subscribeTo(this.getHost(), ContinuousQueryId.RETIRED_COMPUTES,
                this::onRetiredComputeChange);
    }

    public void onRetiredComputeChange(Operation op) {
        op.complete();
        QueryTask queryTask = op.getBody(QueryTask.class);
        if (queryTask.results != null && queryTask.results.documentLinks != null
                && !queryTask.results.documentLinks.isEmpty()) {
            logInfo("Retired compute found, triggering removal: %s",
                    String.join(", ", queryTask.results.documentLinks));
            triggerComputeRemoval(queryTask.results.documentLinks);
        }
    }

    private void triggerComputeRemoval(Collection<String> computeLinks) {
        for (String computeLink : computeLinks) {
            ComputeRemovalTaskState computeRemovalTask = new ComputeRemovalTaskState();
            computeRemovalTask.resourceLinks = Collections.singleton(computeLink);
            computeRemovalTask.documentSelfLink = UriUtils.getLastPathSegment(computeLink);

            // local changes only as the compute is considered unavailable on the remote site
            computeRemovalTask.resourceRemovalOptions = EnumSet.of(TaskOption.DOCUMENT_CHANGES_ONLY);

            sendRequest(Operation.createPost(this, ComputeRemovalTaskService.FACTORY_LINK)
                    .setBody(computeRemovalTask)
                    .setCompletion((o, e) -> {
                        if (e != null && o.getStatusCode() != Operation.STATUS_CODE_CONFLICT) {
                            logSevere("Couldn't start a compute removal task for %s: %s",
                                    computeLink, e.getMessage());
                        }
                    }));
        }
    }
}
