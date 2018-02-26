/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.allocation.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.request.composition.CompositionGraph.ResourceNode;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Utils;

public class SharedUserDefinedNetworkFilter implements ImplicitDependencyFilter {

    private final CompositeDescriptionExpanded compositeDescription;
    private Map<String, ResourceNode> resourceNodesByName;

    public SharedUserDefinedNetworkFilter(
            Map<String, ResourceNode> resourceNodesByName, CompositeDescriptionExpanded desc) {
        this.compositeDescription = desc;
        this.resourceNodesByName = resourceNodesByName;
    }

    @Override
    public boolean isActive() {
        return compositeDescription.componentDescriptions.stream().map((cd) -> {
            return Utils.fromJson(cd.componentJson,
                    CompositeComponentRegistry.metaByType(cd.type).descriptionClass);
        }).filter((item) -> {
            return item instanceof ContainerDescription
                    && ((ContainerDescription) item).networks != null
                    && !((ContainerDescription) item).networks.isEmpty();
        }).count() > 1;
    }

    @Override
    public void apply() {

        List<? extends ResourceState> userNetworks = compositeDescription.componentDescriptions
                .stream().map((cd) -> {
                    return Utils.fromJson(cd.componentJson,
                            CompositeComponentRegistry.metaByType(cd.type).descriptionClass);
                }).filter((item) -> {
                    return item instanceof ContainerNetworkDescription
                            // external could be null
                            && ((ContainerNetworkDescription) item).external != Boolean.TRUE;
                }).collect(Collectors.toList());

        Map<String, List<ContainerDescription>> componentsByNetwork = new HashMap<>();

        for (ResourceState cd : userNetworks) {
            componentsByNetwork.put(cd.name, new ArrayList<ContainerDescription>());
        }

        List<ContainerDescription> containers = compositeDescription.componentDescriptions.stream()
                .filter((item) -> {
                    return item.type.equals(ResourceType.CONTAINER_TYPE.getName());
                }).map((cd) -> {
                    return (ContainerDescription) Utils.fromJson(cd.componentJson,
                            CompositeComponentRegistry.metaByType(cd.type).descriptionClass);
                }).collect(Collectors.toList());

        for (ContainerDescription cd : containers) {
            // if the container has an external network, we should deploy according
            // the host of this container (the host(s) of the external network).
            boolean hasExternalNetwork = false;
            for (Entry<String, ServiceNetwork> net : cd.networks.entrySet()) {
                if (componentsByNetwork.get(net.getKey()) == null) {
                    hasExternalNetwork = true;
                    break;
                }
            }

            for (Entry<String, ServiceNetwork> net : cd.networks.entrySet()) {
                List<ContainerDescription> list = componentsByNetwork.get(net.getKey());
                if (list != null) {
                    if (hasExternalNetwork) {
                        list.add(0, cd);
                    } else {
                        list.add(cd);
                    }
                }
            }
        }

        for (List<ContainerDescription> componentsPerNetwork : componentsByNetwork.values()) {
            String dependency = componentsPerNetwork.get(0).name;
            for (int i = 1; i < componentsPerNetwork.size(); i++) {
                ContainerDescription cd = componentsPerNetwork.get(i);

                if (resourceNodesByName.get(dependency).dependsOn.contains(cd.name)) {
                    continue;
                }

                if (resourceNodesByName.get(cd.name).dependsOn == null) {
                    resourceNodesByName.get(cd.name).dependsOn = new HashSet<>();
                }
                resourceNodesByName.get(cd.name).dependsOn.add(dependency);
            }
        }
    }
}