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

package com.vmware.photon.controller.model.tasks;

import static com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.FIELD_NAME_IP_ADDRESS_STATUS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.IPAddressService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * This class is responsible for marking de-allocated IP addresses as available, after a certain
 * time period. When an IP address used by a virtual machine is released, it should not be used by
 * other allocations immediately. This is because the IP address might be cached at DNS and some
 * other L4 caches. So, we wait until a specified time period, before marking the IP address as
 * available.
 */
public class IPAddressReleaseTaskService extends StatelessService {

    public static final String SELF_LINK = UriPaths.SCHEDULES + "/ip-address-release";
    public static final String IP_ADDRESS_RELEASE_PERIOD_SECONDS_PROPERTY = UriPaths.PROPERTY_PREFIX
            + "ipam.ipaddress.release.period.seconds";
    public static final long DEFAULT_IP_ADDRESS_RELEASE_PERIOD_SECONDS = TimeUnit.MINUTES
            .toSeconds(30);

    public static final long IP_ADDRESS_RELEASE_PERIOD_SECONDS = Long.getLong(
            IP_ADDRESS_RELEASE_PERIOD_SECONDS_PROPERTY,
            DEFAULT_IP_ADDRESS_RELEASE_PERIOD_SECONDS);

    public static final long IP_ADDRESS_MAINTENANCE_PERIOD_MICROS = TimeUnit.MINUTES.toMicros(5);

    public IPAddressReleaseTaskService() {
        super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(IP_ADDRESS_MAINTENANCE_PERIOD_MICROS);
    }

    @Override
    public void handlePeriodicMaintenance(Operation maintenance) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logInfo("Skipping maintenance since service is not available: %s ", getUri());
        }

        maintenance.complete();

        logFine("Performing maintenance for: %s", getUri());

        retrieveReleasedIPAddresses()
                .thenAccept(ips -> markIPAddressesAsAvailable(ips));
    }

    /**
     * Retrieves ip addresses that are in RELEASED state for more than
     * IP_ADDRESS_RELEASE_INTERVAL_MS milliseconds.
     *
     * @return List of IP addresses that are in RELEASED state for more than
     *         IP_ADDRESS_RELEASE_PERIOD_MS millii seconds.
     */
    private DeferredResult<List<IPAddressService.IPAddressState>> retrieveReleasedIPAddresses() {
        long currentTime = Utils.getNowMicrosUtc();
        long ipAddressReleaseCutoffTime = currentTime
                - TimeUnit.SECONDS.toMicros(IP_ADDRESS_RELEASE_PERIOD_SECONDS);

        QueryTask.Query getReleasedIPAddressesQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(IPAddressService.IPAddressState.class)
                .addFieldClause(FIELD_NAME_IP_ADDRESS_STATUS,
                        IPAddressService.IPAddressState.IPAddressStatus.RELEASED.toString())
                .addRangeClause(ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange.createLessThanRange(ipAddressReleaseCutoffTime))
                .build();

        QueryUtils.QueryByPages<IPAddressService.IPAddressState> queryByPages = new QueryUtils.QueryByPages<>(
                this.getHost(),
                getReleasedIPAddressesQuery,
                IPAddressService.IPAddressState.class, null);

        return queryByPages.collectDocuments(Collectors.toList())
                .exceptionally(e -> {
                    logWarning(String.format(
                            "Failed to retrieve released IP addresses due to error %s",
                            e.getMessage()));
                    return new ArrayList<>();
                });
    }

    /**
     * Releases IP Addresses and marks them as available.
     *
     * @param ipAddressStates
     *            IP Address states
     */
    private DeferredResult<List<Operation>> markIPAddressesAsAvailable(
            List<IPAddressService.IPAddressState> ipAddressStates) {
        IPAddressService.IPAddressState addressState = new IPAddressService.IPAddressState();
        addressState.ipAddressStatus = IPAddressService.IPAddressState.IPAddressStatus.AVAILABLE;

        List<DeferredResult<Operation>> ipAddressOperations = new ArrayList<>();
        for (IPAddressService.IPAddressState ipAddressState : ipAddressStates) {
            Operation patchOp = Operation.createPatch(this, ipAddressState.documentSelfLink)
                    .setBody(addressState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logWarning(
                                    "Failed to mark IP address resource %s as available due to failure %s",
                                    ipAddressState.ipAddress, e.getMessage());
                        } else {
                            logInfo("The IP address %s is made available",
                                    ipAddressState.ipAddress);
                        }
                    });

            ipAddressOperations.add(this.sendWithDeferredResult(patchOp));
        }

        return DeferredResult.allOf(ipAddressOperations);
    }
}
