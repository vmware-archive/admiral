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

package com.vmware.admiral.compute.container.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class ResourceDescriptionUtil {

    public static final String CANNOT_DELETE_RESOURCE_DESCRIPTION = "Cannot delete resource description";
    public static final String DESCRIPTION_IS_COMPOSITE_DESCRIPTION_ERROR_FORMAT = "Cannot delete resource description: [%s] "
            + "is a composite description and should be handled with another call.";
    public static final String RESOURCE_DESCRIPTION_IN_USE_ERROR_FORMAT = CANNOT_DELETE_RESOURCE_DESCRIPTION
            + ": " + " %s resource states share resource description [%s].";

    public static final String CANNOT_DELETE_COMPOSITE_DESCRIPTION = "Cannot delete composite description";
    public static final String NOT_COMPOSITE_DESCRIPTION_ERROR_FORMAT = CANNOT_DELETE_COMPOSITE_DESCRIPTION
            + ": " + "service document [%s] is not a composite description.";
    public static final String NOT_CLONED_COMPOSITE_DESCRIPTION_ERROR_FORMAT = CANNOT_DELETE_COMPOSITE_DESCRIPTION
            + ": " + "composite description [%s] is not a clone.";
    public static final String FAILED_DELETE_COMPONENT_DESC_FROM_COMPOSITE_DESC_ERROR_FORMAT = "Failed to delete component description [%s] "
            + "from composite description [%s]. Cause: %S: %s";
    public static final String FAILED_DELETE_CLONED_COMPOSITE_DESC_ERROR_FORMAT = "Failed to delete cloned composite description [%s].";

    private static final Map<String, Class<? extends ServiceDocument>> MAP_KNOWN_DOCUMENT_KIND_TO_CLASS;
    private static final Map<String, Class<? extends ServiceDocument>> MAP_DESCRIPTION_TO_STATE;

    static {
        MAP_KNOWN_DOCUMENT_KIND_TO_CLASS = Collections.unmodifiableMap(buildKindToClassMap());
        MAP_DESCRIPTION_TO_STATE = Collections.unmodifiableMap(buildDescriptionToStateMap());
    }

    private static Map<String, Class<? extends ServiceDocument>> buildKindToClassMap() {
        HashMap<String, Class<? extends ServiceDocument>> kindToClassMap = new HashMap<>();

        kindToClassMap.put(
                Utils.buildKind(ContainerDescription.class),
                ContainerDescription.class);
        kindToClassMap.put(
                Utils.buildKind(ContainerNetworkDescription.class),
                ContainerNetworkDescription.class);
        kindToClassMap.put(
                Utils.buildKind(ContainerVolumeDescription.class),
                ContainerVolumeDescription.class);
        kindToClassMap.put(
                Utils.buildKind(CompositeDescription.class),
                CompositeDescription.class);

        return kindToClassMap;
    }

    private static Map<String, Class<? extends ServiceDocument>> buildDescriptionToStateMap() {
        HashMap<String, Class<? extends ServiceDocument>> descriptionToStateMap = new HashMap<>();

        descriptionToStateMap.put(
                ContainerDescription.class.getName(),
                ContainerState.class);
        descriptionToStateMap.put(
                ContainerNetworkDescription.class.getName(),
                ContainerNetworkState.class);
        descriptionToStateMap.put(
                ContainerVolumeDescription.class.getName(),
                ContainerVolumeState.class);
        descriptionToStateMap.put(
                CompositeDescription.class.getName(),
                CompositeComponent.class);

        return descriptionToStateMap;
    }

    public static DeferredResult<Void> deleteResourceDescription(ServiceHost host,
            String resourceDescriptionLink) {
        if (resourceDescriptionLink == null || resourceDescriptionLink.isEmpty()) {
            host.log(Level.WARNING, "No description is provided.");
            return DeferredResult.completed(null);
        }

        return getResourceDescription(host, resourceDescriptionLink)
                .thenCompose(desc -> {
                    if (desc == null) {
                        return DeferredResult.completed(null);
                    }

                    if (isCompositeDescription(desc)) {
                        String error = String.format(
                                DESCRIPTION_IS_COMPOSITE_DESCRIPTION_ERROR_FORMAT,
                                resourceDescriptionLink);
                        return DeferredResult.failed(new IllegalArgumentException(error));
                    }
                    return deleteResourceDescription(host,
                            resourceDescriptionLink, stateClassByDescriptionClass(desc.getClass()));
                }).exceptionally(ex -> {
                    String errMsg = String.format("Failed to delete resource description [%s].",
                            resourceDescriptionLink);
                    throw logAndPrepareCompletionException(host, ex, errMsg);
                });
    }

    public static DeferredResult<Void> deleteClonedCompositeDescription(ServiceHost host,
            String resourceDescriptionLink) {
        if (resourceDescriptionLink == null || resourceDescriptionLink.isEmpty()) {
            host.log(Level.WARNING, "No description is provided.");
            return DeferredResult.completed(null);
        }

        return verifyDescriptionIsNotUsed(host, resourceDescriptionLink, CompositeComponent.class)
                .thenCompose(ignore -> getResourceDescription(host, resourceDescriptionLink))
                .thenCompose(desc -> {
                    if (desc == null) {
                        return DeferredResult.completed(null);
                    }

                    // validate this is a composite description
                    if (!isCompositeDescription(desc)) {
                        String error = String.format(
                                NOT_COMPOSITE_DESCRIPTION_ERROR_FORMAT,
                                resourceDescriptionLink);
                        return DeferredResult.failed(new IllegalArgumentException(error));
                    }

                    // validate this is a clone composite description
                    CompositeDescription cd = (CompositeDescription) desc;
                    if (cd.parentDescriptionLink == null) {
                        String error = String.format(
                                NOT_CLONED_COMPOSITE_DESCRIPTION_ERROR_FORMAT,
                                resourceDescriptionLink);
                        host.log(Level.WARNING, error);
                        return DeferredResult.failed(new IllegalArgumentException(error));
                    }

                    // delete the descriptions of the components of the composite description
                    List<DeferredResult<Void>> deleteComponents = cd.descriptionLinks.stream()
                            .map(componentLink -> {
                                return deleteResourceDescription(host, componentLink)
                                        .exceptionally(ex -> {
                                            Throwable cause = ex instanceof CompletionException
                                                    ? ex.getCause() : ex;
                                            host.log(Level.WARNING,
                                                    FAILED_DELETE_COMPONENT_DESC_FROM_COMPOSITE_DESC_ERROR_FORMAT,
                                                    componentLink,
                                                    resourceDescriptionLink,
                                                    cause.getClass().getSimpleName(),
                                                    cause.getMessage());
                                            // ignore the failure to allow the deletion of the
                                            // composite description itself
                                            return null;
                                        });
                            }).collect(Collectors.toList());
                    return DeferredResult.allOf(deleteComponents)
                            .thenApply(ignore -> desc);
                }).thenCompose(desc -> {
                    if (desc == null) {
                        return DeferredResult.completed(null);
                    }

                    // delete the composite description even if a description of one of its
                    // components failed to be deleted
                    host.log(Level.INFO,
                            String.format(
                                    "Sending delete request to clone composite description [%s].",
                                    resourceDescriptionLink));
                    Operation delete = Operation.createDelete(host, resourceDescriptionLink)
                            .setReferer(host.getUri());
                    return host.sendWithDeferredResult(delete);
                }).thenAccept(i -> {
                    host.log(Level.INFO,
                            "Cloned composite description [%s] has been deleted.",
                            resourceDescriptionLink);
                }).exceptionally(ex -> {

                    String errMsg = String.format(
                            FAILED_DELETE_CLONED_COMPOSITE_DESC_ERROR_FORMAT,
                            resourceDescriptionLink);
                    throw logAndPrepareCompletionException(host, ex, errMsg);
                });
    }

    private static DeferredResult<Void> deleteResourceDescription(ServiceHost host,
            String resourceDescriptionLink, Class<? extends ServiceDocument> stateClass) {

        return verifyDescriptionIsNotUsed(host, resourceDescriptionLink, stateClass)
                .thenCompose(ignore -> {
                    host.log(Level.INFO,
                            String.format(
                                    "Resource description [%s] is not referenced by any resource. Deleting description.",
                                    resourceDescriptionLink));
                    Operation delete = Operation.createDelete(host, resourceDescriptionLink)
                            .setReferer(host.getUri());
                    return host.sendWithDeferredResult(delete)
                            .thenAccept(i -> {
                                host.log(Level.INFO,
                                        "Resource description [%s] has been deleted.",
                                        resourceDescriptionLink);
                            }).exceptionally(ex -> {
                                String error = String.format("%s [%s]",
                                        CANNOT_DELETE_RESOURCE_DESCRIPTION,
                                        resourceDescriptionLink);
                                throw logAndPrepareCompletionException(host, ex, error);
                            });
                });
    }

    private static DeferredResult<Void> verifyDescriptionIsNotUsed(ServiceHost host,
            String resourceDescriptionLink, Class<? extends ServiceDocument> stateClass) {
        DeferredResult<Void> deferredResult = new DeferredResult<>();

        QueryTask resourceStateQueryTask = QueryUtil.buildQuery(stateClass, true);

        String descriptionLinkFieldName = stateClass == CompositeComponent.class
                ? CompositeComponent.FIELD_NAME_COMPOSITE_DESCRIPTION_LINK
                : ContainerState.FIELD_NAME_DESCRIPTION_LINK;

        QueryUtil.addListValueClause(resourceStateQueryTask, descriptionLinkFieldName,
                Arrays.asList(resourceDescriptionLink));

        QueryUtil.addCountOption(resourceStateQueryTask);

        new ServiceDocumentQuery<>(host, ContainerState.class)
                .query(resourceStateQueryTask, (r) -> {
                    if (r.hasException()) {
                        String error = String.format(
                                "Failed to retrieve resources, sharing the same description [%s]: %s",
                                r.getDocumentSelfLink(),
                                r.getException());
                        host.log(Level.SEVERE, error);
                        deferredResult.fail(new IllegalStateException(error, r.getException()));
                    } else if (r.hasResult() && r.getCount() != 0) {
                        String error = String.format(
                                RESOURCE_DESCRIPTION_IN_USE_ERROR_FORMAT,
                                r.getCount(),
                                resourceDescriptionLink);
                        host.log(Level.WARNING, error);
                        deferredResult.fail(new IllegalStateException(error));
                    } else {
                        deferredResult.complete(null);
                    }
                });

        return deferredResult;
    }

    private static DeferredResult<? extends ServiceDocument> getResourceDescription(
            ServiceHost host,
            String resourceDescriptionLink) {

        DeferredResult<Operation> deferredResult = new DeferredResult<>();

        Operation.createGet(host, resourceDescriptionLink)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (o != null && o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        host.log(Level.WARNING,
                                "Resource description [%s] cannot be found. It is probably deleted already. Skipping deletion.",
                                resourceDescriptionLink);
                        deferredResult.complete(null);
                    } else if (ex != null) {
                        deferredResult.fail(ex);
                    } else {
                        deferredResult.complete(o);
                    }
                }).sendWith(host);

        return deferredResult.thenApply(ResourceDescriptionUtil::extractResourceDescriptionFromResponse)
                .exceptionally(ex -> {
                    String errMsg = String.format("Failed to retrieve description: %s.",
                            resourceDescriptionLink);
                    throw logAndPrepareCompletionException(host, ex, errMsg);
                });
    }

    private static ServiceDocument extractResourceDescriptionFromResponse(Operation response) {
        if (response == null) {
            return null;
        }

        if (!response.hasBody()) {
            return null;
        }

        String contentType = response.getContentType();
        if (!contentType.equals(Operation.MEDIA_TYPE_APPLICATION_JSON)) {
            throw new IllegalArgumentException(String.format(
                    "Cannot extract resource state from operation [%s %s]. Unexpected content type [%s]. Expected: [%s]",
                    response.getAction().toString(),
                    response.getUri().toString(),
                    contentType,
                    Operation.MEDIA_TYPE_APPLICATION_JSON));
        }

        Object rawBody = response.getBodyRaw();
        ServiceDocument serviceDocument = Utils.fromJson(rawBody, ServiceDocument.class);

        Class<? extends ServiceDocument> resourceClass = MAP_KNOWN_DOCUMENT_KIND_TO_CLASS
                .get(serviceDocument.documentKind);
        if (resourceClass != null) {
            return Utils.fromJson(rawBody, resourceClass);
        }
        return serviceDocument;
    }

    private static boolean isCompositeDescription(ServiceDocument resourceDescription) {
        return resourceDescription != null && resourceDescription instanceof CompositeDescription;
    }

    private static Class<? extends ServiceDocument> stateClassByDescriptionClass(
            Class<? extends ServiceDocument> descriptionClass) {
        Class<? extends ServiceDocument> result = MAP_DESCRIPTION_TO_STATE
                .get(descriptionClass.getName());
        return result != null ? result : ServiceDocument.class;
    }

    private static CompletionException logAndPrepareCompletionException(ServiceHost host,
            Throwable ex, String message) {

        Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;

        String error = String.format(
                "%s Cause: %S: %s",
                message,
                cause.getClass().getSimpleName(),
                cause.getMessage());
        host.log(Level.WARNING, error);

        return ex instanceof CompletionException
                ? (CompletionException) ex
                : new CompletionException(ex);
    }

}
