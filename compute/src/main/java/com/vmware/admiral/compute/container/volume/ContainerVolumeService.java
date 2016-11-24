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

package com.vmware.admiral.compute.container.volume;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.container.util.CompositeComponentNotifier;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Describes a volume instance.
 */
public class ContainerVolumeService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONTAINER_VOLUMES;

    public static class ContainerVolumeState extends ResourceState {

        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";
        public static final String FIELD_NAME_DRIVER = "driver";
        public static final String FIELD_NAME_ORIGINATIONG_HOST_REFERENCE = "originatingHostReference";
        public static final String FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE = "adapterManagementReference";
        public static final String FIELD_NAME_COMPOSITE_COMPONENT_LINK = "compositeComponentLink";

        /** Defines the description of the volume */
        @Documentation(description = "Defines the description of the volume.")
        @UsageOption(option = PropertyUsageOption.LINK)
        public String descriptionLink;

        /** Reference to the host that this volume was created on. */
        @Documentation(description = "Reference to the host that this volume was created on.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI originatingHostReference;

        @Documentation(description = "Link to CompositeComponent when a volume is part of App/Composition request.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK })
        public String compositeComponentLink;

        /** Defines which adapter will serve the provision request */
        @Documentation(description = "Defines which adapter will serve the provision request")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI adapterManagementReference;

        /** Name of the volume driver to use. Defaults to local for the name. */
        @Documentation(description = "Name of the volume driver to use. Defaults to local for the name.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.OPTIONAL })
        public String driver;

        /**
         * Scope describes the level at which the volume exists, can be one of global for
         * cluster-wide or local for machine level. The default is local.
         */
        @Documentation(description = "Scope describes the level at which the volume exists, can be one of global for cluster-wide or local for machine level. The default is local.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.OPTIONAL })
        public String scope;

        /**
         * Mount path of the volume on the host.
         */
        @Documentation(description = "Mount path of the volume on the host.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.OPTIONAL })
        public File mountpoint;

        /**
         * Labels to set on the volume, specified as a map: {"key":"value","key2":"value2"}
         */
        @Documentation(description = "Labels to set on the volume, specified as a map: {\"key\":\"value\",\"key2\":\"value2\"}")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL })
        public Map<String, String> labels;

        /**
         * Low-level details about the volume, provided by the volume driver. Details are returned
         * as a map with key/value pairs: {"key":"value","key2":"value2"}
         */
        @Documentation(description = "Low-level details about the volume, provided by the volume driver. Details are returned as a map with key/value pairs: {\"key\":\"value\",\"key2\":\"value2\"}")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL })
        public Map<String, String> status;

    }

    public ContainerVolumeService() {
        super(ContainerVolumeState.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation create) {
        ContainerVolumeState body = getValidInputFrom(create, false);

        CompositeComponentNotifier.notifyCompositionComponent(this,
                body.compositeComponentLink, create.getAction());

        create.complete();
    }

    @Override
    public void handlePut(Operation put) {
        try {
            ContainerVolumeState putState = getValidInputFrom(put, false);
            setState(put, putState);
            put.setBody(putState).complete();
        } catch (Throwable e) {
            logSevere(e);
            put.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ContainerVolumeState currentState = getState(patch);
        ContainerVolumeState patchBody = getValidInputFrom(patch, true);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);
        String currentCompositeComponentLink = currentState.compositeComponentLink;

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        boolean changed = !newSignature.equals(currentSignature);

        if (!changed) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        } else {
            CompositeComponentNotifier.notifyCompositionComponentOnChange(this, patch.getAction(),
                    currentState.compositeComponentLink, currentCompositeComponentLink);
        }

        patch.complete();
    }

    @Override
    public void handleDelete(Operation delete) {
        ContainerVolumeState currentState = getState(delete);
        CompositeComponentNotifier.notifyCompositionComponent(this,
                currentState.compositeComponentLink, delete.getAction());

        super.handleDelete(delete);
    }

    /**
     * Returns valid {@link ContainerVolumeState} instance for the specified operation or throws an
     * Exception if validation fails.
     */
    private ContainerVolumeState getValidInputFrom(Operation op, boolean isUpdate) {
        checkForBody(op);
        ContainerVolumeState incomingState = op.getBody(ContainerVolumeState.class);
        validateState(incomingState, isUpdate);
        return incomingState;
    }

    /**
     * Validates the specified {@link ContainerVolumeState}. If the validation fails, an Exception
     * will be thrown.
     *
     * @param isUpdate
     *            on updates the check for non <code>null</code> required fields is skipped.
     *            <code>null</code> values in that case represent no change. PATCH method is
     *            considered an update. PUT is not an update.
     */
    public void validateState(ContainerVolumeState state, boolean isUpdate) {
        if (!isUpdate) {
            // check that all required fields are not null.
            // Skip this step on updates (null = no update)
            Utils.validateState(getStateDescription(), state);
        }

        if (state.adapterManagementReference == null) {
            state.adapterManagementReference = UriUtils.buildUri(getHost(),
                    ManagementUriParts.ADAPTER_DOCKER_VOLUME);
        }

    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerVolumeState template = (ContainerVolumeState) super.getDocumentTemplate();

        template.name = "name (string)";
        template.descriptionLink = UriUtils.buildUriPath(
                ContainerVolumeDescriptionService.FACTORY_LINK,
                "docker-volume");

        // ServiceDocumentTemplateUtil.indexCustomProperties(template);

        template.customProperties = new HashMap<>(1);
        template.customProperties.put("key (string)", "value (string)");

        template.labels = new HashMap<>(1);
        template.labels.put("key (string)", "value (string)");

        template.status = new HashMap<>(1);
        template.status.put("key (string)", "value (string)");

        // Default location according to official documents:
        // https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#/inspect-a-volume
        template.mountpoint = FileUtils.getFile("/var/lib/docker/volumes/");

        return template;
    }

}
