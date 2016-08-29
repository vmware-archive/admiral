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

package com.vmware.admiral.compute.endpoint;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.ComputeConstants.AdapterType;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class EndpointService extends StatefulService {

    public static final String FIELD_NAME_CUSTOM_PROP_TYPE = "__endpointType";
    private static final String ENDPOINT = "Endpoint";

    public static final String FACTORY_LINK = ManagementUriParts.ENDPOINTS;

    public static final Object ENDPOINT_ID_QUERY_PARAM = "id";

    public static class EndpointState extends com.vmware.admiral.service.common.MultiTenantDocument {

        @Documentation(description = "The name of this endpoint configuration")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String name;

        @Documentation(description = "The region id for the infra provider")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String regionId;

        @Documentation(description = "The endpoint type")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String endpointType;

        @Documentation(description = "Service Account private key id")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String privateKeyId;

        @Documentation(description = "Service Account private key")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String privateKey;

        @Documentation(description = "Endpoint specific ClientId. E.g. Azure subscriptionId")
        @PropertyOptions(usage = OPTIONAL, indexing = STORE_ONLY)
        public String userLink;

        @Documentation(description = "Host name or IP address.")
        @PropertyOptions(usage = OPTIONAL, indexing = STORE_ONLY)
        public String endpointHost;

        /** Custom properties. */
        @JsonIgnore
        @Documentation(description = "Custom properties.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> customProperties;

        // Service use fields:

        // TODO: Remove privateKey and privateKeyId once the UI is fixed.
        @PropertyOptions(usage = { SERVICE_USE, LINK }, indexing = STORE_ONLY)
        public String authCredentialsLink;

        @PropertyOptions(usage = { SERVICE_USE, LINK }, indexing = STORE_ONLY)
        public String computeLink;

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

    public EndpointService() {
        super(EndpointState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleCreate(Operation startPost) {
        if (!checkForBody(startPost)) {
            return;
        }
        try {
            EndpointState state = startPost.getBody(EndpointState.class);
            logFine("Initial name is %s", state.name);
            validateState(state);

            processStateAndCompleteRequest(startPost, state);
        } catch (Throwable e) {
            logSevere(e);
            startPost.fail(e);
        }
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        EndpointState putBody = put.getBody(EndpointState.class);

        try {
            validateState(putBody);
            this.setState(put, putBody);
            put.setBody(putBody).complete();
        } catch (Throwable e) {
            put.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        EndpointState currentState = getState(patch);
        EndpointState patchBody = patch.getBody(EndpointState.class);

        boolean modified = false;
        if (patchBody.privateKey != null && !patchBody.privateKey.equals(currentState.privateKey)) {
            modified = true;
            currentState.privateKey = patchBody.privateKey;
        }

        if (patchBody.privateKeyId != null
                && !patchBody.privateKeyId.equals(currentState.privateKeyId)) {
            modified = true;
            currentState.privateKeyId = patchBody.privateKeyId;
        }

        if (patchBody.userLink != null && !patchBody.userLink.equals(currentState.userLink)) {
            modified = true;
            currentState.userLink = patchBody.userLink;
        }

        if (patchBody.regionId != null && !patchBody.regionId.equals(currentState.regionId)) {
            modified = true;
            currentState.regionId = patchBody.regionId;
        }

        if (patchBody.customProperties != null && !patchBody.customProperties.isEmpty()) {
            if (currentState.customProperties == null || currentState.customProperties.isEmpty()) {
                currentState.customProperties = patchBody.customProperties;
            } else {
                for (Map.Entry<String, String> e : patchBody.customProperties.entrySet()) {
                    currentState.customProperties.put(e.getKey(), e.getValue());
                }
            }
            modified = true;
        }

        // if not modified we shouldn't modify the state
        if (!modified) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            patch.complete();
        } else {
            patch.setBody(currentState);
            patchDocuments(patch, currentState);
        }

    }

    @Override
    public void handleDelete(Operation delete) {

        EndpointState currentState = getState(delete);
        if (currentState.computeLink == null) {
            super.handleDelete(delete);
            return;
        }
        sendRequest(Operation
                .createGet(UriUtils.buildExpandLinksQueryUri(
                        UriUtils.buildUri(this.getHost(), currentState.computeLink)))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        delete.fail(ex);
                        return;
                    }
                    ComputeStateWithDescription desc = o.getBody(ComputeStateWithDescription.class);
                    proceedComputeStateDeletion(delete, currentState, desc);
                }));
    }

    private void validateState(EndpointState state) throws IllegalArgumentException {
        AssertUtil.assertNotEmpty(state.name, "name");
        AssertUtil.assertNotEmpty(state.endpointType, "endpointType");
        AssertUtil.assertNotEmpty(state.privateKey, "privateKey");
        AssertUtil.assertNotEmpty(state.privateKeyId, "privateKeyId");
        AssertUtil.assertNotEmpty(state.regionId, "regionId");
    }

    private void processStateAndCompleteRequest(Operation op, EndpointState state) {

        AuthCredentialsServiceState authState = new AuthCredentialsServiceState();
        authState.tenantLinks = state.tenantLinks;
        authState.privateKey = state.privateKey;
        authState.privateKeyId = state.privateKeyId;
        authState.userLink = state.userLink;
        authState.customProperties = state.customProperties;
        authState.type = ENDPOINT;

        sendRequest(Operation
                .createPost(this, AuthCredentialsService.FACTORY_LINK)
                .setBody(authState)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                op.fail(ex);
                                return;
                            }
                            AuthCredentialsService.AuthCredentialsServiceState authBody = o
                                    .getBody(
                                            AuthCredentialsService.AuthCredentialsServiceState.class);

                            state.authCredentialsLink = authBody.documentSelfLink;
                            if (validateConnection(op)) {
                                verifyCredentials(op, state);
                            } else {
                                createComputeDocuments(op, state);
                            }
                        }));
    }

    private boolean validateConnection(Operation op) {
        boolean validateConnection = op.getUri().getQuery() != null
                && op.getUri().getQuery()
                        .contains(ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
        return validateConnection;
    }

    private void patchDocuments(Operation patch, EndpointState state) {
        AuthCredentialsServiceState authState = new AuthCredentialsServiceState();
        authState.privateKey = state.privateKey;
        authState.privateKeyId = state.privateKeyId;
        authState.userLink = state.userLink;
        if (state.customProperties != null) {
            authState.customProperties = state.customProperties;
        }

        sendRequest(Operation
                .createPatch(this, state.authCredentialsLink)
                .setBody(authState)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                patch.fail(ex);
                                return;
                            }
                            if (validateConnection(patch)) {
                                verifyCredentialsAndPatch(patch, state);
                            } else {
                                patch.complete();
                            }
                        }));
    }

    private void verifyCredentialsAndPatch(Operation patch, EndpointState state) {
        ComputeInstanceRequest req = new ComputeInstanceRequest();
        req.requestType = ComputeInstanceRequest.InstanceRequestType.VALIDATE_CREDENTIALS;
        req.authCredentialsLink = state.authCredentialsLink;
        req.regionId = state.regionId;
        sendRequest(Operation
                .createPatch(getInstanceAdapterLink(state.endpointType))
                .setBody(req)
                .setCompletion((verifyOp, error) -> {
                    if (error != null) {
                        logWarning(error.getMessage());
                        patch.fail(error);
                        return;
                    }
                    patch.complete();
                }));
    }

    private ComputeDescription configureDescription(EndpointState state) {

        // setting up a host, so all have VM_HOST as a child
        ComputeDescription cd = new ComputeDescription();
        List<String> children = new ArrayList<>();
        // TODO: switch to VM_HOST once we introduce hosts discovery
        // children.add(ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST.toString());
        children.add(ComputeDescriptionService.ComputeDescription.ComputeType.VM_GUEST.toString());
        cd.supportedChildren = children;
        cd.zoneId = state.regionId;
        cd.regionId = state.regionId;
        cd.tenantLinks = state.tenantLinks;
        cd.authCredentialsLink = state.authCredentialsLink;
        cd.name = state.name;
        cd.id = UriUtils.getLastPathSegment(state.documentSelfLink);
        cd.customProperties = new HashMap<String, String>();
        if (state.customProperties != null) {
            cd.customProperties.putAll(state.customProperties);
        }
        cd.customProperties.put(FIELD_NAME_CUSTOM_PROP_TYPE, state.endpointType);

        applyEndpointAware(state.endpointType, cd);

        return cd;
    }

    private void applyEndpointAware(String endpointType, ComputeDescription cd) {
        EndpointType type = EndpointType.valueOf(endpointType);
        cd.environmentName = type.getDescription();
        cd.instanceAdapterReference = getInstanceAdapterLink(type);
        cd.enumerationAdapterReference = getAdapterLink(type, AdapterType.ENUMERATION_ADAPTER);
        cd.bootAdapterReference = getAdapterLink(type, AdapterType.BOOT_ADAPTER);
        cd.powerAdapterReference = getAdapterLink(type, AdapterType.POWER_ADAPTER);
    }

    private URI getInstanceAdapterLink(EndpointType endpointType) {
        return getAdapterLink(endpointType, AdapterType.INSTANCE_ADAPTER);
    }

    private URI getInstanceAdapterLink(String endpointType) {
        return getAdapterLink(EndpointType.valueOf(endpointType), AdapterType.INSTANCE_ADAPTER);
    }

    private URI getAdapterLink(EndpointType endpointType, AdapterType adapterType) {
        if (endpointType.supports(adapterType)) {
            return UriUtils.buildUri(getHost(), adapterType.adapterLink(endpointType.name()));
        }
        return null;
    }

    private void createComputeDocuments(Operation op, EndpointState state) {

        ComputeDescription computeDescription = configureDescription(state);
        ComputeState computeState = configureCompute(state);

        Operation cd = Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK);
        cd.setBody(computeDescription);
        Operation comp = Operation.createPost(this, ComputeService.FACTORY_LINK);
        OperationSequence.create(cd)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        long firstKey = exs.keySet().iterator().next();
                        exs.values()
                                .forEach(ex -> logWarning("Error: %s", ex.getMessage()));
                        op.fail(exs.get(firstKey));
                        return;
                    }

                    Operation cdOp = ops.get(cd.getId());
                    ComputeDescription desc = cdOp.getBody(ComputeDescription.class);
                    computeState.descriptionLink = desc.documentSelfLink;
                    comp.setBody(computeState);
                })
                .next(comp)
                .setCompletion(
                        (ops, exs) -> {
                            if (exs != null) {
                                long firstKey = exs.keySet().iterator().next();
                                exs.values().forEach(
                                        ex -> logWarning("Error: %s", ex.getMessage()));
                                op.fail(exs.get(firstKey));
                                return;
                            }
                            Operation csOp = ops.get(comp.getId());
                            ComputeState c = csOp.getBody(ComputeState.class);
                            state.computeLink = c.documentSelfLink;
                            op.setBody(state).complete();
                        })
                .sendWith(this);

    }

    private ComputeState configureCompute(EndpointState state) {
        ComputeState computeHost = new ComputeState();
        computeHost.id = UriUtils.getLastPathSegment(state.documentSelfLink);
        computeHost.adapterManagementReference = EndpointType.valueOf(state.endpointType)
                .getAdapterManagementUri(state);
        computeHost.tenantLinks = state.tenantLinks;
        computeHost.powerState = PowerState.ON;
        computeHost.customProperties = new HashMap<>();
        if (state.customProperties != null) {
            computeHost.customProperties.putAll(state.customProperties);
        }
        computeHost.customProperties.put(FIELD_NAME_CUSTOM_PROP_TYPE, state.endpointType);
        return computeHost;
    }

    private void verifyCredentials(Operation op, EndpointState state) {
        ComputeInstanceRequest req = new ComputeInstanceRequest();
        req.requestType = ComputeInstanceRequest.InstanceRequestType.VALIDATE_CREDENTIALS;
        req.authCredentialsLink = state.authCredentialsLink;
        req.regionId = state.regionId;
        sendRequest(Operation
                .createPatch(getInstanceAdapterLink(state.endpointType))
                .setBody(req)
                .setCompletion((verifyOp, error) -> {
                    if (error != null) {
                        logWarning(error.getMessage());
                        op.fail(error);
                        return;
                    }
                    createComputeDocuments(op, state);
                }));
    }

    private void proceedComputeStateDeletion(Operation op, EndpointState state,
            ComputeStateWithDescription cswd) {
        Operation opDelCr = Operation.createDelete(this, cswd.description.authCredentialsLink);
        Operation opDelCD = Operation.createDelete(this, cswd.description.documentSelfLink);
        Operation opDelComp = Operation.createDelete(this, cswd.documentSelfLink);
        OperationSequence
                .create(opDelComp)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        Throwable exComp = exs.get(opDelComp.getId());
                        if (exComp != null) {
                            logWarning("Could not delete compute %s, reason : %s",
                                    cswd.documentSelfLink, exComp.getMessage());
                        }
                    }
                })
                .next(opDelCD, opDelCr)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        Throwable exCD = exs.get(opDelCD.getId());
                        if (exCD != null) {
                            logWarning("Could not delete compute description %s, reason : %s",
                                    cswd.documentSelfLink, exCD.getMessage());
                        }
                        Throwable exCr = exs.get(opDelCr.getId());
                        if (exCr != null) {
                            logWarning("Could not delete credentials %s, reason : %s",
                                    cswd.description.authCredentialsLink, exCr.getMessage());
                        }
                        super.handleDelete(op);
                        return;
                    }
                    super.handleDelete(op);
                }).sendWith(this);
    }
}
