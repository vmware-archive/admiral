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

package com.vmware.admiral.request.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

public class ComputeStateUtils {

    public static DeferredResult<Void> patchSubnetsNicsAndDescriptions(ServiceHost host, Set<String> resourceLinks, Set<String> addresses, String subnetName) {

        QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addFieldClause("name", subnetName);

        QueryTask q = QueryTask.Builder.create().setQuery(queryBuilder.build()).build();
        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
        QueryUtil.addExpandOption(q);

        DeferredResult<Void> ret = new DeferredResult<>();
        //It is expected one particular subnet provided by client.
        final SubnetState[] subnet = new SubnetState[1];

        new ServiceDocumentQuery<>(host, SubnetState.class).query(q, (r) -> {
            if (r.hasException()) {
                host.log(Level.WARNING,
                        "Exception while quering SubnetState with name [%s]. Error: [%s]",
                        subnetName, r.getException().getMessage());
                ret.fail(r.getException());
            } else if (r.hasResult()) {
                subnet[0] = r.getResult();
            } else {
                if (subnet.length == 0) {
                    ret.fail(new IllegalStateException("No subnets with name [%s] found."));
                    return;
                }
                /**
                 * 1. Get ComputeStates in order to retrieve their {@link NetworkInterfaceState}
                 * 2. Link {@link NetworkInterfaceState} to above {@link SubnetState}
                 * 3. Link {@link NetworkInterfaceDescription} to above {@link SubnetState}
                 * 4. Resume the task
                 */
                getNicStates(host, resourceLinks)
                        .thenCompose(nicStates -> patchNicStates(host, nicStates,
                                addresses, subnet[0]))
                        .thenCompose(nicDesciptions -> patchNicDescriptions(host,
                                nicDesciptions, addresses, subnet[0])
                        ).whenComplete((result, exception) -> {
                            if (exception != null) {
                                ret.fail(exception);
                            } else {
                                // Finished with NicDescriptions => invoke callback and resume the task now.
                                ret.complete(null);
                            }
                        });
            }
        });

        return ret;
    }

    private static DeferredResult<Set<String>> patchNicStates(ServiceHost host, Set<String> nicStates, Set<String> staticIps, SubnetState subnet) {

        Iterator<String> ipIterator = staticIps.iterator();

        List<DeferredResult<NetworkInterfaceState>> results = nicStates.stream()
                /**
                 * Filter base on size of ip list and number of NICs. For example:
                 * 1) IP list: [1,2,3], Computes: [A,B] => A[1], B[2]; ip 3 - will be ignored.
                 * 2) IP list: [1,2], Computes: [A,B,C] => A[1], B[2]; Compute C - will be ignored.
                 */
                .filter(nic -> ipIterator.hasNext())
                .map(nicSelfLink -> patchNicStateOperation(host, subnet, ipIterator.next(),
                        nicSelfLink))
                .map(o -> o.setReferer(host.getUri()))
                .map(o -> host.sendWithDeferredResult(o, NetworkInterfaceState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(results).thenApply(nics -> nics.stream().map(nic -> nic
                .networkInterfaceDescriptionLink)
                .collect(Collectors.toSet()));

    }

    private static DeferredResult<List<NetworkInterfaceDescription>> patchNicDescriptions(
            ServiceHost host,
            Set<String> nicDescriptions,
            Set<String> staticIps, SubnetState subnet) {

        Iterator<String> ipIterator = staticIps.iterator();

        List<DeferredResult<NetworkInterfaceDescription>> results = nicDescriptions.stream()
                .filter(nic -> ipIterator.hasNext())
                .map(nicSelfLink -> patchNicDescriptionOperation(host, ipIterator.next(),
                        nicSelfLink))
                .map(o -> o.setReferer(host.getUri()))
                .map(o -> host.sendWithDeferredResult(o, NetworkInterfaceDescription.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(results);
    }

    public static Operation patchNicDescriptionOperation(ServiceHost host, String staticIp, String nicDescLink) {

        NetworkInterfaceDescription patch = new NetworkInterfaceDescription();
        patch.assignment = IpAssignment.STATIC;
        patch.address = staticIp;

        return Operation.createPatch(host, nicDescLink)
                .setBody(patch)
                .setReferer(host.getUri());
    }

    public static Operation patchNicStateOperation(ServiceHost host, SubnetState subnet, String
            staticIp, String nicLink) {

        NetworkInterfaceState patch = new NetworkInterfaceState();
        patch.networkLink = null;
        patch.subnetLink = subnet.documentSelfLink;
        patch.address = staticIp;

        return (Operation.createPatch(host, nicLink)
                .setBody(patch)
                .setReferer(host.getUri()));

    }

    private static DeferredResult<Set<String>> getNicStates(ServiceHost
            host, Set<String> resourceLinks) {

        List<DeferredResult<ComputeState>> results = resourceLinks.stream()
                .map(c -> Operation.createGet(host, c))
                .map(o -> o.setReferer(host.getUri()))
                .map(o -> host.sendWithDeferredResult(o, ComputeState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(results).thenApply(computes ->
                /**
                 * Get NIC links from Compute(s) with only one (default) NIC.
                 */
                computes.stream()
                        .filter(c -> c.networkInterfaceLinks != null &&
                                c.networkInterfaceLinks.size() == 1)
                        .map(c -> c.networkInterfaceLinks.get(0))
                        .collect(Collectors.toSet())
        );
    }
}
