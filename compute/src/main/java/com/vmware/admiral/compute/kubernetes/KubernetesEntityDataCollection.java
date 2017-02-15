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

package com.vmware.admiral.compute.kubernetes;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.KubernetesService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesService.KubernetesState;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class KubernetesEntityDataCollection extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_ENTITY_DATA_COLLECTION;

    public static final String DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_ID =
            "__default-list-data-collection";
    public static final String DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK = UriUtils
            .buildUriPath(FACTORY_LINK, DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_ID);

    public static ServiceDocument buildDefaultStateInstance() {
        KubernetesEntityDataCollectionState state = new KubernetesEntityDataCollectionState();
        state.documentSelfLink = DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK;
        state.taskInfo = new TaskState();
        state.taskInfo.stage = TaskStage.STARTED;
        state.computeHostLinks = new HashSet<String>();
        return state;
    }

    public static class KubernetesEntityDataCollectionState extends
            TaskServiceDocument<DefaultSubStage> {

        @Documentation(description = "The list of compute host links.")
        @PropertyOptions(indexing = {
                PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
        public Set<String> computeHostLinks;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        // don't keep any versions for the document
        template.documentDescription.versionRetentionLimit = 1;
        return template;
    }

    public static class EntityListCallback extends ServiceTaskCallbackResponse {

        public String computeHostLink;
        public Map<String, String> entityIdsAndNames = new HashMap<String, String>();
        public Map<String, String> entityIdsAndTypes = new HashMap<String, String>();
        public boolean unlockDataCollectionForHost;
    }

    public KubernetesEntityDataCollection() {
        super(KubernetesEntityDataCollectionState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    private URI getDefaultListingAdapter(ServiceHost host) {
        return UriUtils.buildUri(host, ManagementUriParts.ADAPTER_KUBERNETES_HOST);
    }

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("body is required"));
            return;
        }

        KubernetesEntityDataCollectionState initState = post
                .getBody(KubernetesEntityDataCollectionState.class);
        if (initState.documentSelfLink == null
                || !initState.documentSelfLink
                .endsWith(DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_ID)) {
            post.fail(new LocalizableValidationException(
                    "Only one instance of kubernetes entity data collection can be started",
                    "compute.entity.data-collection.single"));
            return;
        }

        post.setBody(initState).complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            logFine("Ignoring converted PUT.");
            put.complete();
            return;
        }

        if (!checkForBody(put)) {
            return;
        }

        KubernetesEntityDataCollectionState putBody = put
                .getBody(KubernetesEntityDataCollectionState.class);

        this.setState(put, putBody);
        put.setBody(putBody).complete();
    }

    @Override
    public void handlePatch(Operation op) {
        EntityListCallback body = op.getBody(EntityListCallback.class);

        if (body.computeHostLink == null) {
            logFine("'computeHostLink' is required");
            op.complete();
            return;
        }

        KubernetesEntityDataCollectionState state = getState(op);
        if (body.unlockDataCollectionForHost) {
            // patch to mark that there is no active entity data collection for a given host.
            state.computeHostLinks.remove(body.computeHostLink);
            op.complete();
            return;
        }

        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
            logFine("Host entity list callback invoked for host [%s]", body.computeHostLink);
        }

        // the patch will succeed regardless of the synchronization process
        if (state.computeHostLinks.contains(body.computeHostLink)) {
            op.complete();
            return;// return since there is an active data collection for this host.
        } else {
            state.computeHostLinks.add(body.computeHostLink);
            op.complete();
        }

        List<KubernetesState> entityStates = new ArrayList<KubernetesState>();

        QueryTask queryTask = QueryUtil.buildPropertyQuery(KubernetesState.class,
                KubernetesState.FIELD_NAME_PARENT_LINK, body.computeHostLink);

        QueryUtil.addExpandOption(queryTask);
        QueryUtil.addBroadcastOption(queryTask);

        new ServiceDocumentQuery<KubernetesState>(getHost(), KubernetesState.class).query(queryTask,
                (r) -> {
                    if (r.hasException()) {
                        logSevere(
                                "Failed to query for existing KubernetesState instances: %s",
                                r.getException() instanceof CancellationException
                                        ? r.getException().getMessage()
                                        : Utils.toString(r.getException()));
                        unlockCurrentDataCollectionForHost(body.computeHostLink);
                    } else if (r.hasResult()) {
                        entityStates.add(r.getResult());
                    } else {
                        AdapterRequest request = new AdapterRequest();
                        request.operationTypeId = ContainerHostOperationType.LIST_ENTITIES.id;
                        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
                        request.resourceReference = UriUtils.buildUri(getHost(),
                                body.computeHostLink);
                        sendRequest(Operation
                                .createPatch(getDefaultListingAdapter(getHost()))
                                .setBody(request)
                                .addPragmaDirective(
                                        Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                                .setCompletion(
                                        (o, ex) -> {
                                            if (ex == null) {
                                                EntityListCallback callback = o
                                                        .getBody(EntityListCallback.class);
                                                callback.computeHostLink = body.computeHostLink;
                                                updateEntityStates(callback, entityStates);
                                            } else {
                                                unlockCurrentDataCollectionForHost(
                                                        body.computeHostLink);
                                            }
                                        }));
                    }
                });
    }

    private void updateEntityStates(EntityListCallback callback,
            List<KubernetesState> entityStates) {

        for (KubernetesState entityState : entityStates) {
            if (entityState.id != null) {
                callback.entityIdsAndNames.remove(entityState.id);
            }
        }

        // finished removing existing entity states, now deal with remaining IDs
        List<KubernetesState> entitiesLeft = new ArrayList<KubernetesState>();

        Operation operation = Operation
                .createGet(this, callback.computeHostLink)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere("Failure to retrieve host [%s].",
                                        callback.computeHostLink, Utils.toString(ex));
                                unlockCurrentDataCollectionForHost(callback.computeHostLink);
                                return;
                            }
                            ComputeState host = o.getBody(ComputeState.class);
                            List<String> group = host.tenantLinks;

                            for (Entry<String, String> entry : callback.entityIdsAndNames.entrySet()) {
                                KubernetesState entity = new KubernetesState();
                                entity.id = entry.getKey();
                                entity.name = entry.getValue();
                                entity.documentSelfLink = KubernetesUtil.buildEntityLink(entity.id);
                                entity.type = callback.entityIdsAndTypes.get(entry.getKey());

                                entity.tenantLinks = group;
                                entity.descriptionLink = String.format("%s-%s",
                                        KubernetesDescriptionService.DISCOVERED_DESCRIPTION_LINK,
                                        UUID.randomUUID().toString());

                                entity.parentLink = callback.computeHostLink;

                                entitiesLeft.add(entity);
                            }

                            createDiscoveredEntities(
                                    entitiesLeft,
                                    // e is not used?
                                    (e) -> unlockCurrentDataCollectionForHost(
                                            callback.computeHostLink));
                        });

        sendRequest(operation);
    }

    private void unlockCurrentDataCollectionForHost(String computeHostLink) {
        EntityListCallback body = new EntityListCallback();
        body.computeHostLink = computeHostLink;
        body.unlockDataCollectionForHost = true;
        sendRequest(Operation.createPatch(getUri())
                .setBody(body)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Self patch failed: %s",
                                ex instanceof CancellationException ? ex.getMessage()
                                        : Utils.toString(ex));
                    }
                }));
    }

    private void createDiscoveredEntities(List<KubernetesState> entities,
            Consumer<Throwable> callback) {
        if (entities.isEmpty()) {
            callback.accept(null);
        } else {
            AtomicInteger counter = new AtomicInteger(entities.size());
            for (KubernetesState entity : entities) {
                if (entity.name == null) {
                    logWarning("Name not set for entity: %s", entity.documentSelfLink);
                    if (counter.decrementAndGet() == 0) {
                        callback.accept(null);
                    }
                    continue;
                }
                if (entity.type == null) {
                    logWarning("Type not set for entity: %s", entity.documentSelfLink);
                    if (counter.decrementAndGet() == 0) {
                        callback.accept(null);
                    }
                    continue;
                }
                // check again if the entity state already exists by id. This is needed in
                // cluster mode not to create entity states that we already have

                List<KubernetesState> entitiesFound = new ArrayList<KubernetesState>();
                QueryTask entityStatesQuery = QueryUtil.buildPropertyQuery(KubernetesState.class,
                        KubernetesState.FIELD_NAME_ID, entity.id);
                new ServiceDocumentQuery<KubernetesState>(getHost(), KubernetesState.class).query(entityStatesQuery,
                        (r) -> {
                            if (r.hasException()) {
                                logSevere("Failed to get entity %s : %s",
                                        entity.name, r.getException().getMessage());
                                callback.accept(r.getException());
                            } else if (r.hasResult()) {
                                entitiesFound.add(r.getResult());
                            } else {
                                if (entitiesFound.isEmpty()) {
                                    createDiscoveredEntity(callback, counter,
                                            entity);
                                } else {
                                    if (counter.decrementAndGet() == 0) {
                                        callback.accept(null);
                                    }
                                }
                            }
                        });
            }
        }
    }

    private void createDiscoveredEntity(Consumer<Throwable> callback,
            AtomicInteger counter, KubernetesState entity) {

        logFine("Creating KubernetesState for discovered entity: %s", entity.id);

        // TODO: differentiate between entity types and create requests to the corresponding
        // services
        AtomicBoolean hasError = new AtomicBoolean(false);
        sendRequest(OperationUtil
                .createForcedPost(this, KubernetesService.FACTORY_LINK)
                .setBody(entity)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere(
                                        "Failed to create KubernetesState for discovered entity (id=%s): %s",
                                        entity.id,
                                        ex.getMessage());
                                if (hasError.compareAndSet(false, true)) {
                                    callback.accept(ex);
                                }
                                return;
                            } else {
                                logInfo("Created KubernetesState for discovered entity: %s",
                                        entity.id);
                            }

                            KubernetesState body = o.getBody(KubernetesState.class);
                            createDiscoveredEntityDescription(body);

                            if (counter.decrementAndGet() == 0) {
                                callback.accept(null);
                            }
                        }));
    }

    private void createDiscoveredEntityDescription(KubernetesState entity) {
        if (entity.kubernetesEntity == null) {
            logWarning("Yaml missing for entity: %s", entity.documentSelfLink);
            return;
        }

        logFine("Creating KubernetesDescription for discovered entity: %s", entity.id);

        KubernetesDescription entityDescription =
                KubernetesUtil.createKubernetesEntityDescription(entity);

        sendRequest(OperationUtil
                .createForcedPost(this, KubernetesDescriptionService.FACTORY_LINK)
                .setBody(entityDescription)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere(
                                        "Failed to create KubernetesDescription for discovered entity (id=%s): %s",
                                        entity.id, ex.getMessage());
                            } else {
                                logInfo("Created KubernetesDescription for discovered entity: %s",
                                        entity.id);
                            }
                        }));

    }
}
