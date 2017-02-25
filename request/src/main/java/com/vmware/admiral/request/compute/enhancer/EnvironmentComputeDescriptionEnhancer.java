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

import static com.vmware.admiral.compute.ComputeConstants.OVA_URI;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.env.ComputeImageDescription;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentState;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentStateExpanded;
import com.vmware.admiral.compute.env.InstanceTypeDescription;
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

public class EnvironmentComputeDescriptionEnhancer extends ComputeDescriptionEnhancer {
    static final String TEMPLATE_LINK = "__templateComputeLink";

    private ServiceHost host;
    private URI referer;

    public EnvironmentComputeDescriptionEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {

        return getEnvironmentState(context.environmentLink)
                .thenCompose(env -> {
                    DeferredResult<ComputeDescription> result = new DeferredResult<>();
                    applyInstanceType(cd, env);

                    if (cd.dataStoreId == null) {
                        cd.dataStoreId = env.getStringMiscValue("placement", "dataStoreId");
                    }

                    if (cd.authCredentialsLink == null) {
                        cd.authCredentialsLink = env.getStringMiscValue("authentication",
                                "guestAuthLink");
                    }
                    if (cd.zoneId == null) {
                        cd.zoneId = env.getStringMiscValue("placement", "zoneId");
                    }
                    if (cd.zoneId == null && context.zoneId != null) {
                        cd.zoneId = context.zoneId;
                    }

                    String absImageId = context.imageType;
                    if (absImageId != null) {
                        String imageId = null;
                        if (env.computeProfile != null && env.computeProfile.imageMapping != null
                                && env.computeProfile.imageMapping.containsKey(absImageId)) {
                            ComputeImageDescription imageDesc = env.computeProfile.imageMapping
                                    .get(absImageId);
                            if (imageDesc.image != null) {
                                imageId = imageDesc.image;
                            } else if (imageDesc.imageByRegion != null) {
                                imageId = imageDesc.imageByRegion.get(context.regionId);
                            }
                        }
                        if (imageId == null) {
                            imageId = absImageId;
                        }

                        // if it's not clone from template
                        if (!cd.customProperties.containsKey(TEMPLATE_LINK)) {
                            try {
                                URI imageUri = URI.create(imageId);
                                String scheme = imageUri.getScheme();
                                if (scheme != null
                                        && (scheme.startsWith("http")
                                                || scheme.startsWith("file"))) {
                                    cd.customProperties.put(OVA_URI, imageUri.toString());
                                }
                                cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                                        imageId);
                            } catch (Throwable t) {
                                result.fail(t);
                                return result;
                            }
                        } else {
                            cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                                    imageId);
                        }
                    }

                    if (!context.skipNetwork && (cd.networkInterfaceDescLinks == null
                            || cd.networkInterfaceDescLinks.isEmpty())) {
                        attachNetworkInterfaceDescription(context, cd, env, result);
                    } else {
                        result.complete(cd);
                    }
                    return result;
                });

    }

    private void applyInstanceType(ComputeDescription cd, EnvironmentStateExpanded env) {
        InstanceTypeDescription instanceTypeDescription = null;
        if (env.computeProfile != null && env.computeProfile.instanceTypeMapping != null) {
            instanceTypeDescription = env.computeProfile.instanceTypeMapping.get(cd.instanceType);
        }

        if (instanceTypeDescription == null) {
            return;
        }

        if (instanceTypeDescription.instanceType != null) {
            cd.instanceType = instanceTypeDescription.instanceType;
        } else {
            cd.cpuCount = instanceTypeDescription.cpuCount;
            cd.totalMemoryBytes = instanceTypeDescription.memoryMb * 1024 * 1024;
        }
    }

    private void attachNetworkInterfaceDescription(EnhanceContext context, ComputeDescription cd,
            EnvironmentState env, DeferredResult<ComputeDescription> result) {

        String networkId = env.getStringMiscValue("placement", "networkId");

        if (networkId == null) {
            // For now keep it here, optionally we will support it as direct placement decision.
            String subnetLink = EnhancerUtils.getCustomProperty(cd, "subnetworkLink");
            createNicDesc(context, cd, null, subnetLink, result);
        } else {
            Query q = Query.Builder.create()
                    .addKindFieldClause(NetworkState.class)
                    .addCaseInsensitiveFieldClause(NetworkState.FIELD_NAME_NAME, networkId,
                            MatchType.TERM, Occurance.MUST_OCCUR)
                    .addFieldClause(NetworkState.FIELD_NAME_REGION_ID, context.regionId)
                    .build();

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(q)
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

    private DeferredResult<EnvironmentStateExpanded> getEnvironmentState(String uriLink) {
        host.log(Level.INFO, "Loading state for %s", uriLink);

        URI envUri = UriUtils.buildUri(host, uriLink);
        return host.sendWithDeferredResult(
                Operation.createGet(EnvironmentStateExpanded.buildUri(envUri)).setReferer(referer),
                EnvironmentStateExpanded.class);
    }
}
