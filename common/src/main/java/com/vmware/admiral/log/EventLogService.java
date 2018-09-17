/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.log;

import static com.vmware.admiral.common.SwaggerDocumentation.BASE_PATH;
import static com.vmware.admiral.common.SwaggerDocumentation.DataTypes.DATA_TYPE_OBJECT;
import static com.vmware.admiral.common.SwaggerDocumentation.ParamTypes.PARAM_TYPE_BODY;
import static com.vmware.admiral.common.SwaggerDocumentation.Tags.EVENT_LOGS;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ReflectionUtils;
import com.vmware.admiral.common.util.ReflectionUtils.CustomPath;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * Describes a result of some asynchronous operations in the form of event log that usually cannot
 * be propagated to the UI by normal means but still need user attention.
 */
@Api(tags = {EVENT_LOGS})
@Path("")
public class EventLogService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.EVENT_LOG;

    protected static final long EXPIRATION_INTERVAL_HOURS = Long.getLong(
            "com.vmware.admiral.log.eventlogservice.expiration.interval.hours",
            TimeUnit.HOURS.toMicros(72));

    static {
        ReflectionUtils.setAnnotation(EventLogService.class, Path.class,
                new CustomPath(FACTORY_LINK));
    }

    @ApiModel
    public static class EventLogState extends MultiTenantDocument {

        public static final String FIELD_NAME_EVENT_LOG_TYPE = "eventLogType";

        public enum EventLogType {
            /**
             * Events that pass non-critical information to the administrator.
             */
            INFO,
            /**
             * Events that provide implication of potential problems; a warning indicates that the
             * system is not in an ideal state and that some further actions could result in an
             * error.
             */
            WARNING,
            /**
             * Events that indicate problems that are not system-critical and do not require
             * immediate attention.
             */
            ERROR
        }

        //TODO the xenon annotations should be removed in the future
        /**
         * The operation this event originates from. Example: Host config, Registry config, Create
         * container, Maintenance task.
         */
        @Documentation(description = "The operation this event originates from. Example: Host config, Registry config, Create container, Maintenance task.")
        @ApiModelProperty(
                value = "The operation this event originates from.",
                example = "Host config, Registry config, Create container, Maintenance task.",
                required = true)
        public String resourceType;

        /** Severity level type. */
        @Documentation(description = "Severity level type.")
        @ApiModelProperty(
                value = "Severity level type.",
                example = "INFO, WARNING, ERROR",
                required = true)
        public EventLogType eventLogType;

        /** User-friendly description of the event */
        @Documentation(description = "User-friendly description of the event")
        @ApiModelProperty(
                value = "User-friendly description of the event.",
                example = "Host config failed.",
                required = true)
        public String description;

        /** Additional data like operation request/response body, Request IP, etc. */
        @Documentation(description = "Additional data like operation request/response body, Request IP, etc.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @ApiModelProperty(
                value = "Additional data.",
                example = "The body of operation request/response, Request IP")
        public Map<String, String> customProperties;
    }

    public EventLogService() {
        super(EventLogState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.DOCUMENT_OWNER, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    @POST
    @Path(BASE_PATH)
    @ApiOperation(
            value = "Create a new event log.",
            notes = "Creates the new event log and sets it's expiration time.",
            nickname = "createEventLog")
    @ApiResponses({
            @ApiResponse(code = Operation.STATUS_CODE_OK, message = "Event log successfully created.")})
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "Event log state",
                    value = "The new event log to be added.",
                    dataType = DATA_TYPE_OBJECT,
                    dataTypeClass = EventLogState.class,
                    paramType = PARAM_TYPE_BODY,
                    required = true
            )})
    public void handleCreate(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("empty body"));
            return;
        }
        try {
            EventLogState state = post.getBody(EventLogState.class);
            validateStateOnStart(state);
            state.documentExpirationTimeMicros = ServiceUtils
                    .getExpirationTimeFromNowInMicros(
                            EXPIRATION_INTERVAL_HOURS);
            post.setBody(state).complete();
        } catch (Throwable e) {
            logSevere(e);
            post.fail(e);
        }
    }

    @Override
    public void handlePut(Operation put) {
        Operation.failActionNotSupported(put);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private void validateStateOnStart(EventLogState state) {
        assertNotNull(state.description, "description");
        assertNotNull(state.resourceType, "resourceType");
        assertNotNull(state.eventLogType, "eventLogType");
    }
}
