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

package com.vmware.admiral.compute.profile;

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
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfileExpanded;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
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
 * CRUD service for managing deployment profiles.
 */
public class ProfileService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.PROFILES;

    /**
     * Describes a profile - compute/storage/network configuration and mapping for a specific
     * endpoint that allows compute provisioning that is agnostic on the target endpoint type.
     */
    public static class ProfileState extends ResourceState {
        public static final String FIELD_NAME_ENDPOINT_LINK = "endpointLink";
        public static final String FIELD_NAME_ENDPOINT_TYPE = "endpointType";
        public static final String FIELD_NAME_MISC = "misc";
        public static final String FIELD_NAME_NETWORK_PROFILE_LINK = "networkProfileLink";

        @Documentation(description = "Link to the endpoint this profile is associated with")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointLink;

        @Documentation(description = "The endpoint type if this profile is not for a specific endpoint ")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointType;

        @Documentation(description = "Link to the compute profile for this profile")
        @PropertyOptions(usage = { REQUIRED, AUTO_MERGE_IF_NOT_NULL })
        public String computeProfileLink;

        @Documentation(description = "Link to the storage profile for this profile")
        @PropertyOptions(usage = { REQUIRED, AUTO_MERGE_IF_NOT_NULL })
        public String storageProfileLink;

        @Documentation(description = "Link to the network profile for this profile")
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
            if (target instanceof ProfileState) {
                ProfileState targetState = (ProfileState) target;
                targetState.endpointLink = this.endpointLink;
                targetState.endpointType = this.endpointType;
                targetState.computeProfileLink = this.computeProfileLink;
                targetState.storageProfileLink = this.storageProfileLink;
                targetState.networkProfileLink = this.networkProfileLink;
                targetState.misc = this.misc;
            }
        }
    }

    public static class ProfileStateExpanded extends ProfileState {
        public EndpointState endpoint;
        public ComputeProfile computeProfile;
        public StorageProfile storageProfile;
        public NetworkProfileExpanded networkProfile;

        public static URI buildUri(URI profileStateUri) {
            return UriUtils.buildExpandLinksQueryUri(profileStateUri);
        }
    }

    public ProfileService() {
        super(ProfileState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleGet(Operation get) {
        ProfileState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        ProfileStateExpanded expanded = new ProfileStateExpanded();
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
            URI uri = NetworkProfileExpanded.buildUri(UriUtils.buildUri(this.getHost(),
                    currentState.networkProfileLink));
            getOps.add(Operation.createGet(uri)
                    .setReferer(this.getUri())
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            expanded.networkProfile = o.getBody(NetworkProfileExpanded.class);
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
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            logFine("Ignoring converted PUT.");
            put.complete();
            return;
        }

        ProfileState newState = processInput(put);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        ProfileState currentState = getState(patch);
        try {
            Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                    ProfileState.class, patch);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
            return;
        }
        patch.setBody(currentState);
        patch.complete();
    }

    public static List<ProfileStateExpanded> getDefaultProfiles() {
        try {
            ObjectMapper mapper = YamlMapper.objectMapper();
            List<ProfileStateExpanded> profiles = FileUtils
                    .findResources(ProfileStateExpanded.class, "profiles").stream()
                    .filter(r -> r.url != null)
                    .map(r -> {
                        try (InputStream is = r.url.openStream()) {
                            return mapper.readValue(is, ProfileStateExpanded.class);
                        } catch (Exception e) {
                            Utils.log(ProfileService.class,
                                    ProfileService.class.getSimpleName(), Level.WARNING,
                                    "Failure reading default profile: %s, reason: %s", r.url,
                                    e.getMessage());
                            return null;
                        }
                    }).filter(profile -> profile != null)
                    .collect(Collectors.toList());

            // populate pre-defined self links
            profiles.forEach(profile -> setDefaultSelfLinks(profile));

            return profiles;
        } catch (Exception e) {
            Utils.log(ProfileService.class, ProfileService.class.getSimpleName(),
                    Level.SEVERE, "Failure reading default profiles, reason: %s",
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    public static List<ServiceDocument> getAllDefaultDocuments() {
        List<ServiceDocument> docs = new ArrayList<>();
        getDefaultProfiles().forEach(profile -> {
            if (profile.computeProfile != null) {
                docs.add(profile.computeProfile);
            }
            if (profile.storageProfile != null) {
                docs.add(profile.storageProfile);
            }
            if (profile.networkProfile != null) {
                docs.add(profile.networkProfile);
            }
            docs.add(profile);
        });
        return docs;
    }

    private static void setDefaultSelfLinks(ProfileStateExpanded profile) {
        profile.documentSelfLink = UriUtils.buildUriPath(ProfileService.FACTORY_LINK,
                profile.endpointType);

        if (profile.computeProfile != null) {
            profile.computeProfileLink = UriUtils.buildUriPath(ComputeProfileService.FACTORY_LINK,
                    profile.endpointType);
            profile.computeProfile.documentSelfLink = profile.computeProfileLink;
        }
        if (profile.storageProfile != null) {
            profile.storageProfileLink = UriUtils.buildUriPath(StorageProfileService.FACTORY_LINK,
                    profile.endpointType);
            profile.storageProfile.documentSelfLink = profile.storageProfileLink;
        }
        if (profile.networkProfile != null) {
            profile.networkProfileLink = UriUtils.buildUriPath(NetworkProfileService.FACTORY_LINK,
                    profile.endpointType);
            profile.networkProfile.documentSelfLink = profile.networkProfileLink;
        }
    }

    private ProfileState processInput(Operation op) {
        if (!op.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }
        ProfileState state = op.getBody(ProfileState.class);
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
