/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.util.ResourceDescriptionUtil;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Describes grouping of multiple container instances deployed at the same time. It represents a
 * template definition of related services or an application.
 */
public class CompositeComponentService extends StatefulService {

    public static class CompositeComponent extends
            com.vmware.admiral.service.common.MultiTenantDocument {

        public static final String FIELD_NAME_COMPOSITE_DESCRIPTION_LINK =
                "compositeDescriptionLink";
        public static final String CUSTOM_PROPERTY_HOST_LINK = "__hostLink";

        /** Name of composite description */
        @Documentation(description = "Name of composite description.")
        @PropertyOptions(indexing = PropertyIndexingOption.CASE_INSENSITIVE)
        public String name;

        /** (Optional) CompositeDescription link */
        @Documentation(description = "CompositeDescription link.")
        @PropertyOptions(usage = { PropertyUsageOption.LINK, PropertyUsageOption.OPTIONAL })
        public String compositeDescriptionLink;

        @Documentation(description = "Component links.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public List<String> componentLinks;

        /** Composite component creation time in milliseconds */
        @Documentation(description = "Composite creation time in milliseconds")
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        public long created;

        /**
         * Custom property bag that can be used to store resource specific properties.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE,
                PropertyIndexingOption.EXPAND,
                PropertyIndexingOption.FIXED_ITEM_NAME })
        public Map<String, String> customProperties;
    }

    public CompositeComponentService() {
        super(CompositeComponent.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation startPost) {
        if (!checkForBody(startPost)) {
            return;
        }

        try {
            CompositeComponent state = startPost.getBody(CompositeComponent.class);
            state.created = System.currentTimeMillis();
            logFine("Composite created: %s. Refer: %s", state.documentSelfLink,
                    startPost.getReferer());
            validateStateOnStart(state);
            startPost.complete();
        } catch (Throwable e) {
            logSevere(e);
            startPost.fail(e);
        }
    }

    private void validateStateOnStart(CompositeComponent state) {
        assertNotEmpty(state.name, "name");
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        CompositeComponent putBody = put.getBody(CompositeComponent.class);

        try {
            validateStateOnStart(putBody);
            this.setState(put, putBody);
            put.setBody(null).complete();
        } catch (Throwable e) {
            put.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        CompositeComponent currentState = getState(patch);
        CompositeComponent patchBody = patch.getBody(CompositeComponent.class);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        currentState.name = PropertyUtils.mergeProperty(currentState.name, patchBody.name);
        currentState.compositeDescriptionLink = PropertyUtils.mergeProperty(
                currentState.compositeDescriptionLink, patchBody.compositeDescriptionLink);
        currentState.customProperties = PropertyUtils.mergeCustomProperties(currentState
                .customProperties, patchBody.customProperties);

        boolean deletePatch = patch.getUri().getQuery() != null
                && patch.getUri().getQuery().contains(UriUtils.URI_PARAM_INCLUDE_DELETED);

        List<String> componentLinksToCheck = null;

        if (deletePatch && patchBody.componentLinks != null
                && currentState.componentLinks != null) {
            for (String componentLink : patchBody.componentLinks) {
                currentState.componentLinks.remove(componentLink);
            }
            componentLinksToCheck = new ArrayList<>(currentState.componentLinks);
        } else {
            currentState.componentLinks = PropertyUtils.mergeLists(currentState.componentLinks,
                    patchBody.componentLinks);
        }
        currentState.tenantLinks = PropertyUtils.mergeLists(currentState.tenantLinks,
                patchBody.tenantLinks);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        // if the signature hasn't change we shouldn't modify the state
        if (currentSignature.equals(newSignature)) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        patch.complete();

        if (componentLinksToCheck != null) {
            deleteDocumentIfNeeded(componentLinksToCheck, () ->
                    sendWithDeferredResult(Operation
                            .createDelete(getUri())
                            .setBodyNoCloning(new ServiceDocument())
                    ).thenCompose(ignored ->
                            ResourceDescriptionUtil.deleteClonedCompositeDescription(getHost(),
                                    currentState.compositeDescriptionLink)
                    ).whenComplete((aVoid, e) -> {
                        if (e != null) {
                            if (e instanceof CompletionException) {
                                e = e.getCause();
                            }
                            logSevere("Failed deleting cloned component description %s. Error: %s",
                                    currentState.compositeDescriptionLink, Utils.toString(e));
                        }
                    })
            );
        }
    }

    @Override
    public void handleDelete(Operation delete) {
        // Start updating external network and volume composite components,
        // but do not wait for the updates to finish
        updateExternalNetworksIfNeeded(getState(delete));
        updateExternalVolumesIfNeeded(getState(delete));
        delete.complete();
    }

    private void updateExternalNetworksIfNeeded(CompositeComponent composite) {
        if (composite.componentLinks == null) {
            return;
        }

        List<String> networkLinks = composite.componentLinks.stream()
                .filter(link -> link.startsWith(ContainerNetworkService.FACTORY_LINK))
                .collect(Collectors.toList());

        if (networkLinks.isEmpty()) {
            return;
        }

        QueryTask networksQuery = QueryUtil.buildQuery(ContainerNetworkState.class, false);

        QueryUtil.addListValueClause(networksQuery,
                ContainerNetworkState.FIELD_NAME_SELF_LINK, networkLinks);
        QueryUtil.addExpandOption(networksQuery);

        new ServiceDocumentQuery<>(getHost(),
                ContainerNetworkState.class).query(
                        networksQuery, (r) -> {
                            if (r.hasResult()) {
                                boolean external = r.getResult().external != null
                                        ? r.getResult().external : false;
                                if (external) {
                                    ContainerNetworkState networkState = r.getResult();
                                    networkState.compositeComponentLinks.remove(
                                            composite.documentSelfLink);
                                    updateNetworkState(networkState);
                                }
                            }
                        });
    }

    private void updateExternalVolumesIfNeeded(CompositeComponent composite) {
        if (composite.componentLinks == null) {
            return;
        }

        List<String> volumeLinks = composite.componentLinks.stream()
                .filter(link -> link.startsWith(ContainerVolumeService.FACTORY_LINK))
                .collect(Collectors.toList());

        if (volumeLinks.isEmpty()) {
            return;
        }

        QueryTask volumesQuery = QueryUtil.buildQuery(ContainerVolumeState.class, false);

        QueryUtil.addListValueClause(volumesQuery,
                ContainerVolumeState.FIELD_NAME_SELF_LINK, volumeLinks);
        QueryUtil.addExpandOption(volumesQuery);

        new ServiceDocumentQuery<>(getHost(),
                ContainerVolumeState.class).query(
                        volumesQuery, (r) -> {
                            if (r.hasResult()) {
                                boolean external = r.getResult().external != null
                                        ? r.getResult().external : false;
                                if (external) {
                                    ContainerVolumeState volumeState = r.getResult();
                                    volumeState.compositeComponentLinks.remove(
                                            composite.documentSelfLink);
                                    updateVolumeState(volumeState);
                                }
                            }
                        });
    }

    private void updateNetworkState(ContainerNetworkState patch) {
        sendRequest(Operation.createPatch(getHost(), patch.documentSelfLink)
                .setBody(patch)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        getHost().log(Level.WARNING, "Could not update the component links of"
                                + " network %s", patch.name);
                    }
                }));
    }

    private void updateVolumeState(ContainerVolumeState patch) {
        sendRequest(Operation.createPatch(getHost(), patch.documentSelfLink)
                .setBody(patch)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        getHost().log(Level.WARNING, "Could not update the component links of"
                                + " volume %s", patch.name);
                    }
                }));
    }

    private void deleteDocumentIfNeeded(List<String> componentLinks, Runnable deleteCallback) {

        if (componentLinks.isEmpty()) {
            deleteCallback.run();
            return;
        }

        // if the component links are only external networks or volumes then it's possible to delete
        // the composite component

        List<String> containers = componentLinks.stream()
                .filter((c) -> c.startsWith(ContainerFactoryService.SELF_LINK))
                .collect(Collectors.toList());

        List<String> networks = componentLinks.stream()
                .filter((c) -> c.startsWith(ContainerNetworkService.FACTORY_LINK))
                .collect(Collectors.toList());

        List<String> volumes = componentLinks.stream()
                .filter((c) -> c.startsWith(ContainerVolumeService.FACTORY_LINK))
                .collect(Collectors.toList());

        if (containers.isEmpty() && (!networks.isEmpty() || !volumes.isEmpty())) {
            queryExternalComponents(networks, volumes, deleteCallback);
        }
    }

    private void queryExternalComponents(List<String> networks, List<String> volumes,
            Runnable deleteCallback) {

        queryExternalNetworks(networks, (allNetworksExternal) ->
                queryExternalVolumes(volumes, (allVolumesExternal) -> {
                    if (allNetworksExternal && allVolumesExternal) {
                        deleteCallback.run();
                    }
                }));
    }

    private void queryExternalNetworks(List<String> networks, Consumer<Boolean> resultCallback) {
        if (!networks.isEmpty()) {

            QueryTask networkQuery = QueryUtil.buildQuery(ContainerNetworkState.class, false);
            QueryUtil.addListValueClause(networkQuery, ServiceDocument.FIELD_NAME_SELF_LINK,
                    networks);
            QueryUtil.addBroadcastOption(networkQuery);
            QueryUtil.addExpandOption(networkQuery);

            AtomicBoolean allNetworksExternal = new AtomicBoolean(true);

            ServiceDocumentQuery<ContainerNetworkState> query = new ServiceDocumentQuery<>(
                    getHost(), ContainerNetworkState.class);
            query.query(networkQuery, (r) -> {
                if (r.hasException()) {
                    logWarning("Can't find container network states. Error: %s",
                            Utils.toString(r.getException()));
                } else if (r.hasResult()) {
                    allNetworksExternal.set(allNetworksExternal.get()
                            && r.getResult().external != null
                            && r.getResult().external);
                } else {
                    if (allNetworksExternal.get()) {
                        resultCallback.accept(true);
                    } else {
                        logWarning("Non-external networks still associated to this composite"
                                + " component!");
                        resultCallback.accept(false);
                    }
                }
            });
        } else {
            resultCallback.accept(true);
        }
    }

    private void queryExternalVolumes(List<String> volumes, Consumer<Boolean> resultCallback) {
        if (!volumes.isEmpty()) {

            QueryTask volumeQuery = QueryUtil.buildQuery(ContainerVolumeState.class, false);
            QueryUtil.addListValueClause(volumeQuery, ServiceDocument.FIELD_NAME_SELF_LINK,
                    volumes);
            QueryUtil.addBroadcastOption(volumeQuery);
            QueryUtil.addExpandOption(volumeQuery);

            AtomicBoolean allVolumeExternal = new AtomicBoolean(true);

            ServiceDocumentQuery<ContainerVolumeState> query = new ServiceDocumentQuery<>(
                    getHost(), ContainerVolumeState.class);
            query.query(volumeQuery, (r) -> {
                if (r.hasException()) {
                    logWarning("Can't find container volume states. Error: %s",
                            Utils.toString(r.getException()));
                } else if (r.hasResult()) {
                    allVolumeExternal.set(allVolumeExternal.get() && r.getResult().external != null
                            && r.getResult().external);
                } else {
                    if (allVolumeExternal.get()) {
                        resultCallback.accept(true);
                    } else {
                        logWarning("Non-external volumes still associated to this composite"
                                + " component!");
                        resultCallback.accept(false);
                    }
                }
            });
        } else {
            resultCallback.accept(true);
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        CompositeComponent template = (CompositeComponent) super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        template.name = "name (string)";
        template.compositeDescriptionLink = "compositeDescriptionLink (string) (optional)";
        template.componentLinks = new ArrayList<>(1);
        template.componentLinks.add("componentLink (string)");
        template.customProperties = new HashMap<>();

        return template;
    }

}
