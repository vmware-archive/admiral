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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class NetworkInterfaceDescriptionEnhancer extends ComputeDescriptionEnhancer {

    private static final String DEFAULT_SECURITY_GROUP_NAME = "VMware Prelude security group";
    private static final List<Pair<String, Integer>> DEFAULT_ALLOWED_PORTS = Arrays.asList(
            Pair.of("ssh", 22), Pair.of("https", 443), Pair.of("http", 80),
            Pair.of("docker-ssh", 2376), Pair.of("docker", 2375), Pair.of("all", 1));

    private static final String DEFAULT_ALLOWED_NETWORK = "0.0.0.0/0";
    private static final String ALL_PORTS = "1-65535";
    private static final String DEFAULT_PROTOCOL = "tcp";

    private ServiceHost host;
    private URI referer;

    public NetworkInterfaceDescriptionEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {
        if (context.skipNetwork || cd.networkInterfaceDescLinks == null
                || cd.networkInterfaceDescLinks.isEmpty()) {
            return DeferredResult.completed(cd);
        }

        return getPrimaryNetworkInterfaceDesc(cd).thenCompose(nid -> {
            DeferredResult<ComputeDescription> result = new DeferredResult<>();
            if (nid.securityGroupLinks == null || nid.securityGroupLinks.isEmpty()) {
                applyDefaultSecurityGroup(context, nid, result, cd);
            } else {
                result.complete(cd);
            }
            return result;
        });
    }

    private void applyDefaultSecurityGroup(EnhanceContext context, NetworkInterfaceDescription nid,
            DeferredResult<ComputeDescription> result, ComputeDescription cd) {

        queryDefaultSecurityGroup(context, cd)
                .thenCompose(sgLink -> {
                    if (sgLink == null) {
                        SecurityGroupState sg = new SecurityGroupState();
                        sg.name = DEFAULT_SECURITY_GROUP_NAME;
                        sg.tenantLinks = nid.tenantLinks;

                        sg.ingress = getDefaultRules(DEFAULT_ALLOWED_NETWORK);

                        sg.egress = new ArrayList<>();
                        sg.egress.add(createRule("out", ALL_PORTS, DEFAULT_ALLOWED_NETWORK,
                                DEFAULT_PROTOCOL));

                        sg.regionId = context.regionId;
                        sg.resourcePoolLink = context.resourcePoolLink;
                        sg.endpointLink = context.endpointLink;
                        sg.instanceAdapterReference = UriUtils.buildUri(host,
                                UriPaths.AdapterTypePath.FIREWALL_ADAPTER
                                        .adapterLink(context.endpointType));
                        return host.sendWithDeferredResult(
                                Operation.createPost(host, SecurityGroupService.FACTORY_LINK)
                                        .setBody(sg).setReferer(referer),
                                SecurityGroupState.class)
                                .thenCompose(s -> DeferredResult.completed(s.documentSelfLink));
                    } else {
                        return DeferredResult.completed(sgLink);
                    }
                })
                .thenCompose(sLink -> {
                    NetworkInterfaceDescription n = new NetworkInterfaceDescription();
                    n.securityGroupLinks = new ArrayList<>();
                    n.securityGroupLinks.add(sLink);
                    return host.sendWithDeferredResult(
                            Operation.createPatch(host, nid.documentSelfLink)
                                    .setBody(n)
                                    .setReferer(referer),
                            NetworkInterfaceDescription.class);
                }).whenComplete((n, e) -> {
                    if (e != null) {
                        result.fail(e);
                        return;
                    }
                    result.complete(cd);
                });
    }

    public static List<Rule> getDefaultRules(String subnet) {
        List<Rule> rules = new ArrayList<>();
        for (Pair<String, Integer> r : DEFAULT_ALLOWED_PORTS) {
            if (r.getRight() > 1) {
                rules.add(createRule(r.getLeft(), r.getRight().toString()));
            } else {
                rules.add(createRule(r.getLeft(), ALL_PORTS, subnet, DEFAULT_PROTOCOL));
            }
        }
        return rules;
    }

    public static Rule createRule(String name, String port) {
        return createRule(name, port, DEFAULT_ALLOWED_NETWORK, DEFAULT_PROTOCOL);
    }

    public static Rule createRule(String name, String ports, String subnet,
            String protocol) {

        Rule r = new Rule();
        r.name = name;
        r.protocol = protocol;
        r.ipRangeCidr = subnet;
        r.ports = ports;

        return r;
    }

    private DeferredResult<NetworkInterfaceDescription> getPrimaryNetworkInterfaceDesc(
            ComputeDescription cd) {
        List<DeferredResult<NetworkInterfaceDescription>> nidDR = cd.networkInterfaceDescLinks
                .stream()
                .map(nidLink -> host.sendWithDeferredResult(
                        Operation.createGet(UriUtils.buildUri(host, nidLink)).setReferer(referer),
                        NetworkInterfaceDescription.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(nidDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format("Error getting NID states for [%s] VM.", cd.name);
                throw new IllegalStateException(msg, exc);
            }

            return all.stream()
                    .sorted((n1, n2) -> Integer.compare(n1.deviceIndex, n2.deviceIndex))
                    .findFirst().orElse(null);
        });
    }

    private DeferredResult<String> queryDefaultSecurityGroup(EnhanceContext context,
            ComputeDescription cd) {
        Query query = Query.Builder.create()
                .addKindFieldClause(SecurityGroupState.class)
                .addClause(QueryUtil.addTenantClause(cd.tenantLinks))
                .addFieldClause(SecurityGroupState.FIELD_NAME_NAME, DEFAULT_SECURITY_GROUP_NAME)
                .addFieldClause(SecurityGroupState.FIELD_NAME_REGION_ID, context.regionId)
                .build();

        if (context.endpointLink != null && !context.endpointLink.isEmpty()) {
            query.addBooleanClause(Query.Builder.create()
                    .addFieldClause(NetworkState.FIELD_NAME_ENDPOINT_LINK, context.endpointLink)
                    .build());
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.TOP_RESULTS)
                .setQuery(query)
                .setResultLimit(20)
                .build();
        queryTask.tenantLinks = cd.tenantLinks;

        return host.sendWithDeferredResult(
                Operation.createPost(host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                        .setBody(queryTask)
                        .setReferer(referer)
                        .setConnectionSharing(true),
                QueryTask.class)
                .thenCompose(q -> {
                    DeferredResult<String> r = new DeferredResult<>();
                    if (queryTask.results != null && queryTask.results.documentLinks != null) {
                        r.complete(
                                queryTask.results.documentLinks.stream().findFirst().orElse(null));
                    } else {
                        r.complete(null);
                    }
                    host.log(Level.FINE, () -> String.format("%d security group states found.",
                            queryTask.results.documentCount));
                    return r;
                });
        // .exceptionally(e->{
        // host.log(Level.SEVERE,() -> String.format("Failed retrieving query results: %s",
        // e.toString()));
        // return DeferredResult.completed((String)null);
        // });
    }

}
