/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.resources;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a firewall resource.
 * Note: This service is deprecated. Use {@link SecurityGroupService} instead.
 */
public class FirewallService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/firewalls";

    static final String[] PROTOCOL = { "tcp", "udp", "icmp" };

    /**
     * Firewall State document.
     */
    public static class FirewallState extends ResourceState {

        /**
         * Link to secrets. Required
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String authCredentialsLink;

        /**
         * The pool which this resource is a part of.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * The adapter to use to create the firewall.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;

        /**
         *  network that firewall will protect
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String networkDescriptionLink;

        /**
         *  incoming rules
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public List<Allow> ingress;

        /**
         *  outgoing rules
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public List<Allow> egress;

        /**
         * Represents a firewall allow rule.
         */
        public static class Allow {
            public String name;
            public String protocol;
            // IP range that rule will be applied to
            // expressed in CIDR notation
            public String ipRange;

            // port or port range for rule
            // ie. "22", "80", "1-65535"
            public List<String> ports;
        }
    }

    public FirewallService() {
        super(FirewallState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            FirewallState returnState = processInput(put);
            returnState.copyTenantLinks(getState(put));
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private FirewallState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        FirewallState state = op.getBody(FirewallState.class);
        validateState(state);
        return state;
    }

    public void validateState(FirewallState state) {
        Utils.validateState(getStateDescription(), state);

        if (state.networkDescriptionLink.isEmpty()) {
            throw new IllegalArgumentException(
                    "a network description link is required");
        }
        // for now require a minimum of one rule
        if (state.ingress.size() == 0) {
            throw new IllegalArgumentException(
                    "a minimum of one ingress rule is required");
        }
        if (state.egress.size() == 0) {
            throw new IllegalArgumentException(
                    "a minimum of one egress rule is required");
        }

        if (state.regionId.isEmpty()) {
            throw new IllegalArgumentException("regionId required");
        }

        if (state.authCredentialsLink.isEmpty()) {
            throw new IllegalArgumentException("authCredentialsLink required");
        }

        if (state.resourcePoolLink.isEmpty()) {
            throw new IllegalArgumentException("resourcePoolLink required");
        }

        if (state.instanceAdapterReference == null) {
            throw new IllegalArgumentException(
                    "instanceAdapterReference required");
        }

        validateRules(state.ingress);
        validateRules(state.egress);
    }

    /**
     * Ensure that the allow rules conform to standard firewall practices.
     */
    public static void validateRules(List<FirewallState.Allow> rules) {
        for (FirewallState.Allow rule : rules) {
            validateRuleName(rule.name);
            // validate protocol and convert to lower case
            rule.protocol = validateProtocol(rule.protocol);

            // IP range must be in CIDR notation
            // creating new SubnetUtils to validate
            new SubnetUtils(rule.ipRange);
            validatePorts(rule.ports);
        }
    }

    /*
     * validate port list
     */
    public static void validatePorts(List<String> ports) {
        if (ports == null || ports.size() == 0) {
            throw new IllegalArgumentException(
                    "an allow rule requires a minimum of one port, none supplied");
        }

        for (String port : ports) {

            String[] pp = port.split("-");
            if (pp.length > 2) {
                // invalid port range
                throw new IllegalArgumentException(
                        "invalid allow rule port range supplied");
            }
            int previousPort = 0;
            if (pp.length > 0) {
                for (String aPp : pp) {
                    try {
                        int iPort = Integer.parseInt(aPp);
                        if (iPort < 1 || iPort > 65535) {
                            throw new IllegalArgumentException(
                                    "allow rule port numbers must be between 1 and 65535");
                        }
                        if (previousPort > 0 && previousPort > iPort) {
                            throw new IllegalArgumentException(
                                    "allow rule from port is greater than to port");
                        }
                        previousPort = iPort;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "allow rule port numbers must be between 1 and 65535");
                    }
                }
            }
        }
    }

    /*
     * Ensure rule name is populated
     */
    public static void validateRuleName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("a rule name is required");
        }
    }

    /*
     * Protocol must be tcp, udp or icmpi
     */
    public static String validateProtocol(String protocol) {

        if (protocol == null || protocol.isEmpty()) {
            throw new IllegalArgumentException(
                    "only tcp, udp or icmp protocols are supported, none supplied");
        }

        String proto = protocol.toLowerCase();

        if (!Arrays.asList(PROTOCOL).contains(proto)) {
            throw new IllegalArgumentException(
                    "only tcp, udp or icmp protocols are supported, provide a supported protocol");
        }
        return proto;
    }

    @Override
    public void handlePatch(Operation patch) {
        FirewallState currentState = getState(patch);
        Function<Operation, Boolean> customPatchHandler = new Function<Operation, Boolean>() {
            @Override
            public Boolean apply(Operation t) {
                FirewallState patchBody = patch.getBody(FirewallState.class);
                boolean hasStateChanged = false;
                // allow rules are overwritten -- it's not a merge
                // will result in a new version of the service on every call
                // as ingress & egress are never null
                if (patchBody.ingress != null) {
                    currentState.ingress = patchBody.ingress;
                    hasStateChanged = true;
                }

                if (patchBody.egress != null) {
                    currentState.egress = patchBody.egress;
                    hasStateChanged = true;
                }
                return hasStateChanged;
            }
        };
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(), FirewallState.class,
                customPatchHandler);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        FirewallState template = (FirewallState) td;
        template.id = UUID.randomUUID().toString();
        template.name = "firewall-one";

        return template;
    }
}
