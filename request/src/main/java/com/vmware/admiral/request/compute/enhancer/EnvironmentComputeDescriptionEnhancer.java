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
import java.util.function.BiConsumer;
import java.util.logging.Level;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentStateExpanded;
import com.vmware.admiral.compute.env.InstanceTypeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
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
    public void enhance(EnhanceContext context, ComputeDescription cd,
            BiConsumer<ComputeDescription, Throwable> callback) {
        getEnvironmentState(context.environmentLink, (env, e) -> {
            if (e != null) {
                callback.accept(cd, e);
                return;
            }

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
            if (cd.zoneId == null && context.endpointComputeDescription != null) {
                cd.zoneId = context.endpointComputeDescription.zoneId;
            }

            String absImageId = context.imageType;
            if (absImageId != null) {
                String imageId = null;
                if (env.computeProfile != null && env.computeProfile.imageMapping != null
                        && env.computeProfile.imageMapping.containsKey(absImageId)) {
                    imageId = env.computeProfile.imageMapping.get(absImageId).image;
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
                                && (scheme.startsWith("http") || scheme.startsWith("file"))) {
                            cd.customProperties.put(OVA_URI, imageUri.toString());
                        } else {
                            cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                                    imageId);
                        }
                    } catch (Throwable t) {
                        callback.accept(cd, t);
                        return;
                    }
                } else {
                    cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                            imageId);
                }
            }

            String networkId = env.getStringMiscValue("placement", "networkId");
            if (networkId != null && (cd.networkInterfaceDescLinks == null
                    || cd.networkInterfaceDescLinks.isEmpty())) {
                attachNetworkInterfaceDescription(context, cd, networkId, callback);
            } else {
                callback.accept(cd, null);
            }
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
            String networkId,
            BiConsumer<ComputeDescription, Throwable> callback) {
        Query q = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addFieldClause(NetworkState.FIELD_NAME_NAME, networkId)
                .addFieldClause(NetworkState.FIELD_NAME_REGION_ID,
                        context.endpointComputeDescription.regionId)
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
                        callback.accept(cd, null);
                        return;
                    }

                    QueryTask body = o.getBody(QueryTask.class);
                    if (body.results.documentLinks.isEmpty()) {
                        callback.accept(cd, null);
                    } else {
                        String networkLink = body.results.documentLinks.get(0);
                        createNicDesc(context, cd, networkLink, callback);
                    }
                })
                .sendWith(this.host);
    }

    private void createNicDesc(
            com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext context,
            ComputeDescription cd, String networkLink,
            BiConsumer<ComputeDescription, Throwable> callback) {
        NetworkInterfaceDescription nid = new NetworkInterfaceDescription();
        nid.assignment = IpAssignment.DYNAMIC;
        nid.deviceIndex = 0;
        nid.name = cd.name + "_nic";
        nid.networkLink = networkLink;
        nid.tenantLinks = cd.tenantLinks;
        Operation.createPost(this.host, NetworkInterfaceDescriptionService.FACTORY_LINK)
                .setBody(nid)
                .setReferer(this.referer)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        // don't fail for now
                        host.log(Level.WARNING, "Unable to create Nic description");
                        callback.accept(cd, null);
                        return;
                    }
                    NetworkInterfaceDescription b = o.getBody(NetworkInterfaceDescription.class);
                    cd.networkInterfaceDescLinks = new ArrayList<>();
                    cd.networkInterfaceDescLinks.add(b.documentSelfLink);
                    callback.accept(cd, null);
                })
                .sendWith(host);
    }

    private <T extends ServiceDocument> void getEnvironmentState(String uriLink,
            BiConsumer<EnvironmentStateExpanded, Throwable> callback) {
        host.log(Level.INFO, "Loading state for %s", uriLink);

        URI envUri = UriUtils.buildUri(host, uriLink);
        host.sendRequest(Operation.createGet(EnvironmentStateExpanded.buildUri(envUri))
                .setReferer(referer)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        callback.accept(null, e);
                        return;
                    }

                    EnvironmentStateExpanded state = o.getBody(EnvironmentStateExpanded.class);
                    callback.accept(state, null);
                }));
    }
}
