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

package com.vmware.admiral.adapter.pks.service;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_QUERY_PARAM_NAME;
import static com.vmware.admiral.common.SwaggerDocumentation.BASE_PATH;
import static com.vmware.admiral.common.SwaggerDocumentation.DataTypes.DATA_TYPE_STRING;
import static com.vmware.admiral.common.SwaggerDocumentation.ParamTypes.PARAM_TYPE_QUERY;
import static com.vmware.admiral.common.SwaggerDocumentation.Tags.PKS_PLAN_LIST_TAG;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.PKSPlan;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.ReflectionUtils;
import com.vmware.admiral.common.util.ReflectionUtils.CustomPath;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Help service to retrieve existing Kubernetes clusters for a given PKS endpoint.
 * A flag indicates whether each cluster has been added to Admiral.
 */
@Api(tags = {PKS_PLAN_LIST_TAG})
@Path("")
public class PKSPlanListService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.PKS_PLANS;

    static {
        ReflectionUtils.setAnnotation(PKSPlanListService.class, Path.class,
                new CustomPath(SELF_LINK));
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.GET) {
            Operation.failActionNotSupported(op);
            return;
        }

        super.handleRequest(op);
    }

    @Override
    @GET
    @Path(BASE_PATH)
    @ApiOperation(
            value = "List all endpoint plans.",
            notes = "Retrieves all plans for the specified endpoint.",
            nickname = "getSingleOrAll",
            response = PKSPlan.class,
            responseContainer = "List")
    @ApiResponses({
            @ApiResponse(code = Operation.STATUS_CODE_OK, message = "Successfully retrieved all endpoint plans.")})
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = PKS_ENDPOINT_QUERY_PARAM_NAME,
                    value = "The self link to the endpoint state.",
                    dataType = DATA_TYPE_STRING,
                    paramType = PARAM_TYPE_QUERY,
                    required = true)})
    public void handleGet(Operation op) {
        try {
            Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());

            String endpointLink = queryParams.get(PKS_ENDPOINT_QUERY_PARAM_NAME);
            AssertUtil.assertNotNullOrEmpty(endpointLink, PKS_ENDPOINT_QUERY_PARAM_NAME);

            handleListRequest(op, endpointLink);
        } catch (Exception x) {
            logSevere(x);
            op.fail(x);
        }
    }

    private void handleListRequest(Operation op, String endpointLink) {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = PKSOperationType.LIST_PLANS.toString();
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = UriUtils.buildUri(getHost(), endpointLink);

        sendRequest(Operation.createPatch(this, ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Adapter request for listing PKS plans failed. Error: %s",
                                Utils.toString(ex));
                        op.fail(ex);
                    } else {
                        PKSPlan[] pksPlans = o.getBody(PKSPlan[].class);
                        op.setBodyNoCloning(pksPlans);
                        op.complete();
                    }
                }));
    }

}
