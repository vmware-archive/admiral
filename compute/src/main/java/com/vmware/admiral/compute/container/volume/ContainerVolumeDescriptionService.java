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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.CloneableResource;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
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
public class ContainerVolumeDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONTAINER_VOLUMES_DESC;

    /* Instance to link to when existing volumes are discovered on a host */
    public static final String DISCOVERED_INSTANCE = "discovered";
    public static final String DISCOVERED_DESCRIPTION_LINK = UriUtils.buildUriPath(FACTORY_LINK,
            DISCOVERED_INSTANCE);

    public static final String DEFAULT_VOLUME_DRIVER = "local";

    @JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
    @JsonIgnoreProperties({ "customProperties" })
    public static class ContainerVolumeDescription extends ResourceState
            implements CloneableResource {

        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DRIVER = "driver";

        /** Defines which adapter will serve the provision request */
        @Documentation(description = "Defines which adapter will serve the provision request")
        @JsonIgnore
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI instanceAdapterReference;

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
         * Composite Template use only. If set to true, specifies that this volume exists outside of
         * the Composite Template.
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

        /** Link to the parent volume description */
        @JsonProperty("parent_description_link")
        @Documentation(description = "Link to the parent volume description.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String parentDescriptionLink;

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

        @Override
        public Operation createCloneOperation(Service sender) {
            this.parentDescriptionLink = this.documentSelfLink;
            this.documentSelfLink = null;
            return Operation.createPost(sender, FACTORY_LINK)
                    .setBody(this);
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
    public void handleCreate(Operation startPost) {
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
            if (StringUtils.isBlank(state.driver)) {
                state.driver = DEFAULT_VOLUME_DRIVER;
            }

            // check that all required fields are not null.
            // Skip this step on updates (null = no update)
            Utils.validateState(getStateDescription(), state);
            if (DEFAULT_VOLUME_DRIVER.equals(state.driver)) {
                VolumeUtil.validateLocalVolumeName(state.name);
            }
        }


        if (state.instanceAdapterReference == null) {
            state.instanceAdapterReference = UriUtils
                    .buildUri(ManagementUriParts.ADAPTER_DOCKER_VOLUME);
        }

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
        template.customProperties = new HashMap<>(1);
        template.customProperties.put("key (string)", "value (string)");

        template.options = new HashMap<>(1);
        template.options.put("mountpoint (string)",
                "/var/lib/docker/volumes/ (string)");
        return template;
    }

}
