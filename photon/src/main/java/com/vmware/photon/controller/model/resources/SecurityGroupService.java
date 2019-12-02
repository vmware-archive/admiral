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
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a security group resource.
 */
public class SecurityGroupService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_SECURITY_GROUPS;

    public enum Protocol {

        ANY(SecurityGroupService.ANY, 0), TCP("tcp", 6), UDP("udp", 17), ICMPv4("icmp",
                1), ICMPv6("icmpv6", 58);

        private final int protocolNumber;
        private final String name;

        Protocol(String name, int protocolNumber) {
            this.protocolNumber = protocolNumber;
            this.name = name;
        }

        public int getProtocolNumber() {
            return this.protocolNumber;
        }

        public String getName() {
            return this.name;
        }

        /**
         * Obtain the enumeration choice that corresponds to the provided String which either equal
         * the name or the ProtocolNumber of the choice
         */
        public static Protocol fromString(String s) {
            AssertUtil.assertNotNull(s, "'protocol' must not be null");
            Predicate<Protocol> namePredicate = protocol -> protocol.getName().equals(s);
            Predicate<Protocol> protocolNumberPredicate = protocol -> {
                try {
                    return protocol.getProtocolNumber() == Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return false;
                }
            };
            Predicate<Protocol> fullPredicate = namePredicate.or(protocolNumberPredicate);
            return Arrays.stream(values())
                    .filter(fullPredicate)
                    .findFirst().orElse(null);
        }

        /**
         * Protocol must be one of the supported types.
         */
        public static Protocol validateProtocol(String protocol) {

            if (protocol == null || protocol.isEmpty()) {
                throw new IllegalArgumentException(
                        "Provided protocol is empty."
                                + " Supported protocols: " + Arrays.toString(Protocol.values()));
            }

            Protocol protocolEnumVal = Protocol.fromString(protocol.toLowerCase());
            if (protocolEnumVal == null) {
                throw new IllegalArgumentException(
                        "Protocol is not correctly specified " + protocol.toLowerCase()
                                + " Supported protocols: " + Arrays.toString(Protocol.values()));
            }

            return protocolEnumVal;
        }
    }

    /**
     * ANY can be used when when specifying protocol or IP range in a security group rule.
     * <ul>
     * <li>protocol - the role is applicable to all protocols</li>
     * <li>IP range - the rule is applicable to any IP.</li>
     * </ul>
     */
    public static final String ANY = "*";

    /**
     * ALL_PORTS can be used for SecurityGroup rules, where all ports are allowed.
     */
    public static final String ALL_PORTS = "1-65535";

    /**
     * Security Group State document.
     */
    public static class SecurityGroupState extends ResourceState {
        public static final String FIELD_NAME_AUTH_CREDENTIAL_LINK = "authCredentialsLink";

        /**
         * Link to secrets. Required
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String authCredentialsLink;

        /**
         * The pool which this resource is a part of.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * The adapter to use to create the security group.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;

        /**
         * incoming rules
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public List<Rule> ingress;

        /**
         * outgoing rules
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public List<Rule> egress;

        /**
         * Link to the cloud account endpoint the disk belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        public String endpointLink;

        /**
         * Represents a security group rule.
         */
        public static class Rule {
            public String name;
            public String protocol;
            /**
             * IP range that rule will be applied to expressed in CIDR notation
             */
            public String ipRangeCidr;

            /**
             * port or port range for rule ie. "22", "80", "1-65535" -1 means all the ports for the
             * particular protocol
             */
            public String ports;

            /**
             * Gets or sets network traffic is allowed or denied. Default is Allow.
             */
            public Access access = Access.Allow;

            public enum Access {
                Allow, Deny
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == null) {
                    return false;
                }
                if (!Rule.class.isAssignableFrom(obj.getClass())) {
                    return false;
                }
                Rule newRule = (Rule) obj;
                if (this.protocol.equals(newRule.protocol) &&
                        this.access.equals(newRule.access) &&
                        this.ipRangeCidr.equals(newRule.ipRangeCidr) &&
                        this.ports.equals(newRule.ports)) {
                    return true;
                }
                return false;
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.access, this.ipRangeCidr, this.ports, this.protocol);
            }
        }
    }

    public SecurityGroupService() {
        super(SecurityGroupState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        processInput(start);
        start.complete();
    }

    @Override
    public void handlePut(Operation put) {
        SecurityGroupState returnState = processInput(put);
        returnState.copyTenantLinks(getState(put));
        setState(put, returnState);
        put.complete();
    }

    @Override
    public void handlePost(Operation post) {
        SecurityGroupState returnState = processInput(post);
        setState(post, returnState);
        post.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        SecurityGroupState currentState = getState(patch);
        Function<Operation, Boolean> customPatchHandler = t -> {
            SecurityGroupState patchBody = patch.getBody(SecurityGroupState.class);
            boolean hasStateChanged = false;
            // rules are overwritten -- it's not a merge
            // if a new set of rules are input they will be overwritten
            if (patchBody.ingress != null) {
                if (currentState.ingress == null
                        || !areRuleListsEqual(currentState.ingress, patchBody.ingress)) {
                    currentState.ingress = patchBody.ingress;
                    hasStateChanged = true;
                }
            }
            if (patchBody.egress != null) {
                if (currentState.egress == null
                        || !areRuleListsEqual(currentState.egress, patchBody.egress)) {
                    currentState.egress = patchBody.egress;
                    hasStateChanged = true;
                }
            }
            if (patchBody.endpointLink != null && currentState.endpointLink == null) {
                currentState.endpointLink = patchBody.endpointLink;
                hasStateChanged = true;
            }
            return hasStateChanged;
        };
        ResourceUtils
                .handlePatch(patch, currentState, getStateDescription(), SecurityGroupState.class,
                        customPatchHandler);
    }

    // check if the sourceList and destList have the same elements in any order
    private boolean areRuleListsEqual(List<Rule> sourceList, List<Rule> destList) {
        if (sourceList.size() != destList.size()) {
            return false;
        }
        for (Rule sourceRule : sourceList) {
            if (!destList.contains(sourceRule)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        SecurityGroupState template = (SecurityGroupState) td;
        template.id = UUID.randomUUID().toString();
        template.name = "security-group-one";

        return template;
    }

    private SecurityGroupState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        SecurityGroupState state = op.getBody(SecurityGroupState.class);
        validateState(state);
        return state;
    }

    private void validateState(SecurityGroupState state) {
        Utils.validateState(getStateDescription(), state);
        PhotonModelUtils.validateRegionId(state);

        if (state.regionId.isEmpty()) {
            throw new IllegalArgumentException("regionId required");
        }

        if (state.resourcePoolLink.isEmpty()) {
            throw new IllegalArgumentException("resourcePoolLink required");
        }

        validateRules(state.ingress);
        validateRules(state.egress);
    }

    /**
     * Ensure that the rules conform to standard security group practices.
     */
    private static void validateRules(List<Rule> rules) {
        for (Rule rule : rules) {

            validateRuleName(rule.name);

            Protocol ruleProtocol = Protocol.validateProtocol(rule.protocol);

            // IP range must be in CIDR notation or "*".
            // creating new SubnetUtils to validate
            if (!ANY.equals(rule.ipRangeCidr)) {
                new SubnetUtils(rule.ipRangeCidr);
            }
            // port validation has no meaning for ICMP protocol
            if (!Protocol.ICMPv4.equals(ruleProtocol) &&
                    !Protocol.ICMPv6.equals(ruleProtocol)) {
                validatePorts(rule.ports);
            }
        }
    }

    /**
     * Validate ports
     */
    private static void validatePorts(String ports) {
        if (ports == null) {
            throw new IllegalArgumentException(
                    "minimum of one port is required, "
                            + "none supplied");
        }
        String[] pp = ports.split("-");
        if (pp.length > 2) {
            // invalid port range
            throw new IllegalArgumentException(
                    "invalid allow rule port range supplied " + ports);
        }
        int previousPort = 0;
        if (pp.length > 0) {
            for (String aPp : pp) {
                try {
                    int iPort = Integer.parseInt(aPp);
                    if (iPort < 0 || iPort > 65535) {
                        throw new IllegalArgumentException(
                                "port numbers must be between 0 and 65535 but was "
                                        + iPort);
                    }
                    if (previousPort > 0 && previousPort > iPort) {
                        throw new IllegalArgumentException(
                                "from port is greater than to port " + iPort);
                    }
                    previousPort = iPort;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Not a valid port: " + aPp);
                }
            }
        }
    }

    /*
     * Ensure rule name is populated
     */
    private static void validateRuleName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("a rule name is required");
        }
    }

}
