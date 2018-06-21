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

package com.vmware.photon.controller.model.resources;

import static com.vmware.photon.controller.model.resources.SubnetRangeService.SubnetRangeState.FIELD_NAME_SUBNET_LINK;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.SubnetValidator;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Represents a range of IP addresses, assigned statically or by DHCP.
 * Reserved IP addresses should not be part of the range (for example, broadcast IP)
 *
 * @see SubnetService.SubnetState
 */
public class SubnetRangeService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/subnet-ranges";

    /**
     * Represents the state of a subnet.
     */
    public static class SubnetRangeState extends ResourceState {

        public static final String FIELD_NAME_SUBNET_LINK = "subnetLink";

        /**
         * Link to the subnet this subnet range is part of.
         */
        @Documentation(description = "Link to the parent subnet.")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT,
                ServiceDocumentDescription.PropertyUsageOption.LINK
                })
        public String subnetLink;

        /**
         * Start IP address.
         */
        @Documentation(description = "Start IP address of the range")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public String startIPAddress;

        /**
         * End IP address.
         */
        @Documentation(description = "End IP address of the range")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public String endIPAddress;

        /**
         * Whether the start and end IP address is IPv4 or IPv6.
         * Default value IPv4.
         */
        @Documentation(description = "IP address version: IPv4 or IPv6. Default: IPv4")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public IPVersion ipVersion;

        /**
         * Whether this IP range is managed by a DHCP server or static allocation.
         * If not set, default to false.
         */
        @Documentation(description = "Indication if the range is managed by DHCP. Default: false.")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public Boolean isDHCP;

        /**
         * DNS IP addresses for this subnet range.
         * May override the SubnetState values.
         */
        @Documentation(description = "DNS server addresses")
        @PropertyOptions(
                usage = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> dnsServerAddresses;

        /**
         * DNS domain of the subnet range.
         * May override the SubnetState values.
         */
        @PropertyOptions(
                usage = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String domain;

        /**
         * DNS domain search.
         * May override the SubnetState values.
         */
        @PropertyOptions(
                usage = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> dnsSearchDomains;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("name: ").append(this.name);
            sb.append(", id: ").append(this.id);
            sb.append(", subnet: ").append(this.subnetLink);
            sb.append(", start IP address: ").append(this.startIPAddress);
            sb.append(", end IP address: ").append(this.endIPAddress);
            sb.append(", IP version: ").append(this.ipVersion);
            sb.append(", is DHCP: ").append(this.isDHCP);
            sb.append(", domain: ").append(this.domain);

            return sb.toString();
        }
    }

    public SubnetRangeService() {
        super(SubnetRangeState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    /**
     * Comes in here for a http post for a new document
     */
    @Override
    public void handleCreate(Operation create) {
        try {

            SubnetRangeState subnetRangeState = getOperationBody(create);
            validateAll(subnetRangeState)
                    .whenCompleteNotify(create);
        } catch (Throwable t) {
            create.fail(t);
        }
    }

    /**
     * Comes in here for a http put on an existing doc
     */
    @Override
    public void handlePut(Operation put) {
        try {
            SubnetRangeState subnetRangeState = getOperationBody(put);

            validateAll(subnetRangeState)
                    .thenAccept((ignored) -> setState(put, subnetRangeState))
                    .whenCompleteNotify(put);
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    /**
     * Comes in here for a http patch on an existing doc
     * For patch only the values being changed are sent.
     * getState() method fills in the missing values from the existing doc.
     */
    @Override
    public void handlePatch(Operation patch) {
        checkHasBody(patch);

        try {
            SubnetRangeState currentState = getState(patch);

            // Merge the patch values to current state
            // In order to validate the merged result
            EnumSet<Utils.MergeResult> mergeResult =
                    Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                            SubnetRangeState.class, patch);

            boolean hasStateChanged = mergeResult.contains(Utils.MergeResult.STATE_CHANGED);

            if (hasStateChanged) {
                validateAll(currentState)
                        .thenAccept((ignored) -> setState(patch, currentState))
                        .whenCompleteNotify(patch);
            } else {
                patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
                patch.complete();
            }

        } catch (Exception e) {
            this.logSevere(String.format("SubnetRangeService: failed to perform patch [%s]",
                    e.getMessage()));
            patch.fail(e);
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        SubnetRangeState template = (SubnetRangeState) td;

        template.id = UUID.randomUUID().toString();
        template.name = "subnet-range";

        return template;
    }

    /**
     * Do all validations on the ip address before the data is committed.
     * In case of any validation error an exception is raised.
     *
     * @param subnetRangeState
     * @return A deferred result with no data. The return just means no exception was raised
     * hence its okay to proceed.
     */
    private DeferredResult<Void> validateAll(SubnetRangeState subnetRangeState) {

        validateState(subnetRangeState);

        return DeferredResult
                .allOf(
                        validateIps(subnetRangeState),
                        validateNoRangeOverlap(subnetRangeState)
                );
    }

    /**
     * Returns a Deferred result if start IP address and end IP address are within the network
     * specfied  by the subnet CIDR. If not an exception is raised in the method this calls.
     *
     * @param subnetRangeState The subnet state that is being validated.
     * @return a deferred result, that has no data. This method returning just validates the data.
     */
    private DeferredResult<Void> validateIps(SubnetRangeState subnetRangeState) {
        if (subnetRangeState.subnetLink != null) {
            return getSubnetState(subnetRangeState.subnetLink)
                    .thenAccept((op) -> {
                        SubnetState subnetState = op.getBody(SubnetState.class);
                        validateIpInRange(subnetRangeState, subnetState);
                    });
        }
        return DeferredResult.completed(null);
    }

    /**
     * Returns a Deferred result if there is no overlap of the current ip range with the pre
     * existing ip ranges. Else exception is raised (in the method this calls).
     *
     * @param subnetRangeState
     * @return A deferred result which indicates there was no range overlap.
     */
    private DeferredResult<Void> validateNoRangeOverlap(SubnetRangeState subnetRangeState) {
        if (subnetRangeState.subnetLink != null) {
            return getSubnetRangesInSubnet(subnetRangeState.subnetLink)
                    .thenAccept((subnetRangeList) -> {
                        validateIpsOutsideDefinedRanges(
                                subnetRangeState.documentSelfLink,
                                subnetRangeState.startIPAddress,
                                subnetRangeState.endIPAddress,
                                subnetRangeList);
                    });
        }
        return DeferredResult.completed(null);
    }

    /**
     * Validate:
     * - valid start IP address
     * - valid end IP address
     * - valid range
     *
     * @param state SubnetRangeState to validate
     */
    private void validateState(SubnetRangeState state) {
        Utils.validateState(getStateDescription(), state);

        if (!SubnetValidator.isValidIPAddress(state.startIPAddress, state.ipVersion)) {
            throw new LocalizableValidationException(
                    String.format("Invalid start IP address: %s",
                            state.startIPAddress),
                    "subnet.range.ip.invalid.start",
                    state.startIPAddress);
        }

        if (!SubnetValidator.isValidIPAddress(state.endIPAddress, state.ipVersion)) {
            throw new LocalizableValidationException(
                    String.format("Invalid end IP address: %s",
                            state.endIPAddress),
                    "subnet.range.ip.invalid.start",
                    state.endIPAddress);
        }

        if (SubnetValidator
                .isStartIPGreaterThanEndIP(state.startIPAddress, state.endIPAddress,
                        state.ipVersion)) {
            throw new LocalizableValidationException(
                    "Subnet range is invalid. Start IP address must be smaller than end IP address",
                    "subnet.range.ip.start.must.be.smaller");

        }
    }

    /**
     * Validate that the start and end IP addresses are inside the network specified by the CIDR.
     * If it's outside, an exception is raised.
     *
     * @param subnetRangeState The subnetRange state. This contains the start and end ip address
     *                         that was specified in the ip address range.
     * @param subnetstate      The subnet state contains the CIDR from which the valid network address
     *                         is identified.
     */
    private void validateIpInRange(SubnetRangeState subnetRangeState, SubnetState subnetstate) {

        if (!SubnetValidator
                .isIpInValidRange(
                        subnetRangeState.startIPAddress,
                        subnetstate.subnetCIDR,
                        subnetRangeState.ipVersion)) {
            throw new LocalizableValidationException(
                    String.format("Start IP address %s is invalid. It lies outside the "
                                    + "IP range specified by the CIDR: %s",
                            subnetRangeState.startIPAddress,
                            subnetstate.subnetCIDR),
                    "subnet.range.ip.outside.range.start",
                    subnetRangeState.startIPAddress,
                    subnetstate.subnetCIDR);
        }

        if (!SubnetValidator
                .isIpInValidRange(
                        subnetRangeState.endIPAddress,
                        subnetstate.subnetCIDR,
                        subnetRangeState.ipVersion)) {
            throw new LocalizableValidationException(
                    String.format("End IP address %s is invalid. It lies outside the "
                                    + "IP range specified by the CIDR: %s",
                            subnetRangeState.endIPAddress,
                            subnetstate.subnetCIDR),
                    "subnet.range.ip.outside.range.start",
                    subnetRangeState.endIPAddress,
                    subnetstate.subnetCIDR);
        }
    }

    /**
     * We don't want ip ranges to overlap. So we check the newly created ip range,
     * with the pre existing ip ranges and make sure there is no over lap.
     * If there is an overlap, raise exception.
     *
     * @param documentSelfLink  The self link of the subnetrange document being modified
     *                          This is empty if its a create.
     * @param startIp           The start ip provided for this subnet range
     * @param endIp             The end ip for this subnet range
     * @param subnetRangeStates This is a list of all the pre-existing subnet states. We check
     *                          for overlap against these.
     */
    private void validateIpsOutsideDefinedRanges(String documentSelfLink, String startIp,
            String endIp,
            List<SubnetRangeState>
                    subnetRangeStates) {
        String ipUnderTest;

        for (SubnetRangeState subnetRangeState : subnetRangeStates) {
            String selfLink = subnetRangeState.documentSelfLink;

            //For create self link is empty. Check against all pre existing subnet ranges
            //For updates or patches, don't check against self
            if (selfLink == null || !selfLink.equals(documentSelfLink)) {

                String ipBegin = subnetRangeState.startIPAddress;
                String ipEnd = subnetRangeState.endIPAddress;
                IPVersion ipVersion = subnetRangeState.ipVersion;

                ipUnderTest = startIp;
                throwExceptionIfIpOverlap(ipBegin, ipEnd, ipVersion, ipUnderTest);

                ipUnderTest = endIp;
                throwExceptionIfIpOverlap(ipBegin, ipEnd, ipVersion, ipUnderTest);
            }

        }
    }

    private void throwExceptionIfIpOverlap(String ipBegin, String ipEnd, IPVersion ipVersion,
            String ipUnderTest) {
        if (SubnetValidator.isIpInBetween(ipBegin, ipEnd, ipVersion,
                ipUnderTest
        )) {
            throw new LocalizableValidationException(
                    String.format("The submitted IP address range overlaps with a "
                                    + "previously defined IP address range: %s-%s ",
                            ipBegin, ipEnd),
                    "subnet.range.ip.overlap", ipBegin, ipEnd);
        }

    }

    /**
     * Fetch subnet state by document link.
     *
     * @param link Document link for the subnet service
     * @return A deferred result of an operation, that has the subnet state
     */
    private <T> DeferredResult<Operation> getSubnetState(String link) {
        AssertUtil.assertNotEmpty(link, "Cannot fetch subnet details with an empty subnet link");
        URI uri = UriUtils.buildUri(getHost(), link);
        return sendWithDeferredResult(Operation.createGet(uri));
    }

    /**
     * Fetch all pre existing subnet ranges
     *
     * @param subnetLink
     * @return A deferred result that contains a list of pre-existing subnet ranges
     */
    private DeferredResult<List<SubnetRangeState>> getSubnetRangesInSubnet(String subnetLink) {
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(SubnetRangeState.class)
                .addFieldClause(FIELD_NAME_SUBNET_LINK, subnetLink);

        QueryTop<SubnetRangeState> queryTop = new QueryUtils.QueryTop<>(
                this.getHost(),
                qBuilder.build(),
                SubnetRangeState.class,
                null
        );

        return queryTop.collectDocuments(Collectors.toList());
    }

    private SubnetRangeState getOperationBody(Operation operation) {
        checkHasBody(operation);
        SubnetRangeState subnetRangeState = operation.getBody(SubnetRangeState.class);
        return subnetRangeState;
    }

    private void checkHasBody(Operation operation) {
        if (!operation.hasBody()) {
            operation.fail(new IllegalArgumentException("body is required"));
        }
    }
}