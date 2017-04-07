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

package com.vmware.admiral.request.compute.enhancer;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.request.compute.NetworkProfileQueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class ComputeDescriptionProfileEnhancer extends ComputeDescriptionEnhancer {

    private ServiceHost host;
    private URI referer;

    public ComputeDescriptionProfileEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {

        return getProfileState(host, referer, context)
                .thenCompose(profile -> {
                    context.profile = profile;
                    DeferredResult<ComputeDescription> result = new DeferredResult<>();
                    if (cd.dataStoreId == null) {
                        cd.dataStoreId = profile.getStringMiscValue("placement", "dataStoreId");
                    }

                    if (cd.authCredentialsLink == null) {
                        cd.authCredentialsLink = profile.getStringMiscValue("authentication",
                                "guestAuthLink");
                    }
                    if (cd.zoneId == null) {
                        cd.zoneId = profile.getStringMiscValue("placement", "zoneId");
                    }
                    if (cd.zoneId == null && context.zoneId != null) {
                        cd.zoneId = context.zoneId;
                    }
                    cd.customProperties.put(ComputeConstants.CUSTOM_PROP_ENDPOINT_TYPE_NAME,
                            context.endpointType);

                    if (!context.skipNetwork && (cd.networkInterfaceDescLinks == null
                            || cd.networkInterfaceDescLinks.isEmpty())) {
                        attachNetworkInterfaceDescription(context, cd, profile, result);
                    } else {
                        result.complete(cd);
                    }
                    return result;
                });

    }

    private void attachNetworkInterfaceDescription(EnhanceContext context, ComputeDescription cd,
            ProfileState profile, DeferredResult<ComputeDescription> result) {

        String networkId = profile.getStringMiscValue("placement", "networkId");

        if (networkId == null) {
            // For now keep it here, optionally we will support it as direct placement decision.
            String subnetLink = EnhancerUtils.getCustomProperty(cd, "subnetworkLink");
            createNicDesc(context, cd, null, subnetLink, result);
        } else {
            Query.Builder queryBuilder = Query.Builder.create()
                    .addKindFieldClause(NetworkState.class)
                    .addCaseInsensitiveFieldClause(NetworkState.FIELD_NAME_NAME, networkId,
                            MatchType.TERM, Occurance.MUST_OCCUR)
                    .addFieldClause(NetworkState.FIELD_NAME_ENDPOINT_LINK, context.endpointLink);

            if (context.regionId != null) {
                queryBuilder.addFieldClause(NetworkState.FIELD_NAME_REGION_ID, context.regionId);
            }

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(queryBuilder.build())
                    .build();
            queryTask.tenantLinks = cd.tenantLinks;
            Operation.createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_QUERY_TASKS))
                    .setBody(queryTask)
                    .setReferer(this.referer)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            host.log(Level.WARNING, "Error processing task %s",
                                    queryTask.documentSelfLink);
                            result.complete(cd);
                            return;
                        }

                        QueryTask body = o.getBody(QueryTask.class);
                        if (body.results.documentLinks.isEmpty()) {
                            result.complete(cd);
                        } else {
                            String networkLink = body.results.documentLinks.get(0);
                            createNicDesc(context, cd, networkLink, null, result);
                        }
                    })
                    .sendWith(this.host);
        }
    }

    private void createNicDesc(
            com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext context,
            ComputeDescription cd, String networkLink, String subnetLink,
            DeferredResult<ComputeDescription> result) {
        NetworkInterfaceDescription nid = new NetworkInterfaceDescription();
        nid.assignment = IpAssignment.DYNAMIC;
        nid.deviceIndex = 0;
        nid.name = cd.name + "_nic";
        nid.networkLink = networkLink;
        nid.subnetLink = subnetLink;
        nid.tenantLinks = cd.tenantLinks;
        nid.customProperties = new HashMap<>();
        nid.customProperties.put(NetworkProfileQueryUtils.NO_NIC_VM, Boolean.TRUE.toString());

        Operation.createPost(this.host, NetworkInterfaceDescriptionService.FACTORY_LINK)
                .setBody(nid)
                .setReferer(this.referer)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        // don't fail for now
                        host.log(Level.WARNING, "Unable to create Nic description");
                        result.complete(cd);
                        return;
                    }
                    NetworkInterfaceDescription b = o.getBody(NetworkInterfaceDescription.class);
                    cd.networkInterfaceDescLinks = new ArrayList<>();
                    cd.networkInterfaceDescLinks.add(b.documentSelfLink);
                    result.complete(cd);
                })
                .sendWith(host);
    }
}
