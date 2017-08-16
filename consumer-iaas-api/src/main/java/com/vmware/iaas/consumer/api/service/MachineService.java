/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.iaas.consumer.api.service;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.vmware.iaas.consumer.api.common.Constants.UriPathElements;
import com.vmware.iaas.consumer.api.common.ServiceOperationUtil;
import com.vmware.iaas.consumer.api.model.Machine;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter;

/**
 * Service responsible for machines provisioning and management
 */
public class MachineService extends BaseControllerService {

    public static String SELF_LINK = UriPathElements.MACHINES_PREFIX;

    public MachineService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @ApiOperation(nickname = "createMachine",
            value = "Create Machine",
            notes = "Create a new Machine.",
            response = Machine.class,
            code = 201,
            tags = { "compute-api, dev-persona" })
    @ApiResponses(
            value = {
                    @ApiResponse(code = 400, message = "Invalid request - bad data."),
                    @ApiResponse(code = 403, message = "Forbidden.")
            })
    @Override
    public void handlePost(Operation post) {
        throw new NotImplementedException();
    }

    @ApiOperation(nickname = "getMachines",
            value = "Get Machines",
            notes = "Get a page of Machines.",
            response = Machine.class,
            code = 200,
            tags = { "compute-api, dev-persona" })
    @ApiResponses(
            value = {
                    @ApiResponse(code = 403, message = "Forbidden.")
            })
    @Override
    public void handleGet(Operation get) {
        ServiceOperationUtil.handleGet(get, getHost(), ComputeService.FACTORY_LINK,
                ComputeState.class, Machine.class);
    }

    @ApiOperation(nickname = "updateMachine",
            value = "Update Machine",
            notes = "Update an existing Machine.",
            response = Machine.class,
            code = 200,
            tags = { "compute-api, dev-persona" })
    @ApiResponses(
            value = {
                    @ApiResponse(code = 400, message = "Invalid request - bad data."),
                    @ApiResponse(code = 403, message = "Forbidden.")
            })
    @Override
    public void handlePatch(Operation patch) {
        throw new NotImplementedException();
    }

    @Override
    protected RequestRouter createControllerRouting() {
        RequestRouter requestRouter = new RequestRouter();

        requestRouter.register(
                Action.GET,
                new RequestRouter.RequestDefaultMatcher(),
                this::handleGet, "Get for all machines");

        return requestRouter;
    }
}
