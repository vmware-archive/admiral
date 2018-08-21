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

import static com.vmware.admiral.compute.content.CompositeDescriptionContentService.KUBERNETES_APPLICATION_TEMPLATE_PREFIX;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.FORMATTER;

import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.adapter.common.KubernetesOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
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
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

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
        state.computeHostLinks = new HashSet<>();
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
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    public static class KubernetesEntityData {

        public String kind;

        public String name;

        public String selfLink;

        public String namespace;

        /**
         * This will be != null, in case the entity is created from admiral,
         * and it was part of composite component. Will be used to discover applications
         * deployed from admiral.
         */
        public String compositeComponentId;
    }

    public static class EntityListCallback extends ServiceTaskCallbackResponse {

        public String computeHostLink;
        public Map<String, KubernetesEntityData> idToEntityData = new ConcurrentHashMap<>();
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

    private QueryTask getKubernetesStatesQueryTask() {
        QueryTask q = new QueryTask();
        q.querySpec = new QueryTask.QuerySpecification();
        q.taskInfo.isDirect = true;

        Iterator<Class<? extends ResourceState>> iterator = CompositeComponentRegistry.getClasses();

        while (iterator.hasNext()) {
            q.querySpec.query.addBooleanClause(new Query()
                    .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
                    .setTermMatchValue(Utils.buildKind(iterator.next()))
                    .setOccurance(Occurance.SHOULD_OCCUR));
        }

        q.documentExpirationTimeMicros = ServiceDocumentQuery.getDefaultQueryExpiration();

        QueryUtil.addExpandOption(q);
        QueryUtil.addBroadcastOption(q);
        return q;
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
            op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
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
            op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            op.complete();
            return;// return since there is an active data collection for this host.
        } else {
            state.computeHostLinks.add(body.computeHostLink);
            op.complete();
        }

        List<ResourceState> entityStates = new ArrayList<>();

        QueryTask q = getKubernetesStatesQueryTask();

        q.querySpec.query.addBooleanClause(new QueryTask.Query()
                .setTermPropertyName(BaseKubernetesState.FIELD_NAME_PARENT_LINK)
                .setTermMatchValue(body.computeHostLink)
                .setOccurance(Occurance.MUST_OCCUR));

        new ServiceDocumentQuery<ResourceState>(getHost(), ResourceState.class).query(q,
                (r) -> {
                    if (r.hasException()) {
                        logSevere("Failed to query for existing KubernetesState instances: %s",
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
            List<ResourceState> entityStates) {

        for (ResourceState entityState : entityStates) {
            boolean exists = false;
            if (entityState.id != null) {
                exists = callback.idToEntityData.remove(entityState.id) != null;
            }
            if (!exists) {
                handleMissingEntity(entityState);
            } else {
                requestEntityInspection(entityState);
            }
        }

        // finished removing existing entity states, now deal with remaining IDs
        List<BaseKubernetesState> entitiesLeft = new ArrayList<>();
        Set<String> compositeIdsToCreate = new HashSet<>();
        Operation operation = Operation
                .createGet(this, callback.computeHostLink)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere("Failure to retrieve host [%s]. Error: %s",
                                        callback.computeHostLink, Utils.toString(ex));
                                unlockCurrentDataCollectionForHost(callback.computeHostLink);
                                return;
                            }
                            ComputeState host = o.getBody(ComputeState.class);
                            List<String> group = host.tenantLinks;

                            for (Entry<String, KubernetesEntityData> entry : callback.idToEntityData
                                    .entrySet()) {
                                KubernetesEntityData data = entry.getValue();
                                BaseKubernetesState state =
                                        KubernetesUtil.createKubernetesEntityState(data.kind);
                                if (state == null) {
                                    logWarning("Dropping entity %s, because of unknown type %s",
                                            entry.getKey(), data.kind);
                                    continue;
                                }

                                state.name = data.name;
                                state.id = entry.getKey();
                                state.documentSelfLink = entry.getKey();
                                state.kubernetesSelfLink = data.selfLink;
                                state.tenantLinks = group;
                                state.parentLink = callback.computeHostLink;
                                state = checkForCompositeComponentId(state, data);
                                entitiesLeft.add(state);
                                if (state.compositeComponentLink != null && !state
                                        .compositeComponentLink.isEmpty()) {
                                    compositeIdsToCreate.add(state.compositeComponentLink);
                                }
                            }
                            createCompositeComponents(compositeIdsToCreate, () ->
                                    createDiscoveredEntities(entitiesLeft, () ->
                                            unlockCurrentDataCollectionForHost(
                                                    callback.computeHostLink)));
                        });
        sendRequest(operation);
    }

    private void requestEntityInspection(ResourceState kubernetesState) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(getHost(),
                kubernetesState.documentSelfLink);

        request.operationTypeId = KubernetesOperationType.INSPECT.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        getHost().sendRequest(Operation
                .createPatch(getHost(), ManagementUriParts.ADAPTER_KUBERNETES)
                .setBody(request)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning(
                                "Exception while inspect request for kubernetes entity: %s. "
                                        + "Error: %s", kubernetesState.documentSelfLink,
                                Utils.toString(ex));
                    }
                }));
    }

    private void createCompositeComponents(Set<String> compositeIds, Runnable callback) {
        if (compositeIds == null || compositeIds.isEmpty()) {
            callback.run();
            return;
        }

        AtomicInteger counter = new AtomicInteger(compositeIds.size());

        Runnable decrementCounter = () -> {
            if (counter.decrementAndGet() == 0) {
                callback.run();
            }
        };

        for (String compositeId : compositeIds) {
            sendRequest(Operation
                    .createGet(this, buildCompositeComponentPath(compositeId))
                    .setCompletion((o, ex) -> {
                        if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                            createCompositeComponent(compositeId, decrementCounter);
                        } else if (ex != null) {
                            logWarning("Error getting composite component: %s", compositeId);
                            decrementCounter.run();
                        } else {
                            decrementCounter.run();
                        }
                    }));
        }

    }

    private void createCompositeComponent(String compositeId, Runnable callback) {
        CompositeComponent compositeComponent = new CompositeComponent();
        compositeComponent.documentSelfLink = compositeId;
        compositeComponent.name = KUBERNETES_APPLICATION_TEMPLATE_PREFIX + ZonedDateTime
                .now(ZoneOffset.UTC).format(FORMATTER);
        sendRequest(Operation.createPost(this, CompositeComponentFactoryService.SELF_LINK)
                .setBody(compositeComponent)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Error creating composite component "
                                + "on kubernetes data collection: %s", Utils.toString(ex));
                    }
                    callback.run();
                }));
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

    private void createDiscoveredEntities(List<BaseKubernetesState> entities,
            Runnable callback) {
        if (entities.isEmpty()) {
            callback.run();
        } else {
            AtomicInteger counter = new AtomicInteger(entities.size());
            for (BaseKubernetesState entity : entities) {
                if (entity.name == null) {
                    logWarning("Name not set for entity: %s", entity.documentSelfLink);
                    if (counter.decrementAndGet() == 0) {
                        callback.run();
                    }
                    continue;
                }
                // check again if the entity state already exists by id. This is needed in
                // cluster mode not to create entity states that we already have

                List<ResourceState> entitiesFound = new ArrayList<>();

                // This may be overkill, as the id alone could give us the desired entity.
                QueryTask entityStatesQuery = getKubernetesStatesQueryTask();

                entityStatesQuery.querySpec.query.addBooleanClause(new QueryTask.Query()
                        .setTermPropertyName(BaseKubernetesState.FIELD_NAME_ID)
                        .setTermMatchValue(entity.id)
                        .setOccurance(Occurance.MUST_OCCUR));

                new ServiceDocumentQuery<ResourceState>(getHost(), ResourceState.class)
                        .query(entityStatesQuery,
                                (r) -> {
                                    if (r.hasException()) {
                                        logSevere("Failed to get entity %s : %s",
                                                entity.name, r.getException().getMessage());
                                        callback.run();
                                    } else if (r.hasResult()) {
                                        entitiesFound.add(r.getResult());
                                    } else {
                                        if (entitiesFound.isEmpty()) {
                                            createDiscoveredEntity(counter, entity, callback);
                                        } else {
                                            if (counter.decrementAndGet() == 0) {
                                                callback.run();
                                            }
                                        }
                                    }
                                });
            }
        }
    }

    private void createDiscoveredEntity(AtomicInteger counter, BaseKubernetesState entity,
            Runnable callback) {

        logFine("Creating KubernetesState for discovered entity: %s", entity.id);
        String type = KubernetesUtil.getResourceType(entity.getType()).getName();

        AtomicBoolean hasError = new AtomicBoolean(false);
        sendRequest(OperationUtil
                .createForcedPost(this, CompositeComponentRegistry.stateFactoryLinkByType(type))
                .setBody(entity)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere("Failed to create KubernetesState for discovered entity"
                                                + " (id=%s): %s", entity.id, ex.getMessage());
                                if (hasError.compareAndSet(false, true)) {
                                    callback.run();
                                }
                            } else {
                                logInfo("Created KubernetesState for discovered entity: %s",
                                        entity.id);
                            }
                            if (counter.decrementAndGet() == 0) {
                                callback.run();
                            }
                        }));
    }

    private void handleMissingEntity(ResourceState state) {
        sendRequest(Operation
                .createDelete(this, state.documentSelfLink)
                .setBody(new ServiceDocument())
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                logWarning("Failed deleting KubernetesState of missing entity: %s",
                                                state.documentSelfLink,
                                        ex);
                                return;
                            }
                            logInfo("Deleted KubernetesState of missing entity: %s",
                                    state.documentSelfLink);
                        }));
    }

    private BaseKubernetesState checkForCompositeComponentId(BaseKubernetesState state,
            KubernetesEntityData data) {
        if (data.compositeComponentId == null || data.compositeComponentId.isEmpty()) {
            return state;
        }
        state.compositeComponentLink = buildCompositeComponentPath(data.compositeComponentId);
        return state;
    }

    private static String buildCompositeComponentPath(String compositeId) {
        return UriUtils.buildUriPath(CompositeComponentFactoryService.SELF_LINK, compositeId);
    }
}
