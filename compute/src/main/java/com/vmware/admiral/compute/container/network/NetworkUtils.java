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

package com.vmware.admiral.compute.container.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.logging.Level;

import io.netty.util.internal.StringUtil;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ConnectedContainersCountIncrement;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState.PowerState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class NetworkUtils {

    /**
     * Merge strategy that will overwrite the target if it is not a map and the source is non null.
     *
     * It will not recurse into merging individual fields of complex objects
     */
    public static final BinaryOperator<Object> SHALLOW_MERGE_SKIP_MAPS_STRATEGY = (copyTo,
            copyFrom) -> {
        if (copyTo instanceof Map) {
            return copyTo;
        }
        return PropertyUtils.mergeProperty(copyTo, copyFrom);
    };

    public static final String ERROR_NETWORK_NAME_IS_REQUIRED = "Network name is required.";
    public static final String FORMAT_IP_VALIDATION_ERROR = "Specified input "
            + "is not a valid IP address: %s";
    public static final String FORMAT_CIDR_NOTATION_VALIDATION_ERROR = "Specified input "
            + "is not a valid CIDR notation: %s";

    /**
     * Matches IPv4 addresses specified in form [0-255].[0-255].[0-255].[0-255]
     */
    public static final String REGEXP_IPV4_ADDRESS = "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}"
            + "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d";

    /**
     * Matches all valid IPv6 text representations as specified in
     * https://tools.ietf.org/html/rfc4291#section-2.2
     */
    public static final String REGEXP_IPV6_ADDRESS = "(([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|"

            + "(([0-9A-Fa-f]{1,4}:){6}"
            + "(:[0-9A-Fa-f]{1,4}|"
            + "((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)|:))|"

            + "(([0-9A-Fa-f]{1,4}:){5}"
            + "(:((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)|"
            + "((:[0-9A-Fa-f]{1,4}){1,2})|:))|"

            + "(([0-9A-Fa-f]{1,4}:){4}"
            + "(((:[0-9A-Fa-f]{1,4})?"
            + ":((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d))|"
            + "((:[0-9A-Fa-f]{1,4}){1,3})|:))|"

            + "(([0-9A-Fa-f]{1,4}:){3}"
            + "(((:[0-9A-Fa-f]{1,4}){0,2}:"
            + "((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d))|"
            + "((:[0-9A-Fa-f]{1,4}){1,4})|:))|"

            + "(([0-9A-Fa-f]{1,4}:){2}"
            + "(((:[0-9A-Fa-f]{1,4}){0,3}:"
            + "((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d))|"
            + "((:[0-9A-Fa-f]{1,4}){1,5})|:))|"

            + "(([0-9A-Fa-f]{1,4}:)"
            + "(((:[0-9A-Fa-f]{1,4}){0,4}:"
            + "((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d))|"
            + "((:[0-9A-Fa-f]{1,4}){1,6})|:))|"

            + "(:"
            + "(((:[0-9A-Fa-f]{1,4}){0,5}:"
            + "((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d))|"
            + "((:[0-9A-Fa-f]{1,4}){1,7})|:))";

    /**
     * Matches an IPv4 network mask or IP range specified in CIDR notation
     */
    public static final String REGEXP_IPV4_CIDR_NOTATION = "(" + REGEXP_IPV4_ADDRESS
            + ")\\/(3[0-2]|[1-2]?[0-9])";

    /**
     * Matches an IPv6 network mask or IP range specified in CIDR notation
     */
    public static final String REGEXP_IPV6_CIDR_NOTATION = "(" + REGEXP_IPV6_ADDRESS
            + ")\\/(12[0-8]|1[0-1]\\d|\\d{1,2})";

    /**
     * Matches an IPv4 or IPv6 address
     */
    public static final String REGEXP_IP_ADDRESS = "("
            + REGEXP_IPV4_ADDRESS + ")|("
            + REGEXP_IPV6_ADDRESS + ")";

    public static final String REGEXP_CIDR_NOTATION = "("
            + REGEXP_IPV4_CIDR_NOTATION + ")|("
            + REGEXP_IPV6_CIDR_NOTATION + ")";

    public static void validateIpCidrNotation(String subnet) {
        if (!StringUtil.isNullOrEmpty(subnet) && !subnet.matches(REGEXP_CIDR_NOTATION)) {
            String error = String.format(
                    FORMAT_CIDR_NOTATION_VALIDATION_ERROR,
                    subnet);
            throw new LocalizableValidationException(error, "compute.network.validate.cidr", subnet);
        }
    }

    public static void validateIpAddress(String gateway) {
        if (!StringUtil.isNullOrEmpty(gateway) && !gateway.matches(REGEXP_IP_ADDRESS)) {
            String error = String.format(FORMAT_IP_VALIDATION_ERROR,
                    gateway);
            throw new LocalizableValidationException(error, "compute.network.validate.ip", gateway);
        }
    }

    public static void validateNetworkName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new LocalizableValidationException(ERROR_NETWORK_NAME_IS_REQUIRED, "compute.network.validate.name");
        }
        // currently, it looks like there are no restrictions on the network name from docker side.
        // Numbers-only names and even space-delimited words are supported. We can add some
        // restrictions here.
    }

    public static ContainerNetworkDescription createContainerNetworkDescription(
            ContainerNetworkState state) {

        ContainerNetworkDescription networkDescription = new ContainerNetworkDescription();

        networkDescription.documentSelfLink = state.descriptionLink;
        networkDescription.documentDescription = state.documentDescription;
        networkDescription.tenantLinks = state.tenantLinks;
        networkDescription.instanceAdapterReference = state.adapterManagementReference;
        networkDescription.name = state.name;
        networkDescription.id = state.id;
        networkDescription.customProperties = state.customProperties;

        // TODO - fill in other network settings

        return networkDescription;
    }

    public static String buildNetworkLink(String name) {
        return UriUtils.buildUriPath(ContainerNetworkService.FACTORY_LINK, buildNetworkId(name));
    }

    public static String buildNetworkId(String name) {
        return name.replaceAll(" ", "-");
    }

    public static void updateConnectedNetworks(ServiceHost host, ContainerState container,
            int increment) {
        Map<String, ServiceNetwork> networks = container.networks;
        if (networks == null || networks.isEmpty()) {
            return;
        }

        networks.keySet().stream().forEach(name -> {
            ConnectedContainersCountIncrement patchBody = new ConnectedContainersCountIncrement();
            patchBody.increment = increment;

            patchConnectedContainersCountIncrement(host, name, patchBody, container);
        });
    }

    private static void patchConnectedContainersCountIncrement(ServiceHost host, String networkName,
            ConnectedContainersCountIncrement patchBody, ContainerState container) {

        String networkLink = buildNetworkLink(networkName);

        host.sendRequest(Operation.createPatch(UriUtils.buildUri(host, networkLink))
                .setReferer(host.getUri())
                .setBody(patchBody)
                .setCompletion((o, e) -> {
                    if (e == null) {
                        host.log(Level.FINE,
                                "Updated connected containers count for ContainerNetworkState: %s ",
                                networkName);
                        return;
                    }

                    if (container == null) {
                        // retry failed
                        host.log(Level.WARNING,
                                "Error updating connected containers count for ContainerNetworkState: %s (%s)",
                                networkName, e.getMessage());
                        return;
                    }

                    // retry by name...

                    List<ContainerNetworkState> networkStates = new ArrayList<ContainerNetworkState>();

                    QueryTask queryTask = NetworkUtils.getNetworkByHostAndNameQueryTask(
                            container.parentLink, networkName);

                    new ServiceDocumentQuery<ContainerNetworkState>(host,
                            ContainerNetworkState.class).query(queryTask, (r) -> {
                                if (r.hasException()) {
                                    host.log(Level.WARNING,
                                            "Failed to query for active networks by name '%s' in host '%s'! %s",
                                            networkName, container.parentLink,
                                            Utils.toString(r.getException()));
                                } else if (r.hasResult()) {
                                    networkStates.add(r.getResult());
                                } else {
                                    if (networkStates.size() == 1) {
                                        patchConnectedContainersCountIncrement(host,
                                                networkStates.get(0).id, patchBody, null);
                                        return;
                                    }
                                    host.log(Level.WARNING,
                                            "%s active network(s) found by name '%s' in host '%s'!",
                                            networkStates.size(), networkName,
                                            container.parentLink);
                                }
                            });
                }));
    }

    public static QueryTask getNetworkByHostAndNameQueryTask(String hostLink, String networkName) {

        QueryTask queryTask = QueryUtil.buildQuery(ContainerNetworkState.class, true);

        String parentLinksItemField = QueryTask.QuerySpecification
                .buildCollectionItemName(ContainerNetworkState.FIELD_NAME_PARENT_LINKS);
        QueryTask.Query parentsClause = new QueryTask.Query()
                .setTermPropertyName(parentLinksItemField)
                .setTermMatchValue(hostLink)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);

        QueryTask.Query nameClause = new QueryTask.Query()
                .setTermPropertyName(ContainerNetworkState.FIELD_NAME_NAME)
                .setTermMatchValue(networkName)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);

        QueryTask.Query stateClause = new QueryTask.Query()
                .setTermPropertyName(ContainerNetworkState.FIELD_NAME_POWER_STATE)
                .setTermMatchValue(PowerState.CONNECTED.toString())
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);

        Query intermediate = new QueryTask.Query().setOccurance(Occurance.MUST_OCCUR);
        intermediate.addBooleanClause(parentsClause);
        intermediate.addBooleanClause(nameClause);
        intermediate.addBooleanClause(stateClause);

        queryTask.querySpec.query.addBooleanClause(intermediate);

        QueryUtil.addExpandOption(queryTask);
        QueryUtil.addBroadcastOption(queryTask);

        return queryTask;
    }
}
