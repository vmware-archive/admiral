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

package com.vmware.admiral.request.allocation.filter;

import static com.vmware.admiral.compute.container.ContainerService.ContainerState.FIELD_NAME_POWER_STATE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} aimed to provide host selection in case the
 * {@link ContainerDescription} specifies <code>ports</code> property. Based on the hostPorts
 * specified in <code>ports</code> the selection algorithm will filter out all docker hosts that
 * run containers with the same hostPorts. Basically this is a hard anti-affinity constraint, implicit
 * between the containers exposing the same host port.
 */
public class ExposedPortsHostFilter
        implements HostSelectionFilter<PlacementHostSelectionTaskState> {
    private static final String HOST_PORT_QUERY_SELECTION = String.format("%s.item.%s",
            ContainerState.FIELD_NAME_PORTS, PortBinding.FIELD_NAME_HOST_PORT);

    private final ServiceHost host;
    private final Set<String> descExposedPorts;

    public ExposedPortsHostFilter(ServiceHost host, ContainerDescription desc) {
        this.host = host;
        this.descExposedPorts = getExposedPorts(desc.portBindings);
    }

    @Override
    public void filter(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        if (descExposedPorts.isEmpty()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        QueryTask q = QueryUtil.buildQuery(ContainerState.class, false);
        QueryUtil.addListValueClause(q, ContainerState.FIELD_NAME_PARENT_LINK,
                hostSelectionMap.keySet());

        //Get only containers with hostPorts that match those from the description
        QueryUtil.addListValueClause(q, HOST_PORT_QUERY_SELECTION, descExposedPorts);

        //Get only powered on containers or those being provisioned
        QueryUtil.addListValueClause(q, FIELD_NAME_POWER_STATE,
                Arrays.asList(PowerState.RUNNING.toString(), PowerState.PROVISIONING.toString()));

        QueryUtil.addExpandOption(q);

        ServiceDocumentQuery<ContainerState> query = new ServiceDocumentQuery<ContainerState>(
                host, ContainerState.class);
        List<ContainerState> containerStates = new ArrayList<>();
        QueryUtil.addBroadcastOption(q);
        query.query(q, (r) -> {
            if (r.hasException()) {
                throw new HostSelectionFilterException("Error querying for container states.",
                        "request.exposed-ports.filter.containers.query.error");
            } else if (r.hasResult()) {
                containerStates.add(r.getResult());
            } else {
                for (ContainerState cs : containerStates) {
                    hostSelectionMap.remove(cs.parentLink);
                }

                if (hostSelectionMap.isEmpty()) {
                    String errMsg = String.format(
                            "No compute hosts found with unexposed ports %s.",
                            descExposedPorts.toString());
                    callback.complete(null, new HostSelectionFilterException(errMsg,
                            "request.exposed-ports.filter.compute-hosts.unavailable", descExposedPorts.toString()));
                } else {
                    callback.complete(hostSelectionMap, null);
                }
            }
        });
    }

    @Override
    public boolean isActive() {
        return !descExposedPorts.isEmpty();
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return Collections.emptyMap();
    }

    private Set<String> getExposedPorts(PortBinding[] exposedPorts) {
        if (exposedPorts == null) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        for (PortBinding port : exposedPorts) {
            if (port != null && port.hostPort != null && !port.hostPort.isEmpty()) {
                result.add(port.hostPort);
            }
        }
        return result;
    }
}
