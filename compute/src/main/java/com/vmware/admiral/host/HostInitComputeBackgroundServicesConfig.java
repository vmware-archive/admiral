/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.vmware.admiral.compute.EpzComputeEnumerationPeriodicService;
import com.vmware.admiral.compute.PlacementCapacityUpdatePeriodicService;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class for starting compute background services - i.e. services that are triggered either
 * periodically or based on a subscription for certain events.
 *
 * In certain scenarios these services may not be wanted to run, for example in tests where
 * background activities can introduce non-deterministic behavior.
 */
public class HostInitComputeBackgroundServicesConfig extends HostInitServiceHelper {

    public static final Collection<ServiceMetadata> SERVICES_METADATA =
            Collections.unmodifiableList(Arrays.asList(
                service(EpzComputeEnumerationPeriodicService.class),
                    service(PlacementCapacityUpdatePeriodicService.class)));

    public static void startServices(ServiceHost host) {
        startServices(host,
                EpzComputeEnumerationPeriodicService.class,
                PlacementCapacityUpdatePeriodicService.class);
    }
}
