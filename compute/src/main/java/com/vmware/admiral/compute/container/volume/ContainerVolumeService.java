/*
 * Copyright (c) 2016-2019 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container.volume;

import static com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.DEFAULT_VOLUME_DRIVER;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        public static final String FIELD_NAME_POWER_STATE = "powerState";
        public static final String FIELD_NAME_DRIVER = "driver";
        public static final String FIELD_NAME_SCOPE = "scope";
        public static final String FIELD_NAME_PARENT_LINKS = "parentLinks";
        public static final String FIELD_NAME_ORIGINATING_HOST_LINK = "originatingHostLink";
        public static final String FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE =
                "adapterManagementReference";
        public static final String FIELD_NAME_COMPOSITE_COMPONENT_LINKS = "compositeComponentLinks";

        public enum PowerState {
            UNKNOWN,
            PROVISIONING,
            CONNECTED,
            RETIRED,
            ERROR;

            public boolean isUnmanaged() {
                return this == PROVISIONING || this == RETIRED;
            }
        }

        /** Defines the description of the volume */
        @Documentation(description = "Defines the description of the volume.")
        @UsageOption(option = PropertyUsageOption.LINK)
        public String descriptionLink;

        /** Reference to the host that this volume was created on. */
        @Documentation(description = "Reference to the host that this volume was created on.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String originatingHostLink;

        @Documentation(description = "Links to CompositeComponents when a volume is part of"
                + " App/Composition request.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL })
        public List<String> compositeComponentLinks;

        /** Defines which adapter will serve the provision request */
        @Documentation(description = "Defines which adapter will serve the provision request")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI adapterManagementReference;

        /** Volume state indicating runtime state of a volume instance. */
        @Documentation(description = "Volume state indicating runtime state of a volume instance.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public PowerState powerState;

        /** Container host links */
        @Documentation(description = "Container host links")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> parentLinks;

        /** Name of the volume driver to use. Defaults to local for the name. */
        @Documentation(description = "Name of the volume driver to use. Defaults to local for"
                + " the name.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.OPTIONAL })
        public String driver;

        /**
         * If set to true, specifies that this volume exists independently of any application.
         */
        @Documentation(description = "If set to true, specifies that this volume exists"
                + " independently of any application.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Boolean external;

        /**
         * Scope describes the level at which the volume exists, can be one of global for
         * cluster-wide or local for machine level. The default is local.
         */
        @Documentation(description = "Scope describes the level at which the volume exists, can"
                + " be one of global for cluster-wide or local for machine level. The default is"
                + " local.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.OPTIONAL })
        public String scope;

        /**
         * Mount path of the volume on the host.
         */
        @Documentation(description = "Mount path of the volume on the host.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.OPTIONAL })
        public String mountpoint;

        /**
         * A map of field-value pairs for a given volume. These are used to specify volume option
         * that are to be used by the volume drivers.
         */
        @Documentation(description = "A map of field-value pairs for a given volume. These are used"
                + " to specify volume options that are used by the volume drivers.")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Map<String, String> options;

        /**
         * Low-level details about the volume, provided by the volume driver. Details are returned
         * as a map with key/value pairs: {"key":"value","key2":"value2"}
         */
        @Documentation(description = "Low-level details about the volume, provided by the volume"
                + " driver. Details are returned as a map with key/value pairs:"
                + " {\"key\":\"value\",\"key2\":\"value2\"}")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL })
        public Map<String, String> status;

        /** Volume connected time in milliseconds */
        @Documentation(description = "Volume connected time in milliseconds")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long connected;

        /** The number of failures to update this volume state by the data collection service. */
        @Documentation(description = "The number of failures to update this volume state by the"
                + " data collection service.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.SERVICE_USE })
        public Integer _healthFailureCount;
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

        if (body.powerState == null) {
            body.powerState = ContainerVolumeState.PowerState.UNKNOWN;
        }

        body.connected = new Date().getTime();

        CompositeComponentNotifier.notifyCompositionComponents(this,
                body.compositeComponentLinks, create.getAction());

        create.complete();
    }

    @Override
    public void handlePut(Operation put) {
        try {
            ContainerVolumeState putState = getValidInputFrom(put, false);
            putState.copyTenantLinks(getState(put));
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
        List<String> currentCompositeComponentLinks = currentState.compositeComponentLinks;

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        boolean changed = !newSignature.equals(currentSignature);

        if (!changed) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        } else {
            CompositeComponentNotifier.notifyCompositionComponentsOnChange(this, patch.getAction(),
                    currentState.compositeComponentLinks, currentCompositeComponentLinks);
        }

        patch.complete();
    }

    @Override
    public void handleDelete(Operation delete) {
        ContainerVolumeState currentState = getState(delete);
        CompositeComponentNotifier.notifyCompositionComponents(this,
                currentState.compositeComponentLinks, delete.getAction());

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
            if (DEFAULT_VOLUME_DRIVER.equals(state.driver)) {
                VolumeUtil.validateLocalVolumeName(state.name);
            }
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerVolumeState template = (ContainerVolumeState) super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);

        template.name = "name (string)";
        template.descriptionLink = UriUtils.buildUriPath(
                ContainerVolumeDescriptionService.FACTORY_LINK,
                "docker-volume");

        template.powerState = ContainerVolumeState.PowerState.UNKNOWN;

        template.customProperties = new HashMap<>(1);
        template.customProperties.put("key (string)", "value (string)");

        template.status = new HashMap<>(1);
        template.status.put("key (string)", "value (string)");

        template.options = new HashMap<>(1);
        template.options.put("type (string)", "tmpfs (string)");

        // Default location according to official documents:
        // https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#/inspect-a-volume
        template.mountpoint = "/var/lib/docker/volumes/";

        template.compositeComponentLinks = new ArrayList<>(0);
        template.parentLinks = new ArrayList<>(0);

        return template;
    }

}
