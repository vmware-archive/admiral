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

package com.vmware.admiral.compute;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.EXPAND;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class EnvironmentMappingService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.ENVIRONMENT_MAPPING;

    public static class EnvironmentMappingState extends ResourceState {
        public static final String FIELD_NAME_ENDPOINT_TYPE_NAME = "endpointType";

        @Documentation(description = "The endpoint type")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = EXPAND)
        public String endpointType;

        public Map<String, PropertyMapping> properties;

        public String getMappingValue(String propertyName, String key) {
            PropertyMapping mapping = properties.get(propertyName);
            if (mapping != null) {
                return mapping.mappings.get(key);
            }
            return null;
        }
    }

    public EnvironmentMappingService() {
        super(EnvironmentMappingState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation start) {
        try {
            EnvironmentMappingState body = processInput(start);
            start.complete();
            logFine("Registering env: %s", body.name);
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            EnvironmentMappingState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        EnvironmentMappingState currentState = getState(patch);
        EnvironmentMappingState patchBody = patch.getBody(EnvironmentMappingState.class);

        ServiceDocumentDescription docDesc = getStateDescription();
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        // if the signature hasn't change we shouldn't modify the state
        if (currentSignature.equals(newSignature)) {
            currentState = null;
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        patch.setBody(currentState).complete();
    }

    private EnvironmentMappingState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        EnvironmentMappingState state = op.getBody(EnvironmentMappingState.class);
        validateState(state);
        return state;
    }

    public static void validateState(EnvironmentMappingState state) {
        if (state.properties == null) {
            throw (new IllegalArgumentException("properties is required"));
        }
    }

    public static List<EnvironmentMappingState> getDefaultMappings() {
        try {
            ObjectMapper mapper = YamlMapper.objectMapper();
            List<EnvironmentMappingState> mappings = FileUtils
                    .findResources(EnvironmentMappingState.class, "mappings").stream()
                    .filter(r -> r.url != null).map(r -> {
                        EnvironmentMappingState mappingState = null;
                        try (InputStream is = r.url.openStream()) {
                            mappingState = mapper.readValue(is,
                                    EnvironmentMappingState.class);
                        } catch (Exception e) {
                            return null;
                        }
                        mappingState.documentSelfLink = UriUtils.buildUriPath(FACTORY_LINK,
                                mappingState.endpointType);
                        return mappingState;
                    }).filter(obj -> obj != null).collect(Collectors.toList());

            return mappings;
        } catch (Throwable t) {
            Utils.log(EnvironmentMappingService.class,
                    EnvironmentMappingService.class.getSimpleName(), Level.SEVERE,
                    "Failure reading mappings,reason:", t.getMessage());
            return Collections.emptyList();
        }
    }

}
