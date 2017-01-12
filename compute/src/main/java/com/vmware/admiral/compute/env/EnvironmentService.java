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

package com.vmware.admiral.compute.env;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.PropertyMapping;
import com.vmware.admiral.compute.env.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.env.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.env.StorageProfileService.StorageProfile;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * CRUD service for managing environments.
 */
public class EnvironmentService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.ENVIRONMENTS;

    /**
     * Describes an environment - compute/storage/network configuration and mapping for a specific
     * endpoint that allows compute provisioning that is agnostic on the target endpoint type.
     */
    public static class EnvironmentState extends ResourceState {
        public static final String FIELD_NAME_ENDPOINT_LINK = "endpointLink";
        public static final String FIELD_NAME_ENDPOINT_TYPE = "endpointType";
        public static final String FIELD_NAME_MISC = "misc";

        @Documentation(description = "Link to the endpoint this environment is associated with")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointLink;

        @Documentation(description = "The endpoint type if this environment is not for a specific endpoint ")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointType;

        @Documentation(description = "Link to the compute profile for this environment")
        @PropertyOptions(usage = { REQUIRED, AUTO_MERGE_IF_NOT_NULL })
        public String computeProfileLink;

        @Documentation(description = "Link to the storage profile for this environment")
        @PropertyOptions(usage = { REQUIRED, AUTO_MERGE_IF_NOT_NULL })
        public String storageProfileLink;

        @Documentation(description = "Link to the network profile for this environment")
        @PropertyOptions(usage = { REQUIRED, AUTO_MERGE_IF_NOT_NULL })
        public String networkProfileLink;

        // TODO: remove this once storage and network profiles are completed
        @Documentation(description = "Misc properties")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public Map<String, PropertyMapping> misc;

        public Object getMiscValue(String propertyName, String key) {
            PropertyMapping mapping = this.misc != null ? this.misc.get(propertyName) : null;
            if (mapping != null) {
                return mapping.mappings.get(key);
            }
            return null;
        }

        public String getStringMiscValue(String propertyName, String key) {
            PropertyMapping mapping = this.misc != null ? this.misc.get(propertyName) : null;
            if (mapping != null) {
                return (String) mapping.mappings.get(key);
            }
            return null;
        }

        @Override
        public void copyTo(ResourceState target) {
            super.copyTo(target);
            if (target instanceof EnvironmentState) {
                EnvironmentState targetState = (EnvironmentState) target;
                targetState.endpointLink = this.endpointLink;
                targetState.endpointType = this.endpointType;
                targetState.computeProfileLink = this.computeProfileLink;
                targetState.storageProfileLink = this.storageProfileLink;
                targetState.networkProfileLink = this.networkProfileLink;
                targetState.misc = this.misc;
            }
        }
    }

    public static class EnvironmentStateExpanded extends EnvironmentState {
        public EndpointState endpoint;
        public ComputeProfile computeProfile;
        public StorageProfile storageProfile;
        public NetworkProfile networkProfile;

        public static URI buildUri(URI envStateUri) {
            return UriUtils.buildExpandLinksQueryUri(envStateUri);
        }
    }

    public EnvironmentService() {
        super(EnvironmentState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleGet(Operation get) {
        EnvironmentState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        EnvironmentStateExpanded expanded = new EnvironmentStateExpanded();
        currentState.copyTo(expanded);

        List<Operation> getOps = new ArrayList<>(4);
        if (currentState.endpointLink != null) {
            getOps.add(Operation.createGet(this, currentState.endpointLink)
                    .setReferer(this.getUri())
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            expanded.endpoint = o.getBody(EndpointState.class);
                        }
                    }));
        }
        if (currentState.computeProfileLink != null) {
            getOps.add(Operation.createGet(this, currentState.computeProfileLink)
                    .setReferer(this.getUri())
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            expanded.computeProfile = o.getBody(ComputeProfile.class);
                        }
                    }));
        }
        if (currentState.storageProfileLink != null) {
            getOps.add(Operation.createGet(this, currentState.storageProfileLink)
                    .setReferer(this.getUri())
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            expanded.storageProfile = o.getBody(StorageProfile.class);
                        }
                    }));
        }
        if (currentState.networkProfileLink != null) {
            getOps.add(Operation.createGet(this, currentState.networkProfileLink)
                    .setReferer(this.getUri())
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            expanded.networkProfile = o.getBody(NetworkProfile.class);
                        }
                    }));
        }

        if (!getOps.isEmpty()) {
            OperationJoin.create(getOps)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            get.fail(exs.values().iterator().next());
                        } else {
                            get.setBody(expanded).complete();
                        }
                    }).sendWith(this);
        } else {
            get.setBody(expanded).complete();
        }
    }

    @Override
    public void handleCreate(Operation post) {
        processInput(post);
        post.complete();
    }

    @Override
    public void handlePut(Operation put) {
        EnvironmentState newState = processInput(put);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        EnvironmentState currentState = getState(patch);
        try {
            Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                    EnvironmentState.class, patch);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
        }
        patch.setBody(currentState);
        patch.complete();
    }

    public static List<EnvironmentStateExpanded> getDefaultEnvironments() {
        try {
            ObjectMapper mapper = YamlMapper.objectMapper();
            List<EnvironmentStateExpanded> envs = FileUtils
                    .findResources(EnvironmentStateExpanded.class, "env").stream()
                    .filter(r -> r.url != null)
                    .map(r -> {
                        try (InputStream is = r.url.openStream()) {
                            return mapper.readValue(is, EnvironmentStateExpanded.class);
                        } catch (Exception e) {
                            Utils.log(EnvironmentService.class,
                                    EnvironmentService.class.getSimpleName(), Level.WARNING,
                                    "Failure reading default environment: %s, reason: %s", r.url,
                                    e.getMessage());
                            return null;
                        }
                    }).filter(env -> env != null)
                    .collect(Collectors.toList());

            // populate pre-defined self links
            envs.forEach(env -> setDefaultSelfLinks(env));

            return envs;
        } catch (Exception e) {
            Utils.log(EnvironmentService.class, EnvironmentService.class.getSimpleName(),
                    Level.SEVERE, "Failure reading default environments, reason: %s",
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    public static List<ServiceDocument> getAllDefaultDocuments() {
        List<ServiceDocument> docs = new ArrayList<>();
        getDefaultEnvironments().forEach(env -> {
            if (env.computeProfile != null) {
                docs.add(env.computeProfile);
            }
            if (env.storageProfile != null) {
                docs.add(env.storageProfile);
            }
            if (env.networkProfile != null) {
                docs.add(env.networkProfile);
            }
            docs.add(env);
        });
        return docs;
    }

    private static void setDefaultSelfLinks(EnvironmentStateExpanded env) {
        env.documentSelfLink = UriUtils.buildUriPath(EnvironmentService.FACTORY_LINK,
                env.endpointType);

        if (env.computeProfile != null) {
            env.computeProfileLink = UriUtils.buildUriPath(ComputeProfileService.FACTORY_LINK,
                    env.endpointType);
            env.computeProfile.documentSelfLink = env.computeProfileLink;
        }
        if (env.storageProfile != null) {
            env.storageProfileLink = UriUtils.buildUriPath(StorageProfileService.FACTORY_LINK,
                    env.endpointType);
            env.storageProfile.documentSelfLink = env.storageProfileLink;
        }
        if (env.networkProfile != null) {
            env.networkProfileLink = UriUtils.buildUriPath(NetworkProfileService.FACTORY_LINK,
                    env.endpointType);
            env.networkProfile.documentSelfLink = env.networkProfileLink;
        }
    }

    private EnvironmentState processInput(Operation op) {
        if (!op.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }
        EnvironmentState state = op.getBody(EnvironmentState.class);
        AssertUtil.assertNotNull(state.name, "name");
        Utils.validateState(getStateDescription(), state);
        if (state.endpointLink == null && state.endpointType == null) {
            throw new LocalizableValidationException("Endpoint or endpoint type must be specified", "compute.endpoint.type.required");
        }
        if (state.endpointLink != null && state.endpointType != null) {
            throw new LocalizableValidationException(
                    "Only one of endpoint link or endpoint type must be specified",
                    "compute.endpoint.link.or.type.only");
        }
        return state;
    }
}
