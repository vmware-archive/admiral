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

package com.vmware.admiral.log;

import static com.vmware.admiral.common.SwaggerDocumentation.DataTypes.DATA_TYPE_STRING;
import static com.vmware.admiral.common.SwaggerDocumentation.ParamTypes.PARAM_TYPE_QUERY;
import static com.vmware.admiral.common.SwaggerDocumentation.Tags.EVENT_LOGS;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.ReflectionUtils;
import com.vmware.admiral.common.util.ReflectionUtils.CustomPath;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.service.common.AbstractSecuredFactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;


@Api(tags = {EVENT_LOGS})
@Path("")
public class EventLogFactoryService extends AbstractSecuredFactoryService {
    public static final String SELF_LINK = ManagementUriParts.EVENT_LOG;

    static {
        ReflectionUtils.setAnnotation(EventLogFactoryService.class, Path.class,
                new CustomPath(SELF_LINK));
    }

    public EventLogFactoryService() {
        super(EventLogState.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new EventLogService();
    }

    @Override
    @GET
    @ApiOperation(
            value = "Get all event logs.",
            notes = "Retrieves all event logs for the project specified in the operation header.",
            nickname = "getAll")
    @ApiResponses({
            @ApiResponse(code = Operation.STATUS_CODE_OK, message = "Successfully retrieved all event logs.")})
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "expand",
                    value = "Expand option to view details of the instances",
                    dataType = DATA_TYPE_STRING,
                    paramType = PARAM_TYPE_QUERY)})
    public void handleGet(Operation get) {
        OperationUtil.transformProjectHeaderToFilterQuery(get);
        super.handleGet(get);
    }
}
