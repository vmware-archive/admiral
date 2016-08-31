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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.io.FileUtils;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Describes a volume instance.
 */
public class ContainerVolumeDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONTAINER_VOLUMES_DESC;

    public static class ContainerVolumeDescription extends MultiTenantDocument {

        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DRIVER = "driver";
        public static final String COMPOSITE_DESCRIPTION_LINK = "compositeDescriptionLink";

        /** The new volume’s name. If not specified, Docker generates a name. */
        @Documentation(description = "The name of a given volume.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String name;

        @Documentation(description = "Link to CompositeComponent when a volume is part of App/Composition request.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK })
        public String compositeDescriptionLink;

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
         * A map of field-value pairs for a given volume. These are used to specify volume options
         * that are to be used by the volume drivers.
         */
        @Documentation(description = "A map of field-value pairs for a given volume. These are used"
                + "to specify volume options that are used by the volume drivers.")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Map<String, String> options;

        /**
         * Composite Template use only. If set to true, specifies that this volume exists outside
         * of the Composite Template.
         */
        @Documentation(description = "Composite Template use only. If set to true, specifies that "
                + "this volume exists outside of the Composite Template.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean external;

        /**
         * Composite Template use only. The name of the external volume. If set then the value of
         * the attribute 'external' is considered 'true'.
         */
        @Documentation(description = "Composite Template use only. The name of the external volume."
                + " If set then the value of the attribute 'external' is considered 'true'.")
        @JsonProperty("external_name")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String externalName;

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
         * A map of field-value pairs for a given volume. These key/value pairs are custom tags,
         * properties or attributes that could be used to add additional data or tag the volume
         * instance for query and policy purposes.
         */
        @Documentation(description = "A map of field-value pairs for a given volume. These key/value pairs are custom tags,"
                + " properties or attributes that could be used to add additional data or tag the volume"
                + " instance for query and policy purposes.")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL })
        public Map<String, String> customProperties;

        @JsonAnySetter
        private void putCustomProperty(String key, String value) {
            if (customProperties == null) {
                customProperties = new HashMap<>();
            }
            customProperties.put(key, value);
        }

        @JsonAnyGetter
        private Map<String, String> getCustomProperties() {
            return customProperties;
        }

    }

    public ContainerVolumeDescriptionService() {
        super(ContainerVolumeDescription.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        try {
            ContainerVolumeDescription desc = getValidInputFrom(startPost, false);
            logFine("Initial name is %s", desc.name);
            startPost.complete();
        } catch (Throwable e) {
            logSevere(e);
            startPost.fail(e);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            ContainerVolumeDescription putDesc = getValidInputFrom(put, false);
            setState(put, putDesc);
            put.setBody(putDesc).complete();
        } catch (Throwable e) {
            logSevere(e);
            put.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ContainerVolumeDescription currentState = getState(patch);
        ContainerVolumeDescription patchBody = getValidInputFrom(patch, true);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);

        PropertyUtils.mergeCustomProperties(currentState.customProperties,
                patchBody.customProperties);

        PropertyUtils.mergeCustomProperties(currentState.labels,
                patchBody.labels);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        boolean changed = !newSignature.equals(currentSignature);
        if (!changed) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        patch.complete();
    }

    /**
     * Validates the specified {@link ContainerVolumeDescription}. If the validation fails, an
     * Exception will be thrown.
     *
     * @param isUpdate
     *            on updates the check for non <code>null</code> required fields is skipped.
     *            <code>null</code> values in that case represent no change. PATCH method is
     *            considered an update. PUT is not an update.
     */
    public void validateState(ContainerVolumeDescription state, boolean isUpdate) {
        if (!isUpdate) {
            // check that all required fields are not null.
            // Skip this step on updates (null = no update)
            Utils.validateState(getStateDescription(), state);
        }
        // TODO Implement more validations.

    }

    /**
     * Returns valid {@link ContainerVolumeDescription} instance for the specified operation or
     * throws an Exception if validation fails.
     */
    private ContainerVolumeDescription getValidInputFrom(Operation op, boolean isUpdate) {
        checkForBody(op);
        ContainerVolumeDescription incomingState = op.getBody(ContainerVolumeDescription.class);
        validateState(incomingState, isUpdate);
        return incomingState;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerVolumeDescription template = (ContainerVolumeDescription) super.getDocumentTemplate();

        template.name = "name (string)";
        template.compositeDescriptionLink = "compositeDescriptionLink (string) (optional)";
        template.customProperties = new HashMap<>(1);
        template.customProperties.put("key (string)", "value (string)");

        template.labels = new HashMap<>(1);
        template.labels.put("key (string)", "value (string)");

        // Default location according to official documents:
        // https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#/inspect-a-volume
        template.mountpoint = FileUtils.getFile("/var/lib/docker/volumes/");

        return template;
    }

}
