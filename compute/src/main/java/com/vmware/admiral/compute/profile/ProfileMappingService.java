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

import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Provides an intersection of mappings available across all profiles.
 */
public class ProfileMappingService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.PROFILE_MAPPINGS;
    private static Map<String, Class<? extends ResourceState>> profiles = new HashMap<>();

    {
        profiles.put(ComputeProfileService.FACTORY_LINK, ComputeProfile.class);
        // profiles.put(NetworkProfileService.FACTORY_LINK, NetworkProfile.class);
        // profiles.put(StorageProfileService.FACTORY_LINK, StorageProfile.class);
    }

    public static class ProfileMappingState extends MultiTenantDocument {
        public Map<String, List<String>> mappings;
    }

    @Override
    public void handleGet(Operation get) {
        Stream<Operation> ops = profiles.keySet().stream()
                .map(link -> Operation
                        .createGet(UriUtils
                                .buildExpandLinksQueryUri(UriUtils.buildUri(getHost(), link,
                                        get.getUri().getQuery())))
                        .setReferer(SELF_LINK));
        OperationJoin.create(ops).setCompletion((os, es) -> {
            if (es != null && !es.isEmpty()) {
                get.fail(es.values().iterator().next());
                return;
            }
            try {
                List<ProfileMappingState> states = os.values().stream().map(o -> {
                    ProfileMappingState state = new ProfileMappingState();
                    state.documentSelfLink = o.getUri().getPath();
                    ServiceDocumentQueryResult body = o.getBody(ServiceDocumentQueryResult.class);
                    if (body == null || body.documents == null) {
                        state.mappings = new HashMap<>();
                    } else {
                        Collection<Object> values = body.documents.values();
                        Class<? extends ResourceState> type = profiles
                                .get(o.getUri().getPath());
                        List<Field> fields = Arrays.asList(type.getDeclaredFields());
                        state.mappings = fields.stream()
                                .filter(f -> Map.class.isAssignableFrom(f.getType()))
                                .map(field -> new AbstractMap.SimpleEntry<String, List<String>>(
                                        field.getName(),
                                        getMappingIntersection(type, field, values)))
                                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
                        if (!values.isEmpty()) {
                            state.tenantLinks = Utils.fromJson(values.iterator().next(),
                                    type).tenantLinks;
                        }
                    }
                    return state;
                }).collect(Collectors.toList());
                get.setBody(QueryUtil.createQueryResult(states));
                get.complete();
            } catch (Exception e) {
                logSevere("Error getting ProfileMappingStates for %s. Error: %s",
                        get.getUri(), Utils.toString(e));
                get.fail(e);
            }
        }).sendWith(getHost());
    }

    @SuppressWarnings("unchecked")
    private Set<String> getMappingValue(Field field, Object profile) {
        try {
            return ((Map<String, Object>) field.get(profile)).keySet();
        } catch (Exception ex) {
            return new LinkedHashSet<String>();
        }
    }

    private List<String> getMappingIntersection(Class<? extends ResourceState> type,
            Field field,
            Collection<Object> values) {
        return new ArrayList<>(values.stream().map(value -> {
            Object profile = Utils.fromJson(value, type);
            if (profile == null) {
                return new LinkedHashSet<String>();
            }
            return getMappingValue(field, profile).stream().map(k -> k.toLowerCase())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }).reduce((a, b) -> {
            if (a == null) {
                return new LinkedHashSet<String>(b);
            }
            a.addAll(b);

            return a;
        }).orElse(new LinkedHashSet<String>()));
    }
}
