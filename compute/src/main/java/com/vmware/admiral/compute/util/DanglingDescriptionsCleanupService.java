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

package com.vmware.admiral.compute.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.util.ResourceDescriptionUtil;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

/**
 * The service is executed during upgrade or can be manually called to cleanup all leftover resource
 * descriptions that are not referenced by any resource state.
 */
public class DanglingDescriptionsCleanupService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.DANGLING_DESCRIPTIONS_CLEANUP;

    @Override
    public void handlePost(Operation post) {

        logInfo("Dangling descriptions cleanup started...");

        getResourceDescriptions(
                ContainerDescription.class,
                ContainerNetworkDescription.class,
                ContainerVolumeDescription.class,
                CompositeDescription.class)
                        .thenCompose(this::deleteResourceDescriptions)
                        .whenComplete((ignore, ex) -> {
                            if (ex != null) {
                                logSevere("Dangling descriptions cleanup failed: %s",
                                        Utils.toString(ex));
                                post.fail(ex);
                            }

                            logInfo("Dangling descriptions cleanup completed successfully");
                            post.complete();
                        });
    }

    private DeferredResult<? extends Collection<String>> getResourceDescriptions(
            Class<?>... resourceClasses) {

        if (resourceClasses == null) {
            logWarning("No resource descriptions found: no resource classes"
                    + " are specified to look for. Skipping cleanup.");
            return DeferredResult.completed(null);
        }

        List<String> documentKinds = Stream.of(resourceClasses)
                .filter(Objects::nonNull)
                .map(Utils::buildKind)
                .collect(Collectors.toList());

        if (documentKinds.isEmpty()) {
            logWarning("List of resource description kinds is empty. Skipping cleanup.");
            return DeferredResult.completed(null);
        }

        QueryTask queryTask = new QueryTask();
        queryTask.querySpec = new QuerySpecification();
        queryTask.taskInfo.isDirect = true;
        QueryUtil.addListValueClause(queryTask, ServiceDocument.FIELD_NAME_KIND, documentKinds);

        ArrayList<String> documentLinks = new ArrayList<>();
        DeferredResult<ArrayList<String>> deferredResult = new DeferredResult<>();

        new ServiceDocumentQuery<>(getHost(), ServiceDocument.class)
                .query(queryTask, r -> {
                    if (r.hasException()) {
                        logSevere("Failed to query resource descriptions: %s",
                                Utils.toString(r.getException()));
                        deferredResult.fail(r.getException());
                    } else if (r.hasResult()) {
                        String documentLink = r.getDocumentSelfLink();
                        logInfo("Found description of resource: %s", documentLink);
                        documentLinks.add(documentLink);
                    } else {
                        deferredResult.complete(documentLinks);
                    }
                });

        return deferredResult;
    }

    private DeferredResult<Void> deleteResourceDescriptions(Collection<String> descriptionLinks) {
        if (descriptionLinks == null || descriptionLinks.isEmpty()) {
            logWarning("List of descriptions to delete is empty. Skipping cleanup.");
            return DeferredResult.completed(null);
        }

        List<DeferredResult<Void>> deferredResults = descriptionLinks.stream()
                .map(link -> {
                    DeferredResult<Void> deferredResult;
                    if (isCompositeDescriptionLink(link)) {
                        logInfo("Cleanup cloned composite description [%s]...", link);
                        deferredResult = ResourceDescriptionUtil
                                .deleteClonedCompositeDescription(getHost(), link);
                    } else {
                        logInfo("Cleanup resource description [%s]...", link);
                        deferredResult = ResourceDescriptionUtil
                                .deleteResourceDescription(getHost(), link);
                    }

                    return deferredResult.exceptionally((ex) -> {
                        Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;

                        // if the cleanup failed because the description was in use or it was not a
                        // cloned composite description, the failure is expected
                        String error = cause.getMessage();
                        if (error.contains(
                                ResourceDescriptionUtil.CANNOT_DELETE_RESOURCE_DESCRIPTION)) {
                            logInfo("Resource description [%s] is in use. Skipping cleanup.", link);
                            return null;
                        }
                        if (error.contains(
                                ResourceDescriptionUtil.CANNOT_DELETE_COMPOSITE_DESCRIPTION)) {
                            logInfo("Cloned composite description [%s] is in use or is not a cloned description. Skipping cleanup.",
                                    link);
                            return null;
                        }

                        // otherwise, propagate the failure
                        throw ex instanceof CompletionException
                                ? (CompletionException) ex
                                : new CompletionException(ex);
                    });
                }).collect(Collectors.toList());

        return DeferredResult.allOf(deferredResults)
                .thenAccept(ignore -> {
                    // converting return type to void
                });
    }

    private boolean isCompositeDescriptionLink(String descriptionLink) {
        return descriptionLink != null
                && descriptionLink.startsWith(CompositeDescriptionService.SELF_LINK);
    }

}
